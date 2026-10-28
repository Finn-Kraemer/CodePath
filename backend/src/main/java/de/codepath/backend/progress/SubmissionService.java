package de.codepath.backend.progress;

import de.codepath.backend.classroom.AccessPolicy;
import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.engine.*;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Ablauf einer Abgabe: Zugriff prüfen → Handler bewertet → in einer Transaktion Versuch protokollieren,
 * Fortschritt aktualisieren und Punkte genau einmal vergeben → Live-Event nach dem Commit.
 */
@Service
@RequiredArgsConstructor
public class SubmissionService {

    static final int MAX_ANSWER_CHARS = 250_000;
    // Bearbeitungszeit wird gedeckelt, falls jemand den Tab über Nacht offen lässt
    static final long MAX_TIME_SECONDS = Duration.ofHours(4).toSeconds();

    private final ContentService contentService;
    private final AccessPolicy accessPolicy;
    private final TaskHandlerRegistry registry;
    private final ProgressRepository progressRepository;
    private final AttemptRepository attemptRepository;
    private final PointsService pointsService;
    private final UserService userService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    @Transactional
    public SubmitResult submit(User user, String moduleSlug, String taskSlug, Map<String, Object> answer) {
        PublishedTask task = contentService.publishedTask(moduleSlug, taskSlug);
        accessPolicy.requireTaskOpen(user, task);
        if (answer == null) throw ApiException.badRequest("Keine Antwort übermittelt.");
        if (answer.toString().length() > MAX_ANSWER_CHARS) throw ApiException.badRequest("Die Antwort ist zu groß.");

        // Serialisiert gleichzeitige Abgaben derselben Person (Doppelklick, zwei Tabs)
        userService.lockForUpdate(user.getId());
        Progress progress = progressFor(user.getId(), task.taskId());
        AttemptRules rules = task.rules();

        if (progress.isLocked()) throw ApiException.conflict("Diese Aufgabe wurde von der Lehrkraft gesperrt.");
        if (progress.getStatus() == ProgressStatus.PENDING_REVIEW) {
            throw ApiException.conflict("Deine Abgabe wird gerade von der Lehrkraft geprüft.");
        }

        TaskHandler handler = registry.get(task.type());
        TaskSpec spec = task.spec();
        TaskContext context = TaskContext.of(user.getId(), task.taskId());

        if (progress.getStatus() == ProgressStatus.SOLVED) {
            // Nochmal prüfen ist erlaubt, bringt aber keine Punkte
            Evaluation again = handler.evaluate(spec, answer, context);
            return result(again, 0, progress, rules, again.isCorrect() ? "Richtig – diese Aufgabe hast du bereits gelöst." : again.feedback());
        }
        if (progress.getStatus() == ProgressStatus.FAILED || attemptsExhausted(progress, rules)) {
            throw ApiException.conflict("Für diese Aufgabe sind keine Versuche mehr übrig.");
        }

        Evaluation evaluation = handler.evaluate(spec, answer, context);
        if (evaluation.outcome() == Evaluation.Outcome.INVALID) {
            return result(evaluation, 0, progress, rules, evaluation.feedback());
        }

        Attempt attempt = new Attempt();
        attempt.setUserId(user.getId());
        attempt.setTaskId(task.taskId());
        attempt.setTaskVersionId(task.version().getId());
        attempt.setAnswer(answer);
        attempt.setOutcome(evaluation.outcome());
        attempt.setScore(BigDecimal.valueOf(evaluation.score()).setScale(4, RoundingMode.HALF_UP));
        attempt.setFeedback(evaluation.feedback());
        attemptRepository.save(attempt);

        int awarded = 0;
        String feedback = evaluation.feedback();
        switch (evaluation.outcome()) {
            case MANUAL -> {
                progress.setStatus(ProgressStatus.PENDING_REVIEW);
                progress.setReviewComment(null);
                events.publishEvent(new PracticeSubmittedEvent(attempt.getId(), user.getId(), task.taskId()));
            }
            case UNGRADED -> progress.setStatus(ProgressStatus.PRACTICED);
            case CORRECT -> {
                awarded = ScoreCalculator.points(spec.points(), 1.0, progress.getHintsUsed(), rules,
                        progress.getFailedAttempts() == 0, progress.isSolutionRevealed());
                complete(progress, ProgressStatus.SOLVED);
                progress.setBestScore(BigDecimal.ONE);
                if (awarded > 0 && !pointsService.awardOnce(user.getId(), task.taskId(), awarded, PointsReason.SOLVED, null, null)) {
                    awarded = 0;
                }
                feedback = feedbackForSolved(evaluation.feedback(), awarded, progress);
            }
            case WRONG, PARTIAL -> {
                progress.setFailedAttempts(progress.getFailedAttempts() + 1);
                if (BigDecimal.valueOf(evaluation.score()).compareTo(progress.getBestScore()) > 0) {
                    progress.setBestScore(BigDecimal.valueOf(evaluation.score()).setScale(4, RoundingMode.HALF_UP));
                }
                if (attemptsExhausted(progress, rules)) {
                    complete(progress, ProgressStatus.FAILED);
                    awarded = partialPoints(user, task, progress, rules, spec);
                    feedback += awarded > 0
                            ? " Keine Versuche mehr – für deinen besten Versuch gibt es " + awarded + " Teilpunkte."
                            : " Keine Versuche mehr.";
                }
            }
            default -> {
            }
        }
        progressRepository.save(progress);
        return result(evaluation, awarded, progress, rules, feedback);
    }

    /** Hinweise werden gestuft freigeschaltet; jeder kostet Punkte, solange die Aufgabe offen ist. */
    @Transactional
    public HintResult requestHint(User user, String moduleSlug, String taskSlug) {
        PublishedTask task = contentService.publishedTask(moduleSlug, taskSlug);
        accessPolicy.requireTaskVisible(user, task);
        userService.lockForUpdate(user.getId());
        Progress progress = progressFor(user.getId(), task.taskId());
        List<String> hints = task.version().getHints();
        boolean charged = !progress.isFinished() && progress.getStatus() != ProgressStatus.PENDING_REVIEW;
        if (charged && progress.getHintsUsed() < hints.size()) {
            progress.setHintsUsed(progress.getHintsUsed() + 1);
            progressRepository.save(progress);
            auditService.log("HINT_USED", "user:" + user.getId(), Map.of("task", task.taskId(), "hint", progress.getHintsUsed()));
        }
        int visible = progress.isFinished() ? hints.size() : Math.min(progress.getHintsUsed(), hints.size());
        return new HintResult(hints.subList(0, visible), hints.size(), task.rules().hintPenaltyPercent());
    }

    public record HintResult(List<String> hints, int hintsTotal, int penaltyPercent) {
    }

    /** Lösung aufdecken: danach gibt es für diese Aufgabe keine Punkte mehr. */
    @Transactional
    public Map<String, Object> revealSolution(User user, String moduleSlug, String taskSlug) {
        PublishedTask task = contentService.publishedTask(moduleSlug, taskSlug);
        accessPolicy.requireTaskVisible(user, task);
        if (!task.rules().revealAllowed()) throw ApiException.forbidden("Für diese Aufgabe gibt es keine Musterlösung zum Aufdecken.");
        Progress progress = progressFor(user.getId(), task.taskId());
        if (!progress.isSolutionRevealed()) {
            progress.setSolutionRevealed(true);
            progressRepository.save(progress);
            auditService.log("SOLUTION_REVEALED", "user:" + user.getId(), Map.of("task", task.taskId()));
        }
        TaskHandler handler = registry.get(task.type());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("solution", handler.solutionView(task.spec(), TaskContext.of(user.getId(), task.taskId())));
        view.put("explanation", task.version().getExplanation());
        return view;
    }

    /** Legt den Fortschritt beim ersten Öffnen an und merkt sich den Beginn (Zeitmessung durch den Server). */
    @Transactional
    public Progress open(User user, PublishedTask task) {
        Progress progress = progressFor(user.getId(), task.taskId());
        if (progress.getStartedAt() == null) {
            progress.setStartedAt(LocalDateTime.now());
            progressRepository.save(progress);
        }
        return progress;
    }

    Progress progressFor(Long userId, Long taskId) {
        return progressRepository.findById(new Progress.Key(userId, taskId)).orElseGet(() -> {
            Progress p = new Progress(userId, taskId);
            p.setStartedAt(LocalDateTime.now());
            return progressRepository.save(p);
        });
    }

    private int partialPoints(User user, PublishedTask task, Progress progress, AttemptRules rules, TaskSpec spec) {
        double best = progress.getBestScore().doubleValue();
        int points = ScoreCalculator.points(spec.points(), best, progress.getHintsUsed(), rules, false, progress.isSolutionRevealed());
        if (points > 0 && pointsService.awardOnce(user.getId(), task.taskId(), points, PointsReason.PARTIAL, null, null)) {
            return points;
        }
        return 0;
    }

    private static void complete(Progress progress, ProgressStatus status) {
        progress.setStatus(status);
        progress.setCompletedAt(LocalDateTime.now());
        if (progress.getStartedAt() != null) {
            long seconds = Duration.between(progress.getStartedAt(), progress.getCompletedAt()).toSeconds();
            progress.setTimeSpentSeconds(Math.clamp(seconds, 0, MAX_TIME_SECONDS));
        }
    }

    static boolean attemptsExhausted(Progress progress, AttemptRules rules) {
        return rules.maxAttempts() != null && progress.getFailedAttempts() >= rules.maxAttempts();
    }

    private static String feedbackForSolved(String base, int awarded, Progress progress) {
        if (progress.isSolutionRevealed()) return base + " Da du die Lösung aufgedeckt hast, gibt es keine Punkte.";
        return base + " +" + awarded + " Punkte.";
    }

    private static SubmitResult result(Evaluation evaluation, int awarded, Progress progress, AttemptRules rules, String feedback) {
        Integer left = rules.maxAttempts() == null ? null : Math.max(0, rules.maxAttempts() - progress.getFailedAttempts());
        return new SubmitResult(evaluation.outcome(), evaluation.isCorrect(), evaluation.score(), feedback,
                evaluation.details(), awarded, progress.getStatus(), progress.getFailedAttempts(), left);
    }
}

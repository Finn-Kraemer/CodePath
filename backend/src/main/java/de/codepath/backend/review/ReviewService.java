package de.codepath.backend.review;

import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.engine.Cfg;
import de.codepath.backend.engine.handlers.PracticeHandler;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.progress.PracticeSubmittedEvent;
import de.codepath.backend.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** Praxisabgaben: Warteschlange, Bewertung mit Rubrik, Rückgabe zur Überarbeitung. */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final PracticeReviewRepository repository;
    private final ProgressService progressService;
    private final ContentService contentService;
    private final UserService userService;
    private final AuditService auditService;

    public record Criterion(String id, String label, int points) {
    }

    public record SubmissionSummary(Long id, String username, String displayName, String moduleTitle, String taskTitle,
                                    LocalDateTime submittedAt, String status, boolean supportUsed, Integer awardedPoints) {
    }

    public record SubmissionDetail(Long id, String username, String displayName, String moduleTitle, String taskTitle,
                                   String instructions, LocalDateTime submittedAt, String content, String status,
                                   List<Criterion> rubric, Map<String, Integer> scores, Integer awardedPoints,
                                   String comment, int hintsUsed, int maxPoints) {
    }

    /** Läuft in der Transaktion der Abgabe: die Bewertung entsteht zusammen mit dem Versuch. */
    @EventListener
    public void onPracticeSubmitted(PracticeSubmittedEvent event) {
        repository.save(new PracticeReview(event.attemptId()));
    }

    public List<SubmissionSummary> pending() {
        return repository.findByStatusOrderByIdAsc(ReviewStatus.PENDING).stream().map(this::summary).flatMap(Optional::stream).toList();
    }

    public List<SubmissionSummary> forStudent(String username) {
        User user = userService.byUsername(username);
        List<Long> attemptIds = new ArrayList<>();
        for (PublishedTask t : contentService.allPublishedTasks()) {
            if (t.task().getType() != de.codepath.backend.engine.TaskType.PRACTICE) continue;
            progressService.attempts(user.getId(), t.taskId()).forEach(a -> attemptIds.add(a.id()));
        }
        if (attemptIds.isEmpty()) return List.of();
        return repository.findByAttemptIdInOrderByIdDesc(attemptIds).stream().map(this::summary).flatMap(Optional::stream).toList();
    }

    public SubmissionDetail detail(Long reviewId) {
        PracticeReview review = get(reviewId);
        ProgressService.AttemptView attempt = attempt(review);
        PublishedTask task = contentService.publishedTask(attempt.taskId());
        User user = userService.byId(attempt.userId()).orElseThrow(() -> ApiException.notFound("Konto gelöscht."));
        return new SubmissionDetail(review.getId(), user.getUsername(), user.getVisibleName(), task.module().getTitle(),
                task.version().getTitle(), Cfg.string(task.version().getConfig(), "instructions", ""), attempt.createdAt(),
                Cfg.string(attempt.answer(), "content", ""), review.getStatus().name(), rubric(task), review.getScores(),
                review.getAwardedPoints(), review.getComment(), progressService.hintsUsed(user.getId(), task.taskId()),
                task.version().getPoints());
    }

    /**
     * Nimmt die Abgabe an. Ohne Rubrik-Punkte zählt die volle Punktzahl, mit halfPoints die Hälfte.
     */
    @Transactional
    public SubmissionDetail approve(Long reviewId, Map<String, Integer> scores, Boolean halfPoints, String comment, User teacher) {
        PracticeReview review = get(reviewId);
        ProgressService.AttemptView attempt = attempt(review);
        PublishedTask task = contentService.publishedTask(attempt.taskId());
        List<Criterion> rubric = rubric(task);

        Map<String, Integer> given = new LinkedHashMap<>();
        if (scores == null || scores.isEmpty()) {
            for (Criterion c : rubric) given.put(c.id(), Boolean.TRUE.equals(halfPoints) ? c.points() / 2 : c.points());
        } else {
            for (Criterion c : rubric) {
                int value = scores.getOrDefault(c.id(), 0);
                if (value < 0 || value > c.points()) {
                    throw ApiException.badRequest("Für „" + c.label() + "“ sind 0 bis " + c.points() + " Punkte möglich.");
                }
                given.put(c.id(), value);
            }
        }
        int rubricPoints = given.values().stream().mapToInt(Integer::intValue).sum();
        int awarded = progressService.completeReview(attempt.userId(), attempt.taskId(), rubricPoints, comment, teacher.getId());

        review.setStatus(ReviewStatus.APPROVED);
        review.setScores(given);
        review.setAwardedPoints(awarded);
        review.setComment(comment);
        review.setReviewerId(teacher.getId());
        review.setReviewedAt(LocalDateTime.now());
        auditService.log("PRACTICE_APPROVED", "user:" + attempt.userId(), Map.of("task", attempt.taskId(), "points", awarded));
        return detail(reviewId);
    }

    @Transactional
    public SubmissionDetail reject(Long reviewId, String comment, boolean lockTask, User teacher) {
        PracticeReview review = get(reviewId);
        ProgressService.AttemptView attempt = attempt(review);
        if (review.getStatus() == ReviewStatus.APPROVED) {
            throw ApiException.conflict("Die Abgabe ist bereits angenommen. Punkte bitte in der Schülerakte korrigieren.");
        }
        progressService.rejectReview(attempt.userId(), attempt.taskId(), comment, lockTask);
        review.setStatus(ReviewStatus.REJECTED);
        review.setComment(comment);
        review.setLockTask(lockTask);
        review.setReviewerId(teacher.getId());
        review.setReviewedAt(LocalDateTime.now());
        return detail(reviewId);
    }

    static List<Criterion> rubric(PublishedTask task) {
        return PracticeHandler.rubric(task.spec()).stream()
                .map(c -> new Criterion(Cfg.string(c, "id"), Cfg.string(c, "label", ""), Optional.ofNullable(Cfg.integer(c, "points")).orElse(0)))
                .toList();
    }

    private Optional<SubmissionSummary> summary(PracticeReview review) {
        Optional<ProgressService.AttemptView> attempt = progressService.attempt(review.getAttemptId());
        if (attempt.isEmpty()) return Optional.empty();
        Optional<User> user = userService.byId(attempt.get().userId());
        if (user.isEmpty()) return Optional.empty();
        PublishedTask task = contentService.publishedTask(attempt.get().taskId());
        return Optional.of(new SubmissionSummary(review.getId(), user.get().getUsername(), user.get().getVisibleName(),
                task.module().getTitle(), task.version().getTitle(), attempt.get().createdAt(), review.getStatus().name(),
                progressService.hintsUsed(user.get().getId(), task.taskId()) > 0, review.getAwardedPoints()));
    }

    private PracticeReview get(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Abgabe nicht gefunden."));
    }

    private ProgressService.AttemptView attempt(PracticeReview review) {
        return progressService.attempt(review.getAttemptId()).orElseThrow(() -> ApiException.notFound("Abgabe nicht gefunden."));
    }
}

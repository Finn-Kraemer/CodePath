package de.codepath.backend.progress;

import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** Öffentliche Schnittstelle des progress-Pakets für Bewertung (review) und Zeugnis (report). */
@Service
@RequiredArgsConstructor
public class ProgressService {

    private final ProgressRepository progressRepository;
    private final AttemptRepository attemptRepository;
    private final PointsService pointsService;
    private final ContentService contentService;
    private final UserService userService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public record AttemptView(Long id, Long userId, Long taskId, Long taskVersionId, Map<String, Object> answer,
                              String outcome, double score, String feedback, LocalDateTime createdAt) {
        static AttemptView of(Attempt a) {
            return new AttemptView(a.getId(), a.getUserId(), a.getTaskId(), a.getTaskVersionId(), a.getAnswer(),
                    a.getOutcome().name(), a.getScore().doubleValue(), a.getFeedback(), a.getCreatedAt());
        }
    }

    public Optional<AttemptView> attempt(Long attemptId) {
        return attemptRepository.findById(attemptId).map(AttemptView::of);
    }

    public List<AttemptView> attempts(Long userId, Long taskId) {
        return attemptRepository.findByUserIdAndTaskIdOrderByCreatedAtDesc(userId, taskId).stream().map(AttemptView::of).toList();
    }

    public int hintsUsed(Long userId, Long taskId) {
        return progressRepository.findById(new Progress.Key(userId, taskId)).map(Progress::getHintsUsed).orElse(0);
    }

    /**
     * Die Lehrkraft hat eine Praxisabgabe angenommen. Rubrik-Punkte minus Hinweis-Abzug werden einmal
     * gebucht; eine erneute Bewertung gleicht die Differenz über eine Korrektur aus.
     */
    @Transactional
    public int completeReview(Long userId, Long taskId, int rubricPoints, String comment, Long reviewerId) {
        PublishedTask task = contentService.publishedTask(taskId);
        userService.lockForUpdate(userId);
        Progress progress = progressRepository.findById(new Progress.Key(userId, taskId)).orElseGet(() -> new Progress(userId, taskId));
        int points = ScoreCalculator.reviewPoints(rubricPoints, progress.getHintsUsed(), task.rules());
        if (!pointsService.awardOnce(userId, taskId, points, PointsReason.REVIEW, comment, reviewerId)) {
            long current = pointsService.forTask(userId, taskId);
            pointsService.correct(userId, taskId, (int) (points - current), "Neubewertung der Praxisabgabe", reviewerId);
        }
        progress.setStatus(ProgressStatus.SOLVED);
        progress.setCompletedAt(LocalDateTime.now());
        progress.setReviewComment(comment);
        progress.setBestScore(java.math.BigDecimal.ONE);
        progressRepository.save(progress);
        events.publishEvent(new ProgressCompletedEvent(
                userId,
                taskId,
                ProgressStatus.SOLVED,
                1.0,
                progress.getFailedAttempts(),
                progress.getHintsUsed(),
                progress.isSolutionRevealed()
        ));
        return points;
    }

    /** Zurück zur Überarbeitung; die Abgabe bleibt im Editor erhalten. Optional endgültig sperren. */
    @Transactional
    public void rejectReview(Long userId, Long taskId, String comment, boolean lock) {
        Progress progress = progressRepository.findById(new Progress.Key(userId, taskId)).orElseGet(() -> new Progress(userId, taskId));
        progress.setStatus(ProgressStatus.OPEN);
        progress.setReviewComment(comment);
        progress.setLocked(progress.isLocked() || lock);
        progressRepository.save(progress);
        auditService.log(lock ? "PRACTICE_REJECTED_LOCKED" : "PRACTICE_REJECTED", "user:" + userId, Map.of("task", taskId));
    }

    /** Stand je Aufgabe, an der die Person schon gearbeitet hat. */
    public Map<Long, ProgressStatus> statusByTask(Long userId) {
        Map<Long, ProgressStatus> result = new HashMap<>();
        progressRepository.findByUserId(userId).forEach(p -> result.put(p.getTaskId(), p.getStatus()));
        return result;
    }

    /** Letzte Abgabe zu einer Aufgabe, z. B. für den eigenen Code im Mitnehm-Paket. */
    public Optional<AttemptView> latestAttempt(Long userId, Long taskId) {
        return attemptRepository.findFirstByUserIdAndTaskIdOrderByCreatedAtDesc(userId, taskId).map(AttemptView::of);
    }

        /** Erreichte Punkte je Aufgabe (Summe aller Buchungen). */
    public Map<Long, Long> pointsByTask(Long userId) {
        return pointsService.byTask(userId);
    }

    public long totalPoints(Long userId) {
        return pointsService.total(userId);
    }

    public Map<Long, Long> totals(Collection<Long> userIds) {
        return pointsService.totals(userIds);
    }

    /**
     * Setzt den Fortschritt der angegebenen Aufgaben für einen Schüler zurück und bucht bereits
     * erhaltene Punkte per Korrekturbuchung ({@link PointsService#correct}) gegen, damit das
     * Punkte-Journal lückenlos nachvollziehbar bleibt.
     */
    @Transactional
    public void resetTasks(Long userId, Collection<Long> taskIds, String note, Long actorId) {
        if (taskIds == null || taskIds.isEmpty()) return;
        userService.lockForUpdate(userId);
        List<Progress> existing = progressRepository.findByUserIdAndTaskIdIn(userId, taskIds);
        if (!existing.isEmpty()) {
            progressRepository.deleteAll(existing);
        }
        for (Long taskId : taskIds) {
            long current = pointsService.forTask(userId, taskId);
            if (current != 0) {
                pointsService.correct(userId, taskId, (int) -current, note, actorId);
            }
        }
    }
}

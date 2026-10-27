package de.codepath.backend.progress;

import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.audit.AuditService;
import lombok.RequiredArgsConstructor;
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
}

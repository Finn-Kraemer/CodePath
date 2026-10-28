package de.codepath.backend.progress;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Stand einer Schülerin, eines Schülers bei einer Aufgabe. Eindeutig je Konto und Aufgabe. */
@Entity
@Table(name = "progress")
@IdClass(Progress.Key.class)
@Getter @Setter @NoArgsConstructor
public class Progress {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "task_id")
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProgressStatus status = ProgressStatus.OPEN;

    /** Vom Server gesetzt, wenn die Aufgabe zum ersten Mal geöffnet wird */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "hints_used", nullable = false)
    private int hintsUsed;

    @Column(name = "solution_revealed", nullable = false)
    private boolean solutionRevealed;

    /** Von der Lehrkraft gesperrt */
    @Column(nullable = false)
    private boolean locked;

    @Column(name = "best_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal bestScore = BigDecimal.ZERO;

    @Column(name = "time_spent_seconds", nullable = false)
    private long timeSpentSeconds;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    public Progress(Long userId, Long taskId) {
        this.userId = userId;
        this.taskId = taskId;
    }

    public boolean isFinished() {
        return status == ProgressStatus.SOLVED || status == ProgressStatus.FAILED;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Key implements Serializable {
        private Long userId;
        private Long taskId;
    }
}

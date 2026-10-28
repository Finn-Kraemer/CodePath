package de.codepath.backend.review;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** Bewertung einer Praxisabgabe durch die Lehrkraft: Punkte je Rubrik-Kriterium und Kommentar. */
@Entity
@Table(name = "practice_reviews")
@Getter @Setter @NoArgsConstructor
public class PracticeReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false, unique = true)
    private Long attemptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status = ReviewStatus.PENDING;

    /** Kriterium-ID → vergebene Punkte */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Integer> scores = new HashMap<>();

    @Column(name = "awarded_points")
    private Integer awardedPoints;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "lock_task", nullable = false)
    private boolean lockTask;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public PracticeReview(Long attemptId) {
        this.attemptId = attemptId;
    }
}

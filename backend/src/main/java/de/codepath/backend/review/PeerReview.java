package de.codepath.backend.review;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/** Anonyme Bewertung einer Praxisabgabe durch eine Mitschülerin oder einen Mitschüler. */
@Entity
@Table(name = "peer_reviews")
@Getter @Setter @NoArgsConstructor
public class PeerReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "practice_review_id", nullable = false)
    private Long practiceReviewId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** Kriterium-ID → Punkte; null, solange nicht abgeschickt */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Integer> scores;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    public boolean isSubmitted() {
        return submittedAt != null;
    }
}

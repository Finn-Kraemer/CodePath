package de.codepath.backend.evaluation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** Ein ausgefüllter Bogen: Kriterium-ID → Antwort (Zahl, Ja/Nein, Text oder Liste von Optionen). */
@Entity
@Table(name = "evaluation_responses")
@Getter @Setter @NoArgsConstructor
public class EvaluationResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvaluationPart part;

    /** Um wen es geht; bei SELF und LESSON die ausfüllende Person */
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    /** Wer ausgefüllt hat (bei TEACHER die Lehrkraft) */
    @Column(name = "author_id")
    private Long authorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> answers = new HashMap<>();

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

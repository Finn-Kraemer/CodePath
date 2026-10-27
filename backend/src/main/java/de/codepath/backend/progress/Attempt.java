package de.codepath.backend.progress;

import de.codepath.backend.engine.Evaluation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** Jeder Versuch wird gespeichert – mit der Version der Aufgabe, gegen die er bewertet wurde. */
@Entity
@Table(name = "attempts")
@Getter @Setter @NoArgsConstructor
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "task_version_id")
    private Long taskVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> answer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Evaluation.Outcome outcome;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal score = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

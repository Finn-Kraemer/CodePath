package de.codepath.backend.evaluation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Eine Bewertungsrunde für einen Kurs mit bis zu drei Bögen: Selbsteinschätzung, Unterrichtsbewertung
 * und Bewertung durch die Lehrkraft. Die Kriterien sind Kopien – eine spätere Änderung der Vorlage
 * verändert laufende Runden nicht.
 */
@Entity
@Table(name = "evaluation_rounds")
@Getter @Setter @NoArgsConstructor
public class EvaluationRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String intro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoundStatus status = RoundStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "self_criteria", columnDefinition = "jsonb")
    private List<Map<String, Object>> selfCriteria;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lesson_criteria", columnDefinition = "jsonb")
    private List<Map<String, Object>> lessonCriteria;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "teacher_criteria", columnDefinition = "jsonb")
    private List<Map<String, Object>> teacherCriteria;

    /** Die Lehrkraft bewertet nach denselben Kriterien wie die Selbsteinschätzung (direkter Vergleich) */
    @Column(name = "teacher_uses_self_criteria", nullable = false)
    private boolean teacherUsesSelfCriteria;

    /** Unterrichtsbewertung ohne Namen für die Lehrkraft */
    @Column(name = "lesson_anonymous", nullable = false)
    private boolean lessonAnonymous = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "teacher_visibility", nullable = false)
    private TeacherVisibility teacherVisibility = TeacherVisibility.HIDDEN;

    /** Schüler dürfen abgeschickte Bögen ändern, solange die Runde offen ist */
    @Column(name = "allow_edit", nullable = false)
    private boolean allowEdit = true;

    @Column(name = "closes_at")
    private LocalDateTime closesAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Offen und Frist nicht abgelaufen */
    public boolean isAcceptingAnswers() {
        return status == RoundStatus.OPEN && (closesAt == null || LocalDateTime.now().isBefore(closesAt));
    }
}

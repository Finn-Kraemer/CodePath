package de.codepath.backend.help;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Ein stiller Hilfe-Ruf „Ich hänge fest“. Sichtbar nur für die rufende Person (ohne Entwurf) und Lehrkräfte. */
@Entity
@Table(name = "help_requests")
@Getter @Setter @NoArgsConstructor
public class HelpRequest {

    public enum Status { OPEN, CLAIMED, RESOLVED, CANCELLED }

    public enum Resolution { TEACHER, SELF_SOLVED, CANCELLED }

    public enum Category { TASK_UNCLEAR, ERROR_MESSAGE, TECHNICAL, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    private Category category;

    private String note;

    @Column(columnDefinition = "TEXT")
    private String draft;

    @Column(name = "last_output", columnDefinition = "TEXT")
    private String lastOutput;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "claimed_by")
    private Long claimedBy;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Enumerated(EnumType.STRING)
    private Resolution resolution;

    @Column(name = "teacher_message")
    private String teacherMessage;

    public boolean isActive() {
        return status == Status.OPEN || status == Status.CLAIMED;
    }
}

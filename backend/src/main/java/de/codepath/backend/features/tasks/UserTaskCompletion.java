package de.codepath.backend.features.tasks;

import de.codepath.backend.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_task_completions")
@IdClass(UserTaskCompletionId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UserTaskCompletion {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "completed_at")
    private LocalDateTime completedAt = LocalDateTime.now();

    @Column(name = "points_awarded", nullable = false)
    private Integer pointsAwarded;
}

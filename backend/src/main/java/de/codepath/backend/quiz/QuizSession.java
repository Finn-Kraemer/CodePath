package de.codepath.backend.quiz;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Eine Quiz-Runde: Lobby → Frage → Auflösung → … → beendet. */
@Entity
@Table(name = "quiz_sessions")
@Getter @Setter @NoArgsConstructor
public class QuizSession {

    public enum Phase { LOBBY, QUESTION, REVEAL, FINISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(nullable = false)
    private String title;

    @Column(name = "task_ids", nullable = false)
    private String taskIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Phase phase = Phase.LOBBY;

    @Column(name = "current_index", nullable = false)
    private int currentIndex = -1;

    @Column(name = "question_started_at")
    private LocalDateTime questionStartedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public List<Long> taskIdList() {
        return Arrays.stream(taskIds.split(",")).filter(s -> !s.isBlank()).map(Long::valueOf).toList();
    }

    public void setTaskIdList(List<Long> ids) {
        this.taskIds = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}

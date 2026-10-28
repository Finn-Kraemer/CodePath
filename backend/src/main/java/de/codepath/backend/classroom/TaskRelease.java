package de.codepath.backend.classroom;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Freigabe einer einzelnen Aufgabe je Kurs. Keine Zeile = die Aufgabe folgt ihrem Modul. */
@Entity
@Table(name = "task_releases")
@IdClass(TaskRelease.Key.class)
@Getter @Setter @NoArgsConstructor
public class TaskRelease {

    @Id
    @Column(name = "course_id")
    private Long courseId;

    @Id
    @Column(name = "task_id")
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskReleaseMode mode;

    public TaskRelease(Long courseId, Long taskId, TaskReleaseMode mode) {
        this.courseId = courseId;
        this.taskId = taskId;
        this.mode = mode;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Key implements Serializable {
        private Long courseId;
        private Long taskId;
    }
}

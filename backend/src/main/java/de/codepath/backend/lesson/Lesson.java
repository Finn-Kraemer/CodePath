package de.codepath.backend.lesson;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Ein Stundenablauf. Schritte, Freigaben-Sicherung und Protokoll liegen als JSON-Text vor. */
@Entity
@Table(name = "lessons")
@Getter @Setter @NoArgsConstructor
public class Lesson {

    public enum Status { DRAFT, RUNNING, DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id")
    private Long courseId;

    @Column(nullable = false)
    private String title;

    @Column(name = "lesson_date")
    private LocalDate date;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String steps = "[]";

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "current_step", nullable = false)
    private int currentStep = -1;

    @Column(name = "step_started_at")
    private LocalDateTime stepStartedAt;

    @Column(name = "restore_releases", nullable = false)
    private boolean restoreReleases;

    @Column(name = "releases_snapshot", columnDefinition = "TEXT")
    private String releasesSnapshot;

    @Column(name = "run_log", nullable = false, columnDefinition = "TEXT")
    private String runLog = "[]";

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

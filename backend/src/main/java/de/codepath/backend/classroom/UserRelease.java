package de.codepath.backend.classroom;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Freigabe eines Moduls oder einer Aufgabe für eine einzelne Person. Geht den Kursfreigaben vor
 * (siehe {@link AccessPolicy}). Genau eins von {@code moduleId} und {@code taskId} ist gesetzt.
 */
@Entity
@Table(name = "user_releases")
@Getter @Setter @NoArgsConstructor
public class UserRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "module_id")
    private Long moduleId;

    @Column(name = "task_id")
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskReleaseMode mode;

    /** Eigene Frist der Person; ohne gilt die Frist des Moduls im Kurs. */
    @Column(name = "available_until")
    private LocalDateTime availableUntil;

    /** Zusatzaufgabe: Punkte zählen nicht in der Rangliste. */
    @Column(nullable = false)
    private boolean bonus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

package de.codepath.backend.pathway;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "learning_paths")
@Getter @Setter @NoArgsConstructor
public class LearningPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description = "";

    /** Knoten und Verbindungen als JSON, siehe {@link PathGraph} */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String graph;

    @Column(nullable = false)
    private boolean published;

    /** Kurs, für den der Lernpfad gilt; null für alle Kurse (ändert dann keine Freigaben) */
    @Column(name = "course_id")
    private Long courseId;

    /** Beim Übernehmen in die Modul-Steuerung Module sperren, die im Lernpfad nicht vorkommen */
    @Column(name = "lock_others", nullable = false)
    private boolean lockOthers;

    @Column(nullable = false)
    private int position;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

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

    /** Verbindlicher Lernpfad: Pflicht-Vorgänger müssen erledigt sein, bevor eine Station frei wird */
    @Column(nullable = false)
    private boolean enforce;

    /** Kurs, für den der Lernpfad gilt; null für alle Kurse (ändert dann keine Freigaben) */
    @Column(name = "course_id")
    private Long courseId;

    /** Beim Übernehmen in die Modul-Steuerung Module sperren, die im Lernpfad nicht vorkommen */
    @Column(name = "lock_others", nullable = false)
    private boolean lockOthers;

    /** Station-ID, ab deren Erreichen alle Module und Aufgaben außerhalb des Lernpfads frei werden */
    @Column(name = "unlock_rest_after")
    private String unlockRestAfter;

    /** Nur als wiederverwendbarer Baustein für andere Lernpfade gedacht (erscheint nicht einzeln in der Schüler-Auswahl) */
    @Column(name = "template_only", nullable = false)
    private boolean templateOnly;

    /** Fokus-Modus: Schüler sehen nur den Lernpfad; Modul-Kacheln und Liste sind ausgeblendet */
    @Column(name = "hide_overview", nullable = false)
    private boolean hideOverview;

    @Column(nullable = false)
    private int position;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

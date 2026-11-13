package de.codepath.backend.observation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Klassenraum und Beobachtungsbogen eines Kurses; ohne Kurs eine Vorlage (Raum + Kriterien). */
@Entity
@Table(name = "observation_boards")
@Getter @Setter @NoArgsConstructor
public class ObservationBoard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id")
    private Long courseId;

    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String layout = "{\"items\":[]}";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

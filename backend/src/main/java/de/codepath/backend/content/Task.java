package de.codepath.backend.content;

import de.codepath.backend.engine.TaskType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stabile Identität einer Aufgabe. Texte, Punkte und Konfiguration stehen in {@link TaskVersion};
 * Schüler sehen immer die aktuelle veröffentlichte Version.
 */
@Entity
@Table(name = "tasks")
@Getter @Setter @NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "module_id")
    private Module module;

    @Column(nullable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    // Eager: andere Pakete arbeiten mit der aktuellen Version außerhalb der Transaktion
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "current_version_id")
    private TaskVersion currentVersion;

    @Column(nullable = false)
    private boolean archived = false;
}

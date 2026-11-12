package de.codepath.backend.journal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Eine Notiz im Lerntagebuch: je Person und Modul, optional je Aufgabe. */
@Entity
@Table(name = "journal_entries")
@Getter @Setter @NoArgsConstructor
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    /** Mit der Lehrkraft geteilt, z. B. als Frage */
    @Column(nullable = false)
    private boolean shared;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

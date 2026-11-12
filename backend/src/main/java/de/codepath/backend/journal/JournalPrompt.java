package de.codepath.backend.journal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Leitfragen der Lehrkraft für das Lerntagebuch eines Moduls, eine je Zeile. */
@Entity
@Table(name = "journal_prompts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class JournalPrompt {

    @Id
    @Column(name = "module_id")
    private Long moduleId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompts;
}

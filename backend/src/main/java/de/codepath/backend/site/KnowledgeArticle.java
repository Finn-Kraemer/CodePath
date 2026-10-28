package de.codepath.backend.site;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_articles")
@Getter @Setter @NoArgsConstructor
public class KnowledgeArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String icon = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body = "";

    @Column(name = "module_slug")
    private String moduleSlug;

    @Column(nullable = false)
    private boolean published = true;

    @Column(nullable = false)
    private int position;

    /** MD5 des mitgelieferten Textes; passt der Text noch dazu, darf der Start ihn aktualisieren. */
    @Column(name = "seeded_hash")
    private String seededHash;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

package de.codepath.backend.content;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Eine Fassung einer Aufgabe. Nach der Veröffentlichung unveränderlich: Versuche hängen an ihrer
 * Version, damit spätere Korrekturen keine Ergebnisse verfälschen. Ohne publishedAt ist sie ein Entwurf.
 */
@Entity
@Table(name = "task_versions")
@Getter @Setter @NoArgsConstructor
public class TaskVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String title;

    @Column(name = "learning_goal", columnDefinition = "TEXT")
    private String learningGoal;

    /** Markdown */
    @Column(columnDefinition = "TEXT")
    private String story;

    /** Adresse (slug) eines passenden Artikels der Wissensdatenbank */
    @Column(name = "knowledge_article")
    private String knowledgeArticle;

    /** Markdown: der eigentliche Auftrag */
    @Column(columnDefinition = "TEXT")
    private String assignment;

    /** Markdown: Erklärung nach dem Lösen */
    @Column(columnDefinition = "TEXT")
    private String explanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> hints = new ArrayList<>();

    @Column(nullable = false)
    private int points;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> config = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> rules = new HashMap<>();

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    public boolean isPublished() {
        return publishedAt != null;
    }
}

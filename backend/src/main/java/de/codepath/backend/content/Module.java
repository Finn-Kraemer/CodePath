package de.codepath.backend.content;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "modules")
@Getter @Setter @NoArgsConstructor
public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "icon_emoji")
    private String icon;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /** Berufsfeld(er) für den Berufe-Kompass, z. B. "Softwareentwickler/in" */
    private String career;

    /** Links zum Weiterlernen für das Mitnehm-Paket: je Eintrag title und url */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "learn_more", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, String>> learnMore = new ArrayList<>();
}

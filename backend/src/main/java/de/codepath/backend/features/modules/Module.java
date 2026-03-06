package de.codepath.backend.features.modules;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "modules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
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
    private String iconEmoji;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "is_unlocked")
    private Boolean isUnlocked = false;
}

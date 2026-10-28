package de.codepath.backend.content;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}

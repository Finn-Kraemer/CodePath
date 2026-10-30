package de.codepath.backend.pathway;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "learning_path_overrides")
@Getter
@Setter
@NoArgsConstructor
public class LearningPathOverride {

    public static final String UNLOCK_NODE = "UNLOCK_NODE";
    public static final String UNLOCK_BRANCH = "UNLOCK_BRANCH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "path_id", nullable = false)
    private Long pathId;

    @Column(name = "node_id", nullable = false, length = 80)
    private String nodeId;

    @Column(nullable = false, length = 30)
    private String kind;

    @Column(nullable = false, length = 80)
    private String target = "";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;
}

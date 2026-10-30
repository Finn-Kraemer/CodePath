package de.codepath.backend.pathway;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "learning_path_branch_results")
@Getter
@Setter
@NoArgsConstructor
public class LearningPathBranchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "path_id", nullable = false)
    private Long pathId;

    @Column(name = "edge_key", nullable = false, length = 120)
    private String edgeKey;

    @Column(name = "matched", nullable = false)
    private boolean matched;

    @Column(name = "rule", length = 40)
    private String rule;

    @Column(name = "detail", length = 255)
    private String detail;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

    public LearningPathBranchResult(Long userId, Long pathId, String edgeKey, boolean matched, String rule, String detail) {
        this.userId = userId;
        this.pathId = pathId;
        this.edgeKey = edgeKey;
        this.matched = matched;
        this.rule = rule;
        this.detail = detail;
        this.decidedAt = LocalDateTime.now();
    }
}

package de.codepath.backend.pathway;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "learning_path_choices")
@Getter
@Setter
@NoArgsConstructor
public class LearningPathChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "path_id", nullable = false)
    private Long pathId;

    @Column(name = "node_id", nullable = false, length = 40)
    private String nodeId;

    @Column(name = "chosen_edge_to", nullable = false, length = 40)
    private String chosenEdgeTo;

    @Column(name = "chosen_at", nullable = false)
    private LocalDateTime chosenAt = LocalDateTime.now();

    @Column(name = "chosen_by", nullable = false, length = 60)
    private String chosenBy;
}

package de.codepath.backend.observation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Eine Zeile im Bogen: Schülerkonto ({@code userId}) oder frei eingetragener Name ({@code name}). */
@Entity
@Table(name = "observation_people")
@Getter @Setter @NoArgsConstructor
public class ObservationPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(name = "user_id")
    private Long userId;

    private String name;

    @Column(name = "seat_id")
    private String seatId;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

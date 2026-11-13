package de.codepath.backend.observation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Aktueller Wert einer Person in einem Kriterium (Zahl als Text oder Wert der gewählten Option). */
@Entity
@Table(name = "observation_ratings")
@IdClass(ObservationRating.Key.class)
@Getter @Setter @NoArgsConstructor
public class ObservationRating {

    @Id
    @Column(name = "person_id")
    private Long personId;

    @Id
    @Column(name = "criterion_id")
    private Long criterionId;

    @Column(nullable = false)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ObservationRating(Long personId, Long criterionId, String value) {
        this.personId = personId;
        this.criterionId = criterionId;
        this.value = value;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Key implements Serializable {
        private Long personId;
        private Long criterionId;
    }
}

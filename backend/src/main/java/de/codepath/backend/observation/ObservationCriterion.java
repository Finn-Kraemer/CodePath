package de.codepath.backend.observation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Eine Spalte im Beobachtungsbogen: Zahl (optional mit Bereich) oder Auswahlliste (Optionen als JSON). */
@Entity
@Table(name = "observation_criteria")
@Getter @Setter @NoArgsConstructor
public class ObservationCriterion {

    public enum Kind { NUMBER, CHOICE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind = Kind.NUMBER;

    @Column(name = "min_value")
    private BigDecimal minValue;

    @Column(name = "max_value")
    private BigDecimal maxValue;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String options = "[]";
}

package de.codepath.backend.identity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Eine Klasse bzw. Gruppe. Schüler treten mit dem Beitrittscode bei. */
@Entity
@Table(name = "courses")
@Getter @Setter @NoArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "join_code", nullable = false, unique = true)
    private String joinCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseStatus status = CourseStatus.ACTIVE;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isActive() {
        return status == CourseStatus.ACTIVE;
    }
}

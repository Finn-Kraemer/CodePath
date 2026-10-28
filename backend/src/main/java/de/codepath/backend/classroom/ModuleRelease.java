package de.codepath.backend.classroom;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Freischaltung und Frist eines Moduls je Kurs. Keine Zeile = gesperrt. */
@Entity
@Table(name = "module_releases")
@IdClass(ModuleRelease.Key.class)
@Getter @Setter @NoArgsConstructor
public class ModuleRelease {

    @Id
    @Column(name = "course_id")
    private Long courseId;

    @Id
    @Column(name = "module_id")
    private Long moduleId;

    @Column(nullable = false)
    private boolean unlocked;

    @Column(name = "available_until")
    private LocalDateTime availableUntil;

    public ModuleRelease(Long courseId, Long moduleId) {
        this.courseId = courseId;
        this.moduleId = moduleId;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Key implements Serializable {
        private Long courseId;
        private Long moduleId;
    }
}

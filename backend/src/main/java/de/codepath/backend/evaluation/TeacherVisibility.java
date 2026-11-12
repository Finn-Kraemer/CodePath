package de.codepath.backend.evaluation;

/** Ob und wann Schülerinnen und Schüler die Bewertung der Lehrkraft sehen. */
public enum TeacherVisibility {
    /** Nur die Lehrkraft sieht ihre Bewertung */
    HIDDEN,
    /** Sichtbar, sobald die Lehrkraft bewertet hat */
    VISIBLE,
    /** Sichtbar erst nach der eigenen Selbsteinschätzung – damit die Selbsteinschätzung unbeeinflusst bleibt */
    AFTER_SELF
}

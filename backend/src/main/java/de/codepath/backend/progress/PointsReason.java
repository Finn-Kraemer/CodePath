package de.codepath.backend.progress;

public enum PointsReason {
    /** Aufgabe gelöst (inkl. Bonus, abzüglich Hinweise) */
    SOLVED,
    /** Teilpunkte, nachdem alle Versuche aufgebraucht waren */
    PARTIAL,
    /** Praxisabgabe von der Lehrkraft bewertet */
    REVIEW,
    /** Bonus-Station auf einem Lernpfad erreicht */
    PATH_BONUS,
    /** Korrektur durch die Lehrkraft */
    CORRECTION
}

package de.codepath.backend.identity;

/**
 * ACTIVE: normaler Betrieb. ARCHIVED: Schüler können sich nicht anmelden, die Lehrkraft liest und exportiert
 * weiter, nach {@code purge_after} wird gelöscht wie beim Beenden. ENDED: Schülerdaten gelöscht oder
 * Mitglieder in einen Folgekurs übernommen.
 */
public enum CourseStatus {
    ACTIVE, ARCHIVED, ENDED
}

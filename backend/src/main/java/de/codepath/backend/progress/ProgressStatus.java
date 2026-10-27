package de.codepath.backend.progress;

public enum ProgressStatus {
    /** Begonnen, noch nicht gelöst */
    OPEN,
    /** Gelöst (Punkte vergeben, ggf. 0 nach aufgedeckter Lösung) */
    SOLVED,
    /** Praxisabgabe wartet auf die Lehrkraft */
    PENDING_REVIEW,
    /** Alle Versuche aufgebraucht */
    FAILED,
    /** Code als Übung gespeichert (kein Code-Runner, keine Punkte) */
    PRACTICED
}

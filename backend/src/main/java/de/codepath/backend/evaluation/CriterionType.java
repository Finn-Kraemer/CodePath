package de.codepath.backend.evaluation;

public enum CriterionType {
    /** Zwischenüberschrift ohne Antwort */
    HEADING,
    /** Skala mit ganzen Zahlen von min bis max (Zahlen oder Sterne) */
    SCALE,
    /** Auswahl aus Optionen, einfach oder mehrfach */
    CHOICE,
    /** Ja/Nein */
    YES_NO,
    /** Freitext */
    TEXT
}

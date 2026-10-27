package de.codepath.backend.engine;

import java.util.Map;

/**
 * Ergebnis der Bewertung einer Antwort.
 *
 * @param outcome  CORRECT, WRONG, PARTIAL (Teil richtig), MANUAL (Lehrkraft bewertet), UNGRADED (Übung ohne Punkte),
 *                 INVALID (unvollständige Eingabe – zählt nicht als Versuch)
 * @param score    Anteil 0–1 für Teilpunkte
 * @param feedback Rückmeldung für Schüler, verrät keine Lösung
 * @param details  zusätzliche Rückmeldung je Typ, z. B. welche Lücke stimmt oder die Ergebnistabelle
 */
public record Evaluation(Outcome outcome, double score, String feedback, Map<String, Object> details) {

    public enum Outcome { CORRECT, PARTIAL, WRONG, MANUAL, UNGRADED, INVALID }

    public Evaluation {
        score = Math.clamp(score, 0.0, 1.0);
        details = details != null ? details : Map.of();
    }

    public static Evaluation correct(String feedback) {
        return new Evaluation(Outcome.CORRECT, 1.0, feedback, Map.of());
    }

    public static Evaluation wrong(String feedback) {
        return new Evaluation(Outcome.WRONG, 0.0, feedback, Map.of());
    }

    /** Unvollständige oder unlesbare Eingabe: kein Versuch, keine Wertung. */
    public static Evaluation invalid(String feedback) {
        return new Evaluation(Outcome.INVALID, 0.0, feedback, Map.of());
    }

    /** Teilweise richtig: score zwischen 0 und 1. Bei 1 gilt die Antwort als richtig. */
    public static Evaluation scored(double score, String feedback, Map<String, Object> details) {
        if (score >= 1.0) return new Evaluation(Outcome.CORRECT, 1.0, feedback, details);
        if (score <= 0.0) return new Evaluation(Outcome.WRONG, 0.0, feedback, details);
        return new Evaluation(Outcome.PARTIAL, score, feedback, details);
    }

    public Evaluation withDetails(Map<String, Object> newDetails) {
        return new Evaluation(outcome, score, feedback, newDetails);
    }

    public boolean isCorrect() {
        return outcome == Outcome.CORRECT;
    }
}

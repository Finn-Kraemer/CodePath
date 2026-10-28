package de.codepath.backend.progress;

import de.codepath.backend.engine.AttemptRules;

/**
 * Punkte nach den einheitlichen Regeln: Anteil × Punkte, minus Abzug je Hinweis, plus Bonus für das
 * Lösen im ersten Versuch ohne Hinweis. Nach aufgedeckter Lösung gibt es nichts.
 */
public final class ScoreCalculator {

    private ScoreCalculator() {
    }

    public static int points(int basePoints, double score, int hintsUsed, AttemptRules rules, boolean firstTry, boolean revealed) {
        if (revealed || basePoints <= 0 || score <= 0) return 0;
        int penaltyPercent = Math.min(AttemptRules.MAX_TOTAL_HINT_PENALTY_PERCENT, hintsUsed * rules.hintPenaltyPercent());
        double value = basePoints * Math.min(score, 1.0) * (100 - penaltyPercent) / 100.0;
        boolean fullySolved = score >= 1.0;
        if (fullySolved && firstTry && hintsUsed == 0) {
            value += basePoints * rules.firstTryBonusPercent() / 100.0;
        }
        int result = (int) Math.round(value);
        // Wer selbst richtig löst, bekommt mindestens einen Punkt
        return fullySolved ? Math.max(1, result) : result;
    }

    /** Punkte für eine bewertete Praxisabgabe: Rubrik-Punkte minus Hinweis-Abzug. */
    public static int reviewPoints(int rubricPoints, int hintsUsed, AttemptRules rules) {
        int penaltyPercent = Math.min(AttemptRules.MAX_TOTAL_HINT_PENALTY_PERCENT, hintsUsed * rules.hintPenaltyPercent());
        return (int) Math.round(rubricPoints * (100 - penaltyPercent) / 100.0);
    }
}

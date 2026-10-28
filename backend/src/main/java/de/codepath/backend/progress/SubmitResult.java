package de.codepath.backend.progress;

import de.codepath.backend.engine.Evaluation;

import java.util.Map;

/**
 * Antwort auf eine Abgabe. Enthält nie die Lösung – die gibt es nur über das Aufdecken
 * oder nach dem Lösen in der Aufgabenansicht.
 */
public record SubmitResult(
        Evaluation.Outcome outcome,
        boolean isCorrect,
        double score,
        String feedback,
        Map<String, Object> details,
        int pointsAwarded,
        ProgressStatus status,
        int failedAttempts,
        Integer attemptsLeft) {
}

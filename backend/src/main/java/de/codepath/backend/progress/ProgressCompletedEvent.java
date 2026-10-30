package de.codepath.backend.progress;

/**
 * Wird ausgelöst, sobald eine Aufgabe für eine Person abgeschlossen wurde (gelöst, geübt oder
 * Versuche verbraucht). Dient u. a. zum Buchen von Lernpfad-Boni und zum Auswerten bedingter Kanten.
 */
public record ProgressCompletedEvent(
        Long userId,
        Long taskId,
        ProgressStatus status,
        double bestScore,
        int failedAttempts,
        int hintsUsed,
        boolean solutionRevealed
) {
    public boolean perfectFirstTry() {
        return status == ProgressStatus.SOLVED && failedAttempts == 0 && hintsUsed == 0 && !solutionRevealed && bestScore >= 1.0;
    }
}

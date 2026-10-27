package de.codepath.backend.progress;

/** Eine Praxisabgabe wartet auf Bewertung; das review-Paket legt dazu eine Bewertung an. */
public record PracticeSubmittedEvent(Long attemptId, Long userId, Long taskId) {
}

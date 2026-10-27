package de.codepath.backend.engine;

import java.util.Random;

/**
 * Kontext einer Bewertung. Der Seed ist je Schüler und Aufgabe fest: Gemischte Reihenfolgen und
 * zufällige Werte (z. B. bei Zahlenumrechnung) bleiben beim Neuladen gleich, unterscheiden sich aber
 * vom Nachbarn.
 */
public record TaskContext(long seed) {

    public static TaskContext of(long userId, long taskId) {
        return new TaskContext(31L * userId + 1_000_003L * taskId);
    }

    public Random random() {
        return new Random(seed);
    }

    public Random random(String purpose) {
        return new Random(seed ^ purpose.hashCode());
    }
}

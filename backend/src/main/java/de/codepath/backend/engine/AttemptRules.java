package de.codepath.backend.engine;

import java.util.Map;

/**
 * Einheitliche Regeln für Versuche, Hinweise und Punkte. Pro Aufgabe überschreibbar, Standard je Typ.
 *
 * @param maxAttempts          null = unbegrenzt
 * @param hintPenaltyPercent   Abzug je angefordertem Hinweis in Prozent der Punkte
 * @param firstTryBonusPercent Bonus für das Lösen im ersten Versuch ohne Hinweis
 * @param revealAllowed        ob die Lösung aufgedeckt werden darf (danach 0 Punkte)
 */
public record AttemptRules(Integer maxAttempts, int hintPenaltyPercent, int firstTryBonusPercent, boolean revealAllowed) {

    public static final int MAX_TOTAL_HINT_PENALTY_PERCENT = 75;

    public static AttemptRules defaultsFor(TaskType type) {
        return switch (type) {
            case MULTIPLE_CHOICE, FILL_BLANK, SORTING, MATCHING, HOTSPOT, IMAGE_HOTSPOT -> new AttemptRules(3, 25, 20, true);
            case NUMBER_CONVERT, PARSONS -> new AttemptRules(5, 25, 20, true);
            case LOGIC_CIRCUIT -> new AttemptRules(null, 25, 20, true);
            case CODE, FILL_CODE, SQL, TERMINAL -> new AttemptRules(null, 25, 20, true);
            case PRACTICE -> new AttemptRules(null, 25, 0, false);
        };
    }

    /** Überlagert die Standardwerte des Typs mit den Feldern, die in der Aufgabe gesetzt sind. */
    public static AttemptRules from(TaskType type, Map<String, Object> overrides) {
        AttemptRules d = defaultsFor(type);
        if (overrides == null || overrides.isEmpty()) return d;
        Integer max = overrides.containsKey("maxAttempts")
                ? (overrides.get("maxAttempts") instanceof Number n && n.intValue() > 0 ? n.intValue() : null)
                : d.maxAttempts();
        return new AttemptRules(
                max,
                intOr(overrides.get("hintPenaltyPercent"), d.hintPenaltyPercent()),
                intOr(overrides.get("firstTryBonusPercent"), d.firstTryBonusPercent()),
                overrides.get("revealAllowed") instanceof Boolean b ? b : d.revealAllowed());
    }

    private static int intOr(Object value, int fallback) {
        return value instanceof Number n ? Math.clamp(n.intValue(), 0, 100) : fallback;
    }
}

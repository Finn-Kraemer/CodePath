package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.Cfg;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vergleicht eine Lückenantwort mit den akzeptierten Antworten: Leerzeichen am Rand und mehrfache
 * Leerzeichen zählen nicht, Groß-/Kleinschreibung nur bei caseSensitive.
 */
final class BlankMatcher {

    private BlankMatcher() {
    }

    static boolean matches(Map<String, Object> blank, Object given) {
        if (!(given instanceof String || given instanceof Number)) return false;
        String actual = normalize(String.valueOf(given));
        boolean caseSensitive = Cfg.bool(blank, "caseSensitive");
        for (String accepted : Cfg.strings(blank, "answers")) {
            String expected = normalize(accepted);
            if (caseSensitive ? expected.equals(actual) : expected.equalsIgnoreCase(actual)) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }

    static String firstAnswer(Map<String, Object> blank) {
        List<String> answers = Cfg.strings(blank, "answers");
        return answers.isEmpty() ? "" : answers.getFirst();
    }

    static String lowerFirst(Map<String, Object> blank) {
        return firstAnswer(blank).toLowerCase(Locale.ROOT);
    }
}

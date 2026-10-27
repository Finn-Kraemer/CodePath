package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.Cfg;
import de.codepath.backend.engine.CodeRunner;
import de.codepath.backend.engine.Evaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lässt Code gegen die Testfälle einer Aufgabe laufen: { tests: [{stdin?, expectedOutput, hidden?}] }.
 * Tests sind standardmäßig versteckt; versteckte Tests verraten in der Rückmeldung weder Eingabe noch erwartete Ausgabe.
 */
final class CodeTests {

    private CodeTests() {
    }

    static List<String> validate(Map<String, Object> config) {
        List<Map<String, Object>> tests = Cfg.objects(config, "tests");
        return new Cfg.Problems()
                .require(!tests.isEmpty(), "Mindestens ein Testfall")
                .require(tests.stream().allMatch(t -> Cfg.string(t, "expectedOutput") != null), "Jeder Testfall braucht expectedOutput")
                .list();
    }

    static Evaluation run(CodeRunner runner, String code, Map<String, Object> config) {
        List<Map<String, Object>> tests = Cfg.objects(config, "tests");
        List<Map<String, Object>> results = new ArrayList<>();
        int passed = 0;
        String firstError = null;
        for (int i = 0; i < tests.size(); i++) {
            Map<String, Object> test = tests.get(i);
            CodeRunner.RunResult result = runner.run(code, Cfg.string(test, "stdin", ""));
            if (result.status() == CodeRunner.RunResult.Status.UNAVAILABLE) {
                return new Evaluation(Evaluation.Outcome.UNGRADED, 0, "Der Code-Prüfer ist gerade nicht erreichbar.", Map.of());
            }
            boolean ok = result.status() == CodeRunner.RunResult.Status.OK
                    && sameOutput(result.stdout(), Cfg.string(test, "expectedOutput", ""));
            if (ok) passed++;
            if (!ok && firstError == null) {
                firstError = switch (result.status()) {
                    case TIMEOUT -> "Zeitlimit überschritten – gibt es eine Endlosschleife?";
                    case ERROR -> "Fehler beim Ausführen: " + lastLine(result.stderr());
                    default -> "Die Ausgabe stimmt noch nicht.";
                };
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", "Test " + (i + 1));
            entry.put("passed", ok);
            if (!isHidden(test)) {
                entry.put("stdin", Cfg.string(test, "stdin", ""));
                entry.put("output", truncate(result.stdout()));
            }
            results.add(entry);
        }
        String feedback = passed == tests.size()
                ? "Alle " + tests.size() + " Tests bestanden!"
                : passed + " von " + tests.size() + " Tests bestanden. " + firstError;
        return Evaluation.scored(tests.isEmpty() ? 0 : (double) passed / tests.size(), feedback, Map.of("tests", results));
    }

    /** Testfälle sind versteckt, solange sie nicht ausdrücklich hidden: false haben. */
    static boolean isHidden(Map<String, Object> test) {
        return !Boolean.FALSE.equals(test.get("hidden"));
    }

    /** Zeilenenden und Leerzeichen am Zeilenende spielen keine Rolle. */
    static boolean sameOutput(String actual, String expected) {
        return normalize(actual).equals(normalize(expected));
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n").replace("\r", "\n").lines().map(String::stripTrailing)
                .reduce((a, b) -> a + "\n" + b).orElse("").strip();
    }

    private static String lastLine(String text) {
        if (text == null || text.isBlank()) return "unbekannt";
        List<String> lines = text.strip().lines().toList();
        return lines.getLast();
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > 2000 ? text.substring(0, 2000) + "…" : text;
    }
}

package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Config: { starterCode, tests: [{stdin?, append?, expectedOutput, hidden?}], solution }
 * Antwort: { code }
 * Bewertung nur mit Code-Runner (ADR-4); ohne Runner ist die Aufgabe eine Übung ohne Punkte.
 */
@Component
public class CodeHandler implements TaskHandler {

    static final int MAX_CODE_LENGTH = 20_000;

    private final CodeRunner runner;

    public CodeHandler(CodeRunner runner) {
        this.runner = runner;
    }

    @Override
    public TaskType type() {
        return TaskType.CODE;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        List<String> problems = new java.util.ArrayList<>(CodeTests.validate(config));
        if (Cfg.string(config, "solution", "").isBlank()) {
            problems.add("Musterlösung fehlt – ohne sie lassen sich die Testfälle nicht im Probelauf prüfen");
        }
        return problems;
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        return Map.of(
                "starterCode", Cfg.string(spec.config(), "starterCode", ""),
                "graded", runner.available(),
                "examples", visibleExamples(spec.config()));
    }

    /** Nicht versteckte Testfälle dienen als Beispiele; versteckte prüfen, ob die Lösung allgemein ist. */
    static List<Map<String, Object>> visibleExamples(Map<String, Object> config) {
        return Cfg.objects(config, "tests").stream()
                .filter(t -> !CodeTests.isHidden(t))
                .map(t -> Map.<String, Object>of("stdin", Cfg.string(t, "stdin", ""), "expectedOutput", Cfg.string(t, "expectedOutput", "")))
                .toList();
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        String code = Cfg.string(answer, "code");
        if (code == null || code.isBlank()) return Evaluation.invalid("Bitte schreibe zuerst Code.");
        if (code.length() > MAX_CODE_LENGTH) return Evaluation.invalid("Der Code ist zu lang.");
        if (!runner.available()) {
            return new Evaluation(Evaluation.Outcome.UNGRADED, 0,
                    "Gespeichert. Code-Aufgaben sind auf diesem Server eine Übung ohne Punkte.", Map.of());
        }
        return CodeTests.run(runner, code, spec.config());
    }

    /** Für den Editor: Code so ausführen, wie ein Testfall ihn ausführen würde. */
    public CodeRunner.RunResult runAsTest(String code, Map<String, Object> test) {
        return runner.run(CodeTests.program(code, test), Cfg.string(test, "stdin", ""));
    }

    public boolean graded() {
        return runner.available();
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        String solution = Cfg.string(spec.config(), "solution");
        return solution != null ? Map.of("code", solution) : Map.of();
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("code", Cfg.string(spec.config(), "solution", ""));
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("code", "print('falsch')");
    }

    @Override
    public Map<String, Object> template() {
        return Map.of(
                "starterCode", "zahl = int(input())\n# Gib das Doppelte aus\n",
                "solution", "zahl = int(input())\nprint(zahl * 2)\n",
                "tests", List.of(
                        Map.of("stdin", "21", "expectedOutput", "42", "hidden", false),
                        Map.of("stdin", "5", "expectedOutput", "10")));
    }
}

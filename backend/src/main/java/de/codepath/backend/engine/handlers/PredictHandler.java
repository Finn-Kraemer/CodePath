package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Vorhersage mit dem Code-Visualizer: Das Programm hält an einem Haltepunkt (Zeile {@code line}, beim
 * {@code hit}-ten Erreichen, bevor sie ausgeführt wird). Schülerinnen und Schüler sagen voraus, welche Werte
 * Variablen dort haben. Danach zeigt der Visualizer die Auflösung Schritt für Schritt.
 *
 * <p>Config: { code, stdin?, breakpoint: {line, hit?}, questions: [{variable, answers: [...], caseSensitive?}] }
 * Antwort: { answers: ["...", ...] } – Teilpunkte je richtiger Variable. Der Server führt nichts aus:
 * die erwarteten Werte stehen in der Aufgabe (im Editor per Knopfdruck aus dem Programm ermittelt).
 */
@Component
public class PredictHandler implements TaskHandler {

    static final int MAX_CODE_LENGTH = 5_000;

    @Override
    public TaskType type() {
        return TaskType.PREDICT;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        String code = Cfg.string(config, "code", "");
        Map<String, Object> breakpoint = Cfg.object(config, "breakpoint");
        Integer line = Cfg.integer(breakpoint, "line");
        Integer hit = Cfg.integer(breakpoint, "hit");
        int lines = code.split("\n", -1).length;
        List<Map<String, Object>> questions = Cfg.objects(config, "questions");
        Cfg.Problems problems = new Cfg.Problems()
                .requireText(config, "code")
                .require(code.length() <= MAX_CODE_LENGTH, "Das Programm ist zu lang (höchstens " + MAX_CODE_LENGTH + " Zeichen)")
                .require(line != null && line >= 1 && line <= lines, "Der Haltepunkt braucht eine Zeile zwischen 1 und " + lines)
                .require(hit == null || hit >= 1, "Der Haltepunkt muss mindestens einmal erreicht werden (hit ≥ 1)")
                .require(!questions.isEmpty(), "Mindestens eine Variable, deren Wert vorhergesagt wird");
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = questions.get(i);
            String variable = Cfg.string(q, "variable", "");
            problems.require(variable.matches("[A-Za-z_][A-Za-z0-9_]*"), "Frage " + (i + 1) + ": gültiger Variablenname nötig");
            problems.require(!Cfg.strings(q, "answers").isEmpty(), "Frage " + (i + 1) + " braucht mindestens eine akzeptierte Antwort");
        }
        return problems.list();
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        Map<String, Object> config = spec.config();
        Map<String, Object> breakpoint = Cfg.object(config, "breakpoint");
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("code", Cfg.string(config, "code", ""));
        view.put("stdin", Cfg.string(config, "stdin", ""));
        view.put("breakpoint", Map.of(
                "line", Optional.ofNullable(Cfg.integer(breakpoint, "line")).orElse(1),
                "hit", Optional.ofNullable(Cfg.integer(breakpoint, "hit")).orElse(1)));
        view.put("questions", Cfg.objects(config, "questions").stream()
                .map(q -> Map.<String, Object>of("variable", Cfg.string(q, "variable", "")))
                .toList());
        return CodeHandler.withVisualizer(view, config);
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        List<Map<String, Object>> questions = Cfg.objects(spec.config(), "questions");
        List<Object> given = Cfg.list(answer, "answers");
        if (questions.isEmpty()) return Evaluation.wrong("Aufgabe ist nicht vollständig eingerichtet.");
        if (given.size() < questions.size() || given.stream().anyMatch(g -> !(g instanceof String s) || s.isBlank())) {
            return Evaluation.invalid("Bitte sag für jede Variable einen Wert voraus.");
        }
        List<Boolean> perQuestion = new ArrayList<>();
        int right = 0;
        for (int i = 0; i < questions.size(); i++) {
            boolean ok = BlankMatcher.matches(questions.get(i), given.get(i));
            perQuestion.add(ok);
            if (ok) right++;
        }
        String feedback = right == questions.size()
                ? "Richtig vorhergesagt!"
                : right + " von " + questions.size() + " Werten richtig. Schau dir nach dem Lösen an, wie das Programm läuft.";
        return Evaluation.scored((double) right / questions.size(), feedback, Map.of("blanks", perQuestion));
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        return Map.of("answers", Cfg.objects(spec.config(), "questions").stream().map(BlankMatcher::firstAnswer).toList());
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return solutionView(spec, context);
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("answers", Cfg.objects(spec.config(), "questions").stream()
                .map(q -> "falsch-" + BlankMatcher.lowerFirst(q) + "-x").toList());
    }

    @Override
    public Map<String, Object> template() {
        return Map.of(
                "code", "summe = 0\nfor i in range(1, 5):\n    summe = summe + i\nprint(summe)",
                "breakpoint", Map.of("line", 3, "hit", 3),
                "questions", List.of(Map.of("variable", "summe", "answers", List.of("3"))));
    }
}

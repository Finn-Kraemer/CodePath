package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Logikschaltung aus Gattern bauen. Config: { inputs: ["A","B"], expression: "A XOR B", allowedGates: [...],
 * maxGates?, showExpression? (Standard true), solution: {gates, output} }.
 * Die Wahrheitstabelle ist die Aufgabenstellung und geht an den Browser; die Musterschaltung nicht.
 * Antwort: { gates: [{id, type, inputs: [..]}], output } – bewertet wird die Wahrheitstabelle der Schaltung.
 */
@Component
public class LogicCircuitHandler implements TaskHandler {

    @Override
    public TaskType type() {
        return TaskType.LOGIC_CIRCUIT;
    }

    private static List<String> inputs(Map<String, Object> config) {
        return Cfg.strings(config, "inputs");
    }

    private static List<String> allowed(Map<String, Object> config) {
        List<String> allowed = Cfg.strings(config, "allowedGates");
        return allowed.isEmpty() ? LogicCircuit.GATES : allowed;
    }

    static List<Boolean> expected(Map<String, Object> config) {
        return LogicCircuit.truthTable(LogicCircuit.parse(Cfg.string(config, "expression"), inputs(config)), inputs(config));
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        List<String> inputs = inputs(config);
        Cfg.Problems problems = new Cfg.Problems()
                .require(!inputs.isEmpty() && inputs.size() <= LogicCircuit.MAX_INPUTS, "1 bis " + LogicCircuit.MAX_INPUTS + " Eingänge")
                .require(inputs.stream().allMatch(i -> i.matches("[A-Z]")), "Eingänge heißen A, B, C … (ein Großbuchstabe)")
                .require(new HashSet<>(inputs).size() == inputs.size(), "Eingänge doppelt")
                .require(LogicCircuit.GATES.containsAll(allowed(config)), "Erlaubte Gatter: " + String.join(", ", LogicCircuit.GATES));
        List<String> result = new ArrayList<>(problems.list());
        if (!result.isEmpty()) return result;
        List<Boolean> expected;
        try {
            expected = expected(config);
        } catch (IllegalArgumentException e) {
            return List.of("Ausdruck: " + e.getMessage());
        }
        Integer maxGates = Cfg.integer(config, "maxGates");
        if (maxGates != null && (maxGates < 1 || maxGates > LogicCircuit.MAX_GATES)) result.add("'maxGates' zwischen 1 und " + LogicCircuit.MAX_GATES);
        LogicCircuit.Circuit solution = LogicCircuit.read(Cfg.object(config, "solution"));
        List<String> circuitProblems = LogicCircuit.check(solution, inputs, allowed(config), maxGates);
        if (!circuitProblems.isEmpty()) {
            result.add("Musterschaltung: " + String.join("; ", circuitProblems));
        } else if (!LogicCircuit.truthTable(solution, inputs).equals(expected)) {
            result.add("Die Musterschaltung erfüllt die Wahrheitstabelle nicht");
        }
        return result;
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("inputs", inputs(spec.config()));
        view.put("expected", expected(spec.config()));
        view.put("allowedGates", allowed(spec.config()));
        Integer maxGates = Cfg.integer(spec.config(), "maxGates");
        view.put("maxGates", maxGates != null ? maxGates : LogicCircuit.MAX_GATES);
        if (!Boolean.FALSE.equals(spec.config().get("showExpression"))) view.put("expression", Cfg.string(spec.config(), "expression", ""));
        return view;
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        List<String> inputs = inputs(spec.config());
        LogicCircuit.Circuit circuit = LogicCircuit.read(answer);
        List<String> problems = LogicCircuit.check(circuit, inputs, allowed(spec.config()), Cfg.integer(spec.config(), "maxGates"));
        if (!problems.isEmpty()) return Evaluation.invalid(problems.getFirst() + ".");

        List<Boolean> expected = expected(spec.config());
        List<Boolean> actual = LogicCircuit.truthTable(circuit, inputs);
        List<Boolean> rows = new ArrayList<>();
        int right = 0;
        for (int i = 0; i < expected.size(); i++) {
            boolean ok = expected.get(i).equals(actual.get(i));
            rows.add(ok);
            if (ok) right++;
        }
        // Eine konstante Ausgabe trifft oft die Hälfte der Zeilen – Punkte gibt es erst darüber
        double score = Math.max(0, 2.0 * right / expected.size() - 1);
        String feedback = right == expected.size() ? "Die Schaltung erfüllt die Wahrheitstabelle!"
                : right + " von " + expected.size() + " Zeilen der Wahrheitstabelle stimmen.";
        return Evaluation.scored(score, feedback, Map.of("rows", rows));
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        return Map.of("circuit", LogicCircuit.toMap(LogicCircuit.read(Cfg.object(spec.config(), "solution"))),
                "expression", Cfg.string(spec.config(), "expression", ""));
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return LogicCircuit.toMap(LogicCircuit.read(Cfg.object(spec.config(), "solution")));
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        // Ein Eingang direkt am Ausgang – passt nie zu einer sinnvollen Aufgabe; sonst dessen Negation
        List<String> inputs = inputs(spec.config());
        List<Boolean> expected = expected(spec.config());
        for (String input : inputs) {
            LogicCircuit.Circuit direct = new LogicCircuit.Circuit(List.of(), input);
            if (!LogicCircuit.truthTable(direct, inputs).equals(expected)) return LogicCircuit.toMap(direct);
        }
        return Map.of("gates", List.of(Map.of("id", "g1", "type", "NOT", "inputs", List.of(inputs.getFirst()))), "output", "g1");
    }

    @Override
    public Map<String, Object> template() {
        return Map.of(
                "inputs", List.of("A", "B"),
                "expression", "A AND B",
                "allowedGates", List.of("AND", "OR", "NOT"),
                "maxGates", 3,
                "showExpression", true,
                "solution", Map.of("gates", List.of(Map.of("id", "g1", "type", "AND", "inputs", List.of("A", "B"))), "output", "g1"));
    }
}

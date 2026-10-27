package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Config: { items: [{id, text}], correctOrder: [id, ...] }
 * Antwort: { order: [id, ...] } – Teilpunkte nach der längsten richtig geordneten Teilfolge.
 */
@Component
public class SortingHandler implements TaskHandler {

    @Override
    public TaskType type() {
        return TaskType.SORTING;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        List<Map<String, Object>> items = Cfg.objects(config, "items");
        Set<String> ids = new HashSet<>();
        items.forEach(i -> ids.add(Cfg.string(i, "id")));
        List<String> order = Cfg.strings(config, "correctOrder");
        return new Cfg.Problems()
                .require(items.size() >= 2, "Mindestens zwei Elemente")
                .requireUniqueIds(items, "items")
                .require(order.size() == items.size() && ids.equals(new HashSet<>(order)),
                        "'correctOrder' muss jedes Element genau einmal enthalten")
                .list();
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        return Map.of("items", shuffled(Cfg.objects(spec.config(), "items"), Cfg.strings(spec.config(), "correctOrder"), context.random("items")));
    }

    /** Mischt, liefert aber nie zufällig die richtige Reihenfolge aus. */
    static List<Map<String, Object>> shuffled(List<Map<String, Object>> items, List<String> solution, Random random) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : items) {
            result.add(Map.of("id", Cfg.string(item, "id"), "text", Cfg.string(item, "text", "")));
        }
        if (result.size() < 2) return result;
        for (int attempt = 0; attempt < 20; attempt++) {
            Collections.shuffle(result, random);
            if (!result.stream().map(i -> (String) i.get("id")).toList().equals(solution)) break;
        }
        return result;
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        List<String> solution = Cfg.strings(spec.config(), "correctOrder");
        List<String> given = Cfg.strings(answer, "order");
        if (given.size() != solution.size() || !new HashSet<>(given).equals(new HashSet<>(solution))) {
            return Evaluation.invalid("Bitte ordne alle Elemente an.");
        }
        int lcs = longestCommonSubsequence(given, solution);
        // Eine Teilfolge der Länge 1 ist immer "richtig" – sie zählt nicht
        double score = solution.size() <= 1 ? 1.0 : (double) (lcs - 1) / (solution.size() - 1);
        String feedback = score >= 1.0 ? "Richtige Reihenfolge!" : lcs + " von " + solution.size() + " Elementen stehen schon richtig zueinander.";
        return Evaluation.scored(score, feedback, Map.of());
    }

    static int longestCommonSubsequence(List<String> a, List<String> b) {
        int[][] table = new int[a.size() + 1][b.size() + 1];
        for (int i = 1; i <= a.size(); i++) {
            for (int j = 1; j <= b.size(); j++) {
                table[i][j] = a.get(i - 1).equals(b.get(j - 1))
                        ? table[i - 1][j - 1] + 1
                        : Math.max(table[i - 1][j], table[i][j - 1]);
            }
        }
        return table[a.size()][b.size()];
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        return Map.of("order", Cfg.strings(spec.config(), "correctOrder"));
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("order", Cfg.strings(spec.config(), "correctOrder"));
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        List<String> reversed = new ArrayList<>(Cfg.strings(spec.config(), "correctOrder"));
        Collections.reverse(reversed);
        return Map.of("order", reversed);
    }

    @Override
    public Map<String, Object> template() {
        return Map.of(
                "items", List.of(Map.of("id", "s1", "text", "Erster Schritt"), Map.of("id", "s2", "text", "Zweiter Schritt"),
                        Map.of("id", "s3", "text", "Dritter Schritt")),
                "correctOrder", List.of("s1", "s2", "s3"));
    }
}

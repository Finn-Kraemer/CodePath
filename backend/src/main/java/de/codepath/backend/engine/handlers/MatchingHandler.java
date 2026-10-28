package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Config: { left: [{id, text}], right: [{id, text}], pairs: [{left, right}] }
 * Rechts darf es zusätzliche Ablenker geben. Antwort: { pairs: {leftId: rightId} } – Teilpunkte je richtigem Paar.
 */
@Component
public class MatchingHandler implements TaskHandler {

    @Override
    public TaskType type() {
        return TaskType.MATCHING;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        List<Map<String, Object>> left = Cfg.objects(config, "left");
        List<Map<String, Object>> right = Cfg.objects(config, "right");
        Set<String> leftIds = ids(left);
        Set<String> rightIds = ids(right);
        Map<String, String> pairs = solution(config);
        return new Cfg.Problems()
                .require(left.size() >= 2, "Mindestens zwei Begriffe links")
                .requireUniqueIds(left, "left")
                .requireUniqueIds(right, "right")
                .require(left.stream().allMatch(i -> !Cfg.string(i, "text", "").isBlank())
                        && right.stream().allMatch(i -> !Cfg.string(i, "text", "").isBlank()), "Jedes Element braucht einen Text")
                .require(pairs.keySet().equals(leftIds), "Jeder Begriff links braucht genau ein Paar in 'pairs'")
                .require(rightIds.containsAll(pairs.values()), "'pairs' verweist auf unbekannte Elemente rechts")
                .require(new HashSet<>(pairs.values()).size() == pairs.size(), "Jedes Element rechts passt zu höchstens einem Begriff")
                .list();
    }

    private static Set<String> ids(List<Map<String, Object>> items) {
        Set<String> ids = new HashSet<>();
        items.forEach(i -> ids.add(Cfg.string(i, "id")));
        return ids;
    }

    static Map<String, String> solution(Map<String, Object> config) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> pair : Cfg.objects(config, "pairs")) {
            String l = Cfg.string(pair, "left");
            String r = Cfg.string(pair, "right");
            if (l != null && r != null) result.put(l, r);
        }
        return result;
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        List<Map<String, Object>> right = new ArrayList<>(texts(Cfg.objects(spec.config(), "right")));
        Collections.shuffle(right, context.random("right"));
        return Map.of("left", texts(Cfg.objects(spec.config(), "left")), "right", right);
    }

    private static List<Map<String, Object>> texts(List<Map<String, Object>> items) {
        return items.stream().map(i -> Map.<String, Object>of("id", Cfg.string(i, "id", ""), "text", Cfg.string(i, "text", ""))).toList();
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        Map<String, String> solution = solution(spec.config());
        Map<String, Object> given = Cfg.object(answer, "pairs");
        if (!given.keySet().containsAll(solution.keySet()) || given.size() != solution.size()) {
            return Evaluation.invalid("Bitte ordne jedem Begriff etwas zu.");
        }
        Map<String, Object> perItem = new LinkedHashMap<>();
        int right = 0;
        for (var entry : solution.entrySet()) {
            boolean ok = entry.getValue().equals(given.get(entry.getKey()));
            if (ok) right++;
            perItem.put(entry.getKey(), ok);
        }
        String feedback = right == solution.size() ? "Alle Paare stimmen!" : right + " von " + solution.size() + " Paaren stimmen.";
        return Evaluation.scored((double) right / solution.size(), feedback, Map.of("pairs", perItem));
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        return Map.of("pairs", solution(spec.config()));
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("pairs", solution(spec.config()));
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        // Jeder Begriff bekommt das Gegenstück des nächsten
        List<String> keys = new ArrayList<>(solution(spec.config()).keySet());
        List<String> values = new ArrayList<>(solution(spec.config()).values());
        Map<String, Object> shifted = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) shifted.put(keys.get(i), values.get((i + 1) % values.size()));
        return Map.of("pairs", shifted);
    }

    @Override
    public Map<String, Object> template() {
        return Map.of(
                "left", List.of(Map.of("id", "l1", "text", "HTTP"), Map.of("id", "l2", "text", "HTTPS"), Map.of("id", "l3", "text", "SSH")),
                "right", List.of(Map.of("id", "r1", "text", "Port 80"), Map.of("id", "r2", "text", "Port 443"),
                        Map.of("id", "r3", "text", "Port 22"), Map.of("id", "r4", "text", "Port 25")),
                "pairs", List.of(Map.of("left", "l1", "right", "r1"), Map.of("left", "l2", "right", "r2"), Map.of("left", "l3", "right", "r3")));
    }
}

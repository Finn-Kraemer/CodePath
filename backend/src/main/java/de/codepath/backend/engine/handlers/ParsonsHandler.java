package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Code-Puzzle. Config: { lines: [{id, code, indent?}], distractors?: [{id, code}], checkIndent? (Standard true) }
 * Die Reihenfolge von {@code lines} ist die Lösung. Antwort: { lines: [{id, indent}] } – nur die verwendeten Zeilen.
 * Verglichen wird der Code-Text, damit gleich lautende Zeilen austauschbar sind.
 */
@Component
public class ParsonsHandler implements TaskHandler {

    static final int MAX_INDENT = 6;

    @Override
    public TaskType type() {
        return TaskType.PARSONS;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        List<Map<String, Object>> lines = Cfg.objects(config, "lines");
        List<Map<String, Object>> distractors = Cfg.objects(config, "distractors");
        List<Map<String, Object>> all = new ArrayList<>(lines);
        all.addAll(distractors);
        Set<String> solutionCode = new HashSet<>();
        lines.forEach(l -> solutionCode.add(Cfg.string(l, "code", "").strip()));
        return new Cfg.Problems()
                .require(lines.size() >= 2, "Mindestens zwei Zeilen")
                .requireUniqueIds(all, "lines/distractors")
                .require(all.stream().allMatch(l -> !Cfg.string(l, "code", "").isBlank()), "Jede Zeile braucht Code")
                .require(lines.stream().allMatch(l -> indent(l) >= 0 && indent(l) <= MAX_INDENT), "Einrückung 0 bis " + MAX_INDENT)
                .require(distractors.stream().noneMatch(d -> solutionCode.contains(Cfg.string(d, "code", "").strip())),
                        "Ein Ablenker darf nicht wie eine Lösungszeile lauten")
                .list();
    }

    private static int indent(Map<String, Object> line) {
        Integer value = Cfg.integer(line, "indent");
        return value == null ? 0 : value;
    }

    private static boolean checkIndent(Map<String, Object> config) {
        return !Boolean.FALSE.equals(config.get("checkIndent"));
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (Map<String, Object> line : Cfg.objects(spec.config(), "lines")) blocks.add(block(line));
        for (Map<String, Object> line : Cfg.objects(spec.config(), "distractors")) blocks.add(block(line));
        List<String> solution = Cfg.objects(spec.config(), "lines").stream().map(l -> Cfg.string(l, "id")).toList();
        return Map.of(
                "blocks", SortingHandler.shuffled(blocks, solution, context.random("blocks")).stream()
                        .map(b -> Map.<String, Object>of("id", b.get("id"), "code", b.get("text"))).toList(),
                "checkIndent", checkIndent(spec.config()),
                "hasDistractors", !Cfg.objects(spec.config(), "distractors").isEmpty());
    }

    private static Map<String, Object> block(Map<String, Object> line) {
        // SortingHandler.shuffled erwartet id/text
        return Map.of("id", Cfg.string(line, "id", ""), "text", Cfg.string(line, "code", "").strip());
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        Map<String, Map<String, Object>> byId = new HashMap<>();
        Set<String> distractorIds = new HashSet<>();
        for (Map<String, Object> line : Cfg.objects(spec.config(), "lines")) byId.put(Cfg.string(line, "id"), line);
        for (Map<String, Object> line : Cfg.objects(spec.config(), "distractors")) {
            byId.put(Cfg.string(line, "id"), line);
            distractorIds.add(Cfg.string(line, "id"));
        }
        List<Map<String, Object>> given = Cfg.objects(answer, "lines");
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> g : given) {
            String id = Cfg.string(g, "id");
            if (id == null || !byId.containsKey(id) || !seen.add(id)) return Evaluation.invalid("Die Antwort enthält unbekannte oder doppelte Zeilen.");
        }
        if (given.isEmpty()) return Evaluation.invalid("Bitte ziehe Zeilen in dein Programm.");

        List<String> expected = Cfg.objects(spec.config(), "lines").stream().map(l -> key(l, indent(l), spec.config())).toList();
        List<String> actual = given.stream().map(g -> key(byId.get(Cfg.string(g, "id")), clampIndent(Cfg.integer(g, "indent")), spec.config())).toList();
        if (actual.equals(expected)) return Evaluation.correct("Das Programm ist richtig zusammengesetzt!");

        long distractorsUsed = given.stream().filter(g -> distractorIds.contains(Cfg.string(g, "id"))).count();
        List<String> expectedCode = Cfg.objects(spec.config(), "lines").stream().map(l -> Cfg.string(l, "code", "").strip()).toList();
        List<String> actualCode = given.stream().map(g -> Cfg.string(byId.get(Cfg.string(g, "id")), "code", "").strip()).toList();
        int n = expected.size();
        double score;
        String feedback;
        if (actualCode.equals(expectedCode)) {
            score = 0.75;
            feedback = "Die Reihenfolge stimmt – prüfe die Einrückung.";
        } else {
            int lcs = SortingHandler.longestCommonSubsequence(actual, expected);
            score = Math.max(0, (double) (lcs - 1 - distractorsUsed) / (n - 1)) * 0.75;
            feedback = distractorsUsed > 0
                    ? "Noch nicht richtig. Nicht jede Zeile gehört ins Programm."
                    : "Noch nicht richtig. " + (given.size() < n ? "Es fehlen Zeilen." : "Prüfe die Reihenfolge.");
        }
        return Evaluation.scored(score, feedback, Map.of());
    }

    private static int clampIndent(Integer value) {
        return value == null ? 0 : Math.clamp(value, 0, MAX_INDENT);
    }

    private static String key(Map<String, Object> line, int indent, Map<String, Object> config) {
        String code = Cfg.string(line, "code", "").strip();
        return checkIndent(config) ? indent + ":" + code : code;
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        return Map.of("lines", answerLines(spec));
    }

    private static List<Map<String, Object>> answerLines(TaskSpec spec) {
        return Cfg.objects(spec.config(), "lines").stream()
                .map(l -> Map.<String, Object>of("id", Cfg.string(l, "id", ""), "indent", indent(l))).toList();
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("lines", answerLines(spec));
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        List<Map<String, Object>> reversed = new ArrayList<>(answerLines(spec));
        Collections.reverse(reversed);
        return Map.of("lines", reversed);
    }

    @Override
    public Map<String, Object> template() {
        return Map.of(
                "lines", List.of(
                        Map.of("id", "z1", "code", "for i in range(3):", "indent", 0),
                        Map.of("id", "z2", "code", "print(i)", "indent", 1),
                        Map.of("id", "z3", "code", "print('fertig')", "indent", 0)),
                "distractors", List.of(Map.of("id", "d1", "code", "for i in range 3:")),
                "checkIndent", true);
    }
}

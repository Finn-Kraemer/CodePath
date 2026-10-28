package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Zahlensysteme umrechnen. Config: { from: 10, to: 2, min: 0, max: 255, count: 3 } oder feste { values: [..] }
 * (in der Basis {@code from}). Ohne feste Werte bekommt jede Schülerin, jeder Schüler eigene Zahlen
 * (Seed je Person und Aufgabe). Antwort: { answers: ["1010", ...] } – Teilpunkte je Zahl.
 */
@Component
public class NumberConvertHandler implements TaskHandler {

    static final Set<Integer> BASES = Set.of(2, 8, 10, 16);
    static final int MAX_VALUE = 65_535;

    @Override
    public TaskType type() {
        return TaskType.NUMBER_CONVERT;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        Integer from = Cfg.integer(config, "from");
        Integer to = Cfg.integer(config, "to");
        Cfg.Problems problems = new Cfg.Problems()
                .require(from != null && BASES.contains(from) && to != null && BASES.contains(to), "'from' und 'to' müssen 2, 8, 10 oder 16 sein")
                .require(!Objects.equals(from, to), "'from' und 'to' müssen verschieden sein");
        if (from == null || !BASES.contains(from)) return problems.list();
        List<String> fixed = Cfg.strings(config, "values");
        if (!fixed.isEmpty()) {
            problems.require(fixed.size() <= 10, "Höchstens 10 Werte")
                    .require(fixed.stream().allMatch(v -> parse(v, from) != null), "Jeder Wert muss eine Zahl zwischen 0 und " + MAX_VALUE + " in Basis " + from + " sein");
        } else {
            int min = Optional.ofNullable(Cfg.integer(config, "min")).orElse(0);
            int max = Optional.ofNullable(Cfg.integer(config, "max")).orElse(255);
            int count = Optional.ofNullable(Cfg.integer(config, "count")).orElse(3);
            problems.require(min >= 0 && max <= MAX_VALUE && min < max, "Bereich: 0 ≤ min < max ≤ " + MAX_VALUE)
                    .require(count >= 1 && count <= 10, "'count' zwischen 1 und 10")
                    .require(max - min + 1 >= count, "Der Bereich ist zu klein für so viele verschiedene Zahlen");
        }
        return problems.list();
    }

    /** Die Zahlen dieser Person als Werte (nicht als Text). */
    static List<Integer> values(Map<String, Object> config, TaskContext context) {
        int from = Optional.ofNullable(Cfg.integer(config, "from")).orElse(10);
        List<String> fixed = Cfg.strings(config, "values");
        if (!fixed.isEmpty()) return fixed.stream().map(v -> parse(v, from)).filter(Objects::nonNull).toList();
        int min = Optional.ofNullable(Cfg.integer(config, "min")).orElse(0);
        int max = Optional.ofNullable(Cfg.integer(config, "max")).orElse(255);
        int count = Math.min(Optional.ofNullable(Cfg.integer(config, "count")).orElse(3), Math.max(1, max - min + 1));
        Random random = context.random("numbers");
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        while (values.size() < count) values.add(min + random.nextInt(max - min + 1));
        return List.copyOf(values);
    }

    /** Liest eine Zahl großzügig: Leerzeichen, Unterstriche, Präfixe 0b/0o/0x und Groß-/Kleinschreibung egal. */
    static Integer parse(String text, int base) {
        if (text == null) return null;
        String t = text.strip().replace(" ", "").replace("_", "").toLowerCase(Locale.ROOT);
        if (base == 2 && t.startsWith("0b") || base == 8 && t.startsWith("0o") || base == 16 && t.startsWith("0x")) t = t.substring(2);
        if (t.isEmpty() || t.length() > 20) return null;
        try {
            int value = Integer.parseInt(t, base);
            return value >= 0 && value <= MAX_VALUE ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String format(int value, int base) {
        return Integer.toString(value, base).toUpperCase(Locale.ROOT);
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        int from = Optional.ofNullable(Cfg.integer(spec.config(), "from")).orElse(10);
        int to = Optional.ofNullable(Cfg.integer(spec.config(), "to")).orElse(2);
        return Map.of("from", from, "to", to,
                "values", values(spec.config(), context).stream().map(v -> format(v, from)).toList());
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        int to = Optional.ofNullable(Cfg.integer(spec.config(), "to")).orElse(2);
        List<Integer> expected = values(spec.config(), context);
        List<String> given = Cfg.strings(answer, "answers");
        if (given.size() != expected.size() || given.stream().anyMatch(String::isBlank)) {
            return Evaluation.invalid("Bitte rechne alle Zahlen um.");
        }
        List<Boolean> perValue = new ArrayList<>();
        int right = 0;
        boolean unreadable = false;
        for (int i = 0; i < expected.size(); i++) {
            Integer value = parse(given.get(i), to);
            if (value == null) unreadable = true;
            boolean ok = expected.get(i).equals(value);
            if (ok) right++;
            perValue.add(ok);
        }
        String feedback = right == expected.size() ? "Alles richtig umgerechnet!"
                : right + " von " + expected.size() + " richtig." + (unreadable ? " Manche Eingaben sind keine Zahl in Basis " + to + "." : "");
        return Evaluation.scored((double) right / expected.size(), feedback, Map.of("values", perValue));
    }

    private static List<String> answers(TaskSpec spec, TaskContext context) {
        int to = Optional.ofNullable(Cfg.integer(spec.config(), "to")).orElse(2);
        return values(spec.config(), context).stream().map(v -> format(v, to)).toList();
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        return Map.of("answers", answers(spec, context));
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("answers", answers(spec, context));
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        int to = Optional.ofNullable(Cfg.integer(spec.config(), "to")).orElse(2);
        return Map.of("answers", values(spec.config(), context).stream().map(v -> format((v + 1) % (MAX_VALUE + 1), to)).toList());
    }

    @Override
    public Map<String, Object> template() {
        return Map.of("from", 10, "to", 2, "min", 1, "max", 255, "count", 3);
    }
}

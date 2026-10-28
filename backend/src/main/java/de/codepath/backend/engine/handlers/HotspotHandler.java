package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Stellen markieren, z. B. Warnzeichen in einer Phishing-Mail oder den Fehler im Code.
 * Config: { text: "mehrzeilig", targets: [Zeilennummern ab 1], mono?: bool, showCount?: bool (Standard true) }
 * Antwort: { selected: [Zeilennummern] }. Punkte: Treffer minus Fehlmarkierungen, geteilt durch die Zahl der Ziele.
 */
@Component
public class HotspotHandler implements TaskHandler {

    static final int MAX_LINES = 60;

    @Override
    public TaskType type() {
        return TaskType.HOTSPOT;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        List<String> lines = lines(config);
        List<Integer> targets = targets(config);
        return new Cfg.Problems()
                .requireText(config, "text")
                .require(lines.size() <= MAX_LINES, "Höchstens " + MAX_LINES + " Zeilen")
                .require(!targets.isEmpty(), "Mindestens eine Zeile in 'targets'")
                .require(targets.size() == Cfg.list(config, "targets").size(), "'targets' enthält nur Zeilennummern")
                .require(new HashSet<>(targets).size() == targets.size(), "Zeilennummern in 'targets' doppelt")
                .require(targets.stream().allMatch(t -> t >= 1 && t <= lines.size()), "'targets' verweist auf Zeilen, die es nicht gibt")
                .require(targets.stream().allMatch(t -> t < 1 || t > lines.size() || !lines.get(t - 1).isBlank()), "Leere Zeilen können kein Ziel sein")
                .require(targets.size() < lines.stream().filter(l -> !l.isBlank()).count(), "Nicht alle Zeilen dürfen Ziele sein")
                .list();
    }

    private static List<String> lines(Map<String, Object> config) {
        return Cfg.string(config, "text", "").replace("\r\n", "\n").lines().toList();
    }

    private static List<Integer> targets(Map<String, Object> config) {
        return Cfg.list(config, "targets").stream().filter(Number.class::isInstance).map(n -> ((Number) n).intValue()).toList();
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("lines", lines(spec.config()));
        view.put("mono", Cfg.bool(spec.config(), "mono"));
        if (!Boolean.FALSE.equals(spec.config().get("showCount"))) view.put("count", targets(spec.config()).size());
        return view;
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        Set<Integer> targets = new HashSet<>(targets(spec.config()));
        Set<Integer> selected = new HashSet<>();
        for (Object item : Cfg.list(answer, "selected")) {
            if (item instanceof Number n) selected.add(n.intValue());
        }
        if (selected.isEmpty()) return Evaluation.invalid("Bitte markiere mindestens eine Zeile.");
        int hits = (int) selected.stream().filter(targets::contains).count();
        int misses = selected.size() - hits;
        double score = (double) (hits - misses) / targets.size();
        String feedback = hits == targets.size() && misses == 0 ? "Alles gefunden!"
                : hits + " von " + targets.size() + " gefunden" + (misses > 0 ? ", " + misses + " Markierung(en) zu viel." : ".");
        return Evaluation.scored(score, feedback, Map.of());
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        return Map.of("selected", targets(spec.config()));
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("selected", targets(spec.config()));
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        Set<Integer> targets = new HashSet<>(targets(spec.config()));
        List<String> lines = lines(spec.config());
        List<Integer> others = new ArrayList<>();
        for (int i = 1; i <= lines.size(); i++) {
            if (!targets.contains(i) && !lines.get(i - 1).isBlank()) others.add(i);
        }
        return Map.of("selected", others.isEmpty() ? List.of(1) : List.of(others.getFirst()));
    }

    @Override
    public Map<String, Object> template() {
        return Map.of(
                "text", "Von: support@paypa1-sicherheit.com\nBetreff: Ihr Konto wird gesperrt!\n\nSehr geehrter Kunde,\nbitte bestätigen Sie innerhalb von 24 Stunden Ihr Passwort:\nhttp://paypa1-login.example\n\nIhr Support-Team",
                "targets", List.of(1, 2, 5, 6),
                "showCount", true);
    }
}

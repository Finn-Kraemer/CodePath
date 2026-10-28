package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Freitextabgabe, bewertet von der Lehrkraft mit einer Rubrik.
 * Config: { instructions, submissionNote?, rubric: [{id, label, points}], codeEditor?, starterCode?, codeRequired? }
 * Antwort: { content: "<html aus dem Editor>", code?: "Python" } – mit codeEditor gibt es eine Python-Werkbank,
 * deren Code mit der Abgabe zur Lehrkraft geht.
 */
@Component
public class PracticeHandler implements TaskHandler {

    static final int MAX_CONTENT_LENGTH = 200_000;
    static final int MAX_CODE_LENGTH = 50_000;

    @Override
    public TaskType type() {
        return TaskType.PRACTICE;
    }

    @Override
    public boolean autoGraded() {
        return false;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        List<Map<String, Object>> rubric = Cfg.objects(config, "rubric");
        int sum = rubric.stream().mapToInt(c -> Cfg.integer(c, "points") != null ? Cfg.integer(c, "points") : 0).sum();
        return new Cfg.Problems()
                .requireText(config, "instructions")
                .require(!rubric.isEmpty(), "Mindestens ein Bewertungskriterium")
                .requireUniqueIds(rubric, "rubric")
                .require(rubric.stream().allMatch(c -> Cfg.string(c, "label") != null && Cfg.integer(c, "points") != null && Cfg.integer(c, "points") > 0),
                        "Jedes Kriterium braucht label und positive points")
                .require(sum == points, "Die Kriterien ergeben " + sum + " Punkte, die Aufgabe hat " + points)
                .list();
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("instructions", Cfg.string(spec.config(), "instructions", ""));
        view.put("submissionNote", Cfg.string(spec.config(), "submissionNote", ""));
        view.put("rubric", rubric(spec));
        // Python-Werkbank: größere Programme im Browser schreiben und ausprobieren, Code geht mit der Abgabe
        view.put("codeEditor", Cfg.bool(spec.config(), "codeEditor"));
        view.put("starterCode", Cfg.string(spec.config(), "starterCode", ""));
        view.put("codeRequired", Cfg.bool(spec.config(), "codeRequired"));
        return view;
    }

    /** Die Rubrik ist keine Lösung: Schüler sollen wissen, worauf es ankommt. */
    public static List<Map<String, Object>> rubric(TaskSpec spec) {
        List<Map<String, Object>> rubric = Cfg.objects(spec.config(), "rubric");
        if (!rubric.isEmpty()) return rubric;
        return List.of(Map.of("id", "gesamt", "label", "Gesamteindruck", "points", spec.points()));
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        String content = Cfg.string(answer, "content", "");
        String code = Cfg.bool(spec.config(), "codeEditor") ? Cfg.string(answer, "code", "") : "";
        boolean hasText = !content.replaceAll("<[^>]*>", "").isBlank();
        boolean hasCode = !code.isBlank();
        if (Cfg.bool(spec.config(), "codeRequired") && !hasCode) return Evaluation.invalid("Bitte gib auch deinen Code ab.");
        if (!hasText && !hasCode) return Evaluation.invalid("Die Abgabe ist leer.");
        if (content.length() > MAX_CONTENT_LENGTH) return Evaluation.invalid("Die Abgabe ist zu lang.");
        if (code.length() > MAX_CODE_LENGTH) return Evaluation.invalid("Der Code ist zu lang.");
        return new Evaluation(Evaluation.Outcome.MANUAL, 0, "Abgabe eingereicht! Die Lehrkraft bewertet sie.", Map.of());
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        return Map.of();
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("content", "<p>Beispielabgabe</p>", "code", "print('Beispiel')");
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("content", "");
    }

    @Override
    public Map<String, Object> template() {
        return Map.of(
                "instructions", "Beschreibe in eigenen Worten …",
                "submissionNote", "",
                "rubric", List.of(Map.of("id", "k1", "label", "Inhalt vollständig", "points", 10)));
    }
}

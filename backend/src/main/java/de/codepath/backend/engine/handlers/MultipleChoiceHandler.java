package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Config: { options: [{id, text, explanation?}], correct: [id, ...], multiple: bool }
 * Antwort: { selected: [id, ...] }
 */
@Component
public class MultipleChoiceHandler implements TaskHandler {

    @Override
    public TaskType type() {
        return TaskType.MULTIPLE_CHOICE;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        List<Map<String, Object>> options = Cfg.objects(config, "options");
        Set<String> ids = new HashSet<>();
        options.forEach(o -> ids.add(Cfg.string(o, "id")));
        List<String> correct = Cfg.strings(config, "correct");
        return new Cfg.Problems()
                .require(options.size() >= 2, "Mindestens zwei Antwortoptionen")
                .requireUniqueIds(options, "options")
                .require(options.stream().allMatch(o -> Cfg.string(o, "text") != null), "Jede Option braucht einen Text")
                .require(!correct.isEmpty(), "Mindestens eine richtige Antwort")
                .require(ids.containsAll(correct), "'correct' verweist auf unbekannte Optionen")
                .require(Cfg.bool(config, "multiple") || correct.size() == 1,
                        "Mehrere richtige Antworten nur mit multiple: true")
                .list();
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (Map<String, Object> option : Cfg.objects(spec.config(), "options")) {
            options.add(Map.of("id", Cfg.string(option, "id"), "text", Cfg.string(option, "text", "")));
        }
        if (!Cfg.bool(spec.config(), "keepOrder")) {
            Collections.shuffle(options, context.random("options"));
        }
        return Map.of("options", options, "multiple", Cfg.bool(spec.config(), "multiple"));
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        Set<String> selected = new HashSet<>(Cfg.strings(answer, "selected"));
        Set<String> correct = new HashSet<>(Cfg.strings(spec.config(), "correct"));
        if (selected.isEmpty()) {
            return Evaluation.invalid("Bitte wähle mindestens eine Antwort aus.");
        }
        if (!Cfg.bool(spec.config(), "multiple") && selected.size() > 1) {
            return Evaluation.invalid("Hier ist genau eine Antwort richtig.");
        }
        return selected.equals(correct)
                ? Evaluation.correct("Richtig!")
                : Evaluation.wrong("Leider falsch.");
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        Map<String, String> explanations = new LinkedHashMap<>();
        for (Map<String, Object> option : Cfg.objects(spec.config(), "options")) {
            String explanation = Cfg.string(option, "explanation");
            if (explanation != null) explanations.put(Cfg.string(option, "id"), explanation);
        }
        return Map.of("correct", Cfg.strings(spec.config(), "correct"), "explanations", explanations);
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("selected", Cfg.strings(spec.config(), "correct"));
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        Set<String> correct = new HashSet<>(Cfg.strings(spec.config(), "correct"));
        return Cfg.objects(spec.config(), "options").stream()
                .map(o -> Cfg.string(o, "id"))
                .filter(id -> !correct.contains(id))
                .findFirst()
                .<Map<String, Object>>map(id -> Map.of("selected", List.of(id)))
                .orElse(Map.of("selected", List.of()));
    }
}

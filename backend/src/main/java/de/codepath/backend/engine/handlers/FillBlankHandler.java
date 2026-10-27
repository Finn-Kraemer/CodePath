package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Config: { template: "Text mit ___ Lücken", blanks: [{answers: [...], caseSensitive?, options?: [...]}] }
 * Antwort: { answers: ["...", ...] } – Teilpunkte je richtiger Lücke.
 */
@Component
public class FillBlankHandler implements TaskHandler {

    static final String GAP = "___";

    @Override
    public TaskType type() {
        return TaskType.FILL_BLANK;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        return validateBlanks(config).list();
    }

    static Cfg.Problems validateBlanks(Map<String, Object> config) {
        String template = Cfg.string(config, "template", "");
        List<Map<String, Object>> blanks = Cfg.objects(config, "blanks");
        int gaps = template.split(GAP, -1).length - 1;
        Cfg.Problems problems = new Cfg.Problems()
                .requireText(config, "template")
                .require(!blanks.isEmpty(), "Mindestens eine Lücke")
                .require(gaps == blanks.size(), "Die Vorlage hat " + gaps + " Lücken (___), aber " + blanks.size() + " Lösungen");
        for (int i = 0; i < blanks.size(); i++) {
            List<String> answers = Cfg.strings(blanks.get(i), "answers");
            problems.require(!answers.isEmpty(), "Lücke " + (i + 1) + " braucht mindestens eine akzeptierte Antwort");
            List<String> options = Cfg.strings(blanks.get(i), "options");
            if (!options.isEmpty()) {
                problems.require(answers.stream().anyMatch(options::contains),
                        "Lücke " + (i + 1) + ": eine akzeptierte Antwort muss unter den Auswahloptionen sein");
            }
        }
        return problems;
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        return Map.of("template", Cfg.string(spec.config(), "template", ""), "blanks", publicBlanks(spec, context));
    }

    static List<Map<String, Object>> publicBlanks(TaskSpec spec, TaskContext context) {
        List<Map<String, Object>> result = new ArrayList<>();
        Random random = context.random("blank-options");
        for (Map<String, Object> blank : Cfg.objects(spec.config(), "blanks")) {
            List<String> options = new ArrayList<>(Cfg.strings(blank, "options"));
            Collections.shuffle(options, random);
            result.add(options.isEmpty() ? Map.of() : Map.of("options", options));
        }
        return result;
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        return evaluateBlanks(spec, answer);
    }

    static Evaluation evaluateBlanks(TaskSpec spec, Map<String, Object> answer) {
        List<Map<String, Object>> blanks = Cfg.objects(spec.config(), "blanks");
        List<Object> given = Cfg.list(answer, "answers");
        if (blanks.isEmpty()) return Evaluation.wrong("Aufgabe ist nicht vollständig eingerichtet.");

        List<Boolean> perBlank = new ArrayList<>();
        int right = 0;
        for (int i = 0; i < blanks.size(); i++) {
            boolean ok = i < given.size() && BlankMatcher.matches(blanks.get(i), given.get(i));
            perBlank.add(ok);
            if (ok) right++;
        }
        double score = (double) right / blanks.size();
        String feedback = right == blanks.size()
                ? "Alle Lücken richtig!"
                : right + " von " + blanks.size() + " Lücken richtig.";
        return Evaluation.scored(score, feedback, Map.of("blanks", perBlank));
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        return Map.of("answers", Cfg.objects(spec.config(), "blanks").stream().map(BlankMatcher::firstAnswer).toList());
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return solutionView(spec, context);
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("answers", Cfg.objects(spec.config(), "blanks").stream().map(b -> "falsch-" + BlankMatcher.lowerFirst(b) + "-x").toList());
    }
}

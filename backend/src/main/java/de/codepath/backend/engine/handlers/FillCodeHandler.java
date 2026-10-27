package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Config: { template: "Code mit ___", blanks: [{answers, caseSensitive?}], tests?: [...] }
 * Antwort: { answers: [...] }
 * Bewertet über die hinterlegten Lückenantworten. Mit Code-Runner zählt außerdem jede andere
 * Ergänzung, die alle Tests besteht.
 */
@Component
public class FillCodeHandler implements TaskHandler {

    private final CodeRunner runner;

    public FillCodeHandler(CodeRunner runner) {
        this.runner = runner;
    }

    @Override
    public TaskType type() {
        return TaskType.FILL_CODE;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        return FillBlankHandler.validateBlanks(config).list();
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        return Map.of("template", Cfg.string(spec.config(), "template", ""), "blanks", FillBlankHandler.publicBlanks(spec, context));
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        Evaluation byBlanks = FillBlankHandler.evaluateBlanks(spec, answer);
        if (byBlanks.isCorrect() || !runner.available() || Cfg.objects(spec.config(), "tests").isEmpty()) {
            return byBlanks;
        }
        // Alternative Lösungen (z. B. "name" statt 'name') zählen, wenn das Programm alle Tests besteht
        String code = assemble(Cfg.string(spec.config(), "template", ""), Cfg.list(answer, "answers"));
        Evaluation byTests = CodeTests.run(runner, code, spec.config());
        return byTests.isCorrect() ? byTests : byBlanks;
    }

    static String assemble(String template, List<Object> answers) {
        String[] parts = template.split(FillBlankHandler.GAP, -1);
        StringBuilder code = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            Object answer = i - 1 < answers.size() ? answers.get(i - 1) : "";
            code.append(answer instanceof String s ? s : "").append(parts[i]);
        }
        return code.toString();
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        List<String> answers = new ArrayList<>();
        for (Map<String, Object> blank : Cfg.objects(spec.config(), "blanks")) {
            answers.add(BlankMatcher.firstAnswer(blank));
        }
        return Map.of("answers", answers);
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return solutionView(spec, context);
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("answers", Cfg.objects(spec.config(), "blanks").stream().map(b -> "xx_falsch").toList());
    }

    @Override
    public Map<String, Object> template() {
        return Map.of(
                "template", "for i in ___(3):\n    print(i)",
                "blanks", List.of(Map.of("answers", List.of("range"), "caseSensitive", true)),
                "tests", List.of(Map.of("expectedOutput", "0\n1\n2\n")));
    }
}

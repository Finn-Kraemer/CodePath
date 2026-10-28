package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import de.codepath.backend.engine.lab.LabCodes;
import de.codepath.backend.engine.lab.LabScenario;
import de.codepath.backend.engine.lab.LabScenarios;
import de.codepath.backend.engine.lab.LabSecret;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Browser-Labor: Eine Übungsseite (eigener Tab) wird mit den Entwicklerwerkzeugen untersucht; unterwegs
 * findet man die Teile eines Codes. Config: { scenario }. Antwort: { code: "XXXX-XXXX-XXXX" }.
 * Der Code ist je Person verschieden; Teilpunkte je richtigem Teil.
 */
@Component
public class DevToolsHandler implements TaskHandler {

    private final LabScenarios scenarios;
    private final LabSecret secret;

    public DevToolsHandler(LabScenarios scenarios, LabSecret secret) {
        this.scenarios = scenarios;
        this.secret = secret;
    }

    @Override
    public TaskType type() {
        return TaskType.DEVTOOLS;
    }

    private Optional<LabScenario> scenario(Map<String, Object> config) {
        return scenarios.find(Cfg.string(config, "scenario"));
    }

    public LabCodes codes(TaskContext context, LabScenario scenario) {
        return new LabCodes(secret.secret(), context, scenario.id());
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        return new Cfg.Problems()
                .require(scenario(config).isPresent(), "Unbekanntes Szenario; verfügbar: "
                        + String.join(", ", scenarios.all().stream().map(LabScenario::id).toList()))
                .list();
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        LabScenario s = scenario(spec.config()).orElseThrow();
        return Map.of("scenario", s.id(), "title", s.title(), "tools", s.tools(), "parts", s.parts(),
                "format", String.join("-", Collections.nCopies(s.parts(), "X".repeat(LabCodes.LENGTH))));
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        LabScenario s = scenario(spec.config()).orElseThrow();
        List<String> given = LabCodes.split(Cfg.string(answer, "code"), s.parts());
        if (given.size() != s.parts() || given.stream().anyMatch(p -> p.length() != LabCodes.LENGTH)) {
            return Evaluation.invalid("Der Code hat " + s.parts() + " Teile mit je " + LabCodes.LENGTH + " Zeichen, z. B. "
                    + String.join("-", Collections.nCopies(s.parts(), "ABCD")) + ".");
        }
        List<String> expected = codes(context, s).parts(s.parts());
        List<Boolean> perPart = new ArrayList<>();
        int right = 0;
        for (int i = 0; i < expected.size(); i++) {
            boolean ok = expected.get(i).equals(given.get(i));
            perPart.add(ok);
            if (ok) right++;
        }
        String feedback = right == expected.size() ? "Alle Teile stimmen – stark!" : right + " von " + expected.size() + " Teilen stimmen.";
        return Evaluation.scored((double) right / expected.size(), feedback, Map.of("parts", perPart));
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        LabScenario s = scenario(spec.config()).orElseThrow();
        return Map.of("code", String.join("-", codes(context, s).parts(s.parts())));
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("code", solutionView(spec, context).get("code"));
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        LabScenario s = scenario(spec.config()).orElseThrow();
        // "1" kommt im Alphabet nicht vor – kann nie stimmen
        return Map.of("code", String.join("-", Collections.nCopies(s.parts(), "1111")));
    }

    @Override
    public Map<String, Object> template() {
        return Map.of("scenario", scenarios.all().isEmpty() ? "" : scenarios.all().getFirst().id());
    }
}

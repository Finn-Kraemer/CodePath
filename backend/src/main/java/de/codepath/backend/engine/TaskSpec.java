package de.codepath.backend.engine;

import java.util.List;
import java.util.Map;

/**
 * Alles, was die Engine über eine (veröffentlichte) Aufgabenversion wissen muss.
 */
public record TaskSpec(TaskType type, int points, Map<String, Object> config, AttemptRules rules, List<String> hints) {

    public TaskSpec {
        config = config != null ? config : Map.of();
        hints = hints != null ? hints : List.of();
    }
}

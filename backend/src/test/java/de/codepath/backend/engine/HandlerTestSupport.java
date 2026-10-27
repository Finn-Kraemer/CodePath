package de.codepath.backend.engine;

import java.util.List;
import java.util.Map;

public final class HandlerTestSupport {

    private HandlerTestSupport() {
    }

    public static final TaskContext CONTEXT = TaskContext.of(7, 42);

    public static TaskSpec spec(TaskType type, int points, Map<String, Object> config) {
        return new TaskSpec(type, points, config, AttemptRules.defaultsFor(type), List.of());
    }
}

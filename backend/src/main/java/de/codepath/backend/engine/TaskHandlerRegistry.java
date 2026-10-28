package de.codepath.backend.engine;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class TaskHandlerRegistry {

    private final Map<TaskType, TaskHandler> handlers = new EnumMap<>(TaskType.class);

    public TaskHandlerRegistry(List<TaskHandler> handlerBeans) {
        for (TaskHandler handler : handlerBeans) {
            if (handlers.put(handler.type(), handler) != null) {
                throw new IllegalStateException("Zwei Handler für " + handler.type());
            }
        }
    }

    public TaskHandler get(TaskType type) {
        TaskHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("Kein Handler für Aufgabentyp " + type);
        }
        return handler;
    }

    public boolean supports(TaskType type) {
        return handlers.containsKey(type);
    }

    /** Validiert eine Konfiguration und wirft bei Problemen. */
    public void requireValid(TaskType type, Map<String, Object> config, int points) {
        List<String> problems = get(type).validate(config, points);
        if (!problems.isEmpty()) {
            throw new InvalidTaskConfigException(problems);
        }
    }
}

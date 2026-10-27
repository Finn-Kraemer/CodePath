package de.codepath.backend.content;

import de.codepath.backend.engine.AttemptRules;
import de.codepath.backend.engine.TaskSpec;
import de.codepath.backend.engine.TaskType;

/** Eine Aufgabe mit ihrer aktuellen veröffentlichten Version – die Sicht anderer Pakete auf Inhalte. */
public record PublishedTask(Task task, Module module, TaskVersion version) {

    public Long taskId() {
        return task.getId();
    }

    public TaskType type() {
        return task.getType();
    }

    public TaskSpec spec() {
        return ContentService.spec(task.getType(), version);
    }

    public AttemptRules rules() {
        return AttemptRules.from(task.getType(), version.getRules());
    }
}

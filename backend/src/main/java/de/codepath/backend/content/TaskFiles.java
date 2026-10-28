package de.codepath.backend.content;

import java.util.List;
import java.util.Map;

/** Umwandlung zwischen gespeicherten Versionen und dem Austauschformat. */
public final class TaskFiles {

    private TaskFiles() {
    }

    public static ContentPackage.TaskFile fromVersion(Task task, TaskVersion v) {
        return new ContentPackage.TaskFile(task.getSlug(), task.getType(), v.getTitle(), v.getDifficulty(), v.getPoints(),
                task.getSortOrder(), nullToEmpty(v.getLearningGoal()), nullToEmpty(v.getStory()), nullToEmpty(v.getAssignment()),
                v.getHints() != null ? v.getHints() : List.of(), nullToEmpty(v.getExplanation()),
                v.getRules() != null ? v.getRules() : Map.of(), v.getConfig() != null ? v.getConfig() : Map.of());
    }

    static ContentPackage.TaskFile normalize(ContentPackage.TaskFile tf) {
        return new ContentPackage.TaskFile(tf.slug(), tf.type(), tf.title(), tf.difficulty(), tf.points(), tf.sortOrder(),
                nullToEmpty(tf.learningGoal()), nullToEmpty(tf.story()), nullToEmpty(tf.assignment()),
                tf.hints() != null ? tf.hints() : List.of(), nullToEmpty(tf.explanation()),
                tf.rules() != null ? tf.rules() : Map.of(), tf.config() != null ? tf.config() : Map.of());
    }

    static ContentPackage.TaskFile withoutOrder(ContentPackage.TaskFile tf) {
        return new ContentPackage.TaskFile(tf.slug(), tf.type(), tf.title(), tf.difficulty(), tf.points(), null,
                tf.learningGoal(), tf.story(), tf.assignment(), tf.hints(), tf.explanation(), tf.rules(), tf.config());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

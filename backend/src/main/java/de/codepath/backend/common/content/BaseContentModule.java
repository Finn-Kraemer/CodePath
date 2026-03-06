package de.codepath.backend.common.content;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.tasks.Difficulty;
import de.codepath.backend.features.tasks.Task;
import de.codepath.backend.features.tasks.TaskType;
import java.util.Map;

public abstract class BaseContentModule implements ContentModule {

    protected Module defineModule(String slug, String title, String description, String emoji, int order) {
        Module m = new Module();
        m.setSlug(slug);
        m.setTitle(title);
        m.setDescription(description);
        m.setIconEmoji(emoji);
        m.setSortOrder(order);
        m.setIsUnlocked(false);
        return m;
    }

    protected Task createTask(Module m, String slug, String title, String desc, TaskType type, Difficulty diff, int pts, int order, Map<String, Object> config) {
        Task t = new Task();
        t.setModule(m);
        t.setSlug(slug);
        t.setTitle(title);
        t.setDescription(desc);
        t.setType(type);
        t.setDifficulty(diff);
        t.setPoints(pts);
        t.setSortOrder(order);
        t.setConfig(config);
        return t;
    }
}

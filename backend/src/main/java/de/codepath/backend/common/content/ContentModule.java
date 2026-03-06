package de.codepath.backend.common.content;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.tasks.Task;
import java.util.List;

public interface ContentModule {
    Module getModuleDefinition();
    List<Task> getTasks(Module module);
}

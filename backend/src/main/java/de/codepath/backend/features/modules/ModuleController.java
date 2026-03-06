package de.codepath.backend.features.modules;

import de.codepath.backend.features.tasks.TaskResponse;
import de.codepath.backend.features.tasks.TaskService;
import de.codepath.backend.features.tasks.TaskSubmissionService;
import de.codepath.backend.features.tasks.SubmitRequest;
import de.codepath.backend.features.tasks.SubmitResponse;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;
    private final TaskService taskService;
    private final UserService userService;
    private final TaskSubmissionService taskSubmissionService;

    @GetMapping
    public List<ModuleResponse> getModules() {
        User user = userService.getCurrentUser();
        return moduleService.getAllModulesForUser(user);
    }

    @GetMapping("/{slug}")
    public List<TaskResponse> getModuleTasks(@PathVariable("slug") String slug) {
        User user = userService.getCurrentUser();
        Module module = moduleService.getModuleBySlug(slug);
        return taskService.getTasksForModule(module, user);
    }

    @GetMapping("/{moduleSlug}/{taskSlug}")
    public TaskResponse getTask(@PathVariable("moduleSlug") String moduleSlug, @PathVariable("taskSlug") String taskSlug) {
        User user = userService.getCurrentUser();
        return taskService.getTask(moduleSlug, taskSlug, user);
    }

    @PostMapping("/{moduleSlug}/{taskSlug}/submit")
    public ResponseEntity<SubmitResponse> submitTask(
            @PathVariable("moduleSlug") String moduleSlug,
            @PathVariable("taskSlug") String taskSlug,
            @RequestBody SubmitRequest request) {
        User user = userService.getCurrentUser();
        return ResponseEntity.ok(taskSubmissionService.submitTask(moduleSlug, taskSlug, user, request));
    }
}

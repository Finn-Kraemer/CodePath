package de.codepath.backend.progress;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor
public class StudentController {

    private final StudentTaskService taskService;
    private final SubmissionService submissionService;
    private final UserService userService;

    @GetMapping
    public List<TaskViews.ModuleSummary> modules() {
        return taskService.modules(userService.currentUser());
    }

    @GetMapping("/{slug}")
    public TaskViews.ModuleDetail module(@PathVariable("slug") String slug) {
        return taskService.module(userService.currentUser(), slug);
    }

    @GetMapping("/{moduleSlug}/{taskSlug}")
    public TaskViews.TaskDetail task(@PathVariable("moduleSlug") String moduleSlug, @PathVariable("taskSlug") String taskSlug) {
        return taskService.task(userService.currentUser(), moduleSlug, taskSlug);
    }

    /** Body: die Antwort im Format des Aufgabentyps, z. B. {"selected": ["b"]}. */
    @PostMapping("/{moduleSlug}/{taskSlug}/submit")
    public SubmitResult submit(@PathVariable("moduleSlug") String moduleSlug, @PathVariable("taskSlug") String taskSlug,
                               @RequestBody Map<String, Object> answer) {
        return submissionService.submit(userService.currentUser(), moduleSlug, taskSlug, answer);
    }

    @PostMapping("/{moduleSlug}/{taskSlug}/hint")
    public SubmissionService.HintResult hint(@PathVariable("moduleSlug") String moduleSlug, @PathVariable("taskSlug") String taskSlug) {
        return submissionService.requestHint(userService.currentUser(), moduleSlug, taskSlug);
    }

    @PostMapping("/{moduleSlug}/{taskSlug}/reveal")
    public Map<String, Object> reveal(@PathVariable("moduleSlug") String moduleSlug, @PathVariable("taskSlug") String taskSlug) {
        return submissionService.revealSolution(userService.currentUser(), moduleSlug, taskSlug);
    }
}

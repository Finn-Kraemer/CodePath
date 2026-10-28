package de.codepath.backend.progress;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class StudentRecordController {

    private final StudentRecordService recordService;
    private final UserService userService;

    @GetMapping("/students")
    public List<StudentRecordService.StudentOverview> students(@RequestParam(name = "courseId", required = false) Long courseId) {
        return recordService.students(courseId);
    }

    @GetMapping("/students/{username}/tasks")
    public List<StudentRecordService.StudentTask> tasks(@PathVariable("username") String username) {
        return recordService.tasks(username);
    }

    @GetMapping("/students/{username}/tasks/{taskId}/attempts")
    public List<ProgressService.AttemptView> attempts(@PathVariable("username") String username, @PathVariable("taskId") Long taskId) {
        return recordService.attempts(username, taskId);
    }

    @PutMapping("/students/{username}/tasks/{taskId}/toggle")
    public void toggle(@PathVariable("username") String username, @PathVariable("taskId") Long taskId,
                       @RequestBody(required = false) Map<String, Boolean> body) {
        recordService.requireStudent(username);
        boolean half = body != null && Boolean.TRUE.equals(body.get("halfPoints"));
        recordService.toggleCompletion(username, taskId, half, userService.currentUser());
    }

    @PutMapping("/students/{username}/tasks/{taskId}/toggle-lock")
    public Map<String, Boolean> toggleLock(@PathVariable("username") String username, @PathVariable("taskId") Long taskId) {
        recordService.requireStudent(username);
        return Map.of("isLocked", recordService.toggleLock(username, taskId));
    }

    @GetMapping("/statistics/tasks")
    public List<StudentRecordService.TaskStatistic> statistics() {
        return recordService.statistics();
    }
}

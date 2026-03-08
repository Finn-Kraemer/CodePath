package de.codepath.backend.features.admin;

import de.codepath.backend.features.modules.ModuleResponse;
import de.codepath.backend.features.tasks.TaskResponse;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;

    @GetMapping("/modules")
    public List<ModuleResponse> getModulesForAdmin() {
        return adminService.getAllModules();
    }

    @PutMapping("/modules/{id}/toggle")
    public ResponseEntity<Map<String, Boolean>> toggleModule(@PathVariable("id") Long id) {
        boolean newState = adminService.toggleModule(id);
        return ResponseEntity.ok(Map.of("isUnlocked", newState));
    }

    @PutMapping("/submissions/{id}/approve")
    public ResponseEntity<Void> approveSubmission(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Boolean> body) {
        User admin = userService.getCurrentUser();
        boolean halfPoints = body != null && Boolean.TRUE.equals(body.get("halfPoints"));
        adminService.approveSubmission(id, admin, halfPoints);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/submissions/{id}/reject")
    public ResponseEntity<Void> rejectSubmission(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        String comment = body != null ? (String) body.get("comment") : null;
        boolean lockTask = body != null && Boolean.TRUE.equals(body.get("lockTask"));
        adminService.rejectSubmission(id, comment, lockTask);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/announcement")
    public ResponseEntity<Void> updateAnnouncement(@RequestBody Map<String, String> body) {
        User admin = userService.getCurrentUser();
        de.codepath.backend.common.AnnouncementDisplayMode mode = de.codepath.backend.common.AnnouncementDisplayMode.valueOf(body.getOrDefault("displayMode", "HEADER"));
        adminService.updateAnnouncement(body.get("content"), mode, admin);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/students")
    public List<StudentOverviewResponse> getStudents() {
        return adminService.getStudentOverview();
    }

    @GetMapping("/submissions")
    public List<Map<String, Object>> getPendingSubmissions() {
        return adminService.getPendingSubmissions();
    }

    @GetMapping("/submissions/{id}")
    public Map<String, Object> getSubmission(@PathVariable("id") Long id) {
        return adminService.getSubmission(id);
    }

    @GetMapping("/students/{username}/submissions")
    public List<Map<String, Object>> getSubmissionsForStudent(@PathVariable("username") String username) {
        return adminService.getSubmissionsForStudent(username);
    }

    @GetMapping("/students/{username}/tasks")
    public List<TaskResponse> getStudentTasks(@PathVariable("username") String username) {
        return adminService.getTasksForStudent(username);
    }

    @PutMapping("/students/{username}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable("username") String username, @RequestBody Map<String, String> body) {
        adminService.resetPassword(username, body.get("password"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/students/{username}/tasks/{taskId}/toggle")
    public ResponseEntity<Void> toggleTask(@PathVariable("username") String username, @PathVariable("taskId") Long taskId, @RequestBody(required = false) Map<String, Boolean> body) {
        boolean halfPoints = body != null && Boolean.TRUE.equals(body.get("halfPoints"));
        adminService.toggleTaskCompletion(username, taskId, halfPoints);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/students/{username}/tasks/{taskId}/toggle-lock")
    public ResponseEntity<Void> toggleTaskLock(@PathVariable("username") String username, @PathVariable("taskId") Long taskId) {
        adminService.toggleTaskLock(username, taskId);
        return ResponseEntity.ok().build();
    }
}

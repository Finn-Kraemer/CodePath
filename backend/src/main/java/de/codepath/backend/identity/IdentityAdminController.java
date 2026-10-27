package de.codepath.backend.identity;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Kurse, gesammelte Konten und Sitzungen – nur für die Lehrkraft (/api/admin/**). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class IdentityAdminController {

    private final CourseService courseService;
    private final SessionAdminService sessionAdminService;
    private final UserService userService;

    public record CreateCourseRequest(@NotBlank String name, LocalDate endsOn) {
    }

    public record CreateAccountsRequest(String prefix, int count) {
    }

    @GetMapping("/courses")
    public List<CourseService.CourseSummary> courses() {
        return courseService.list();
    }

    @PostMapping("/courses")
    public CourseService.CourseSummary createCourse(@RequestBody CreateCourseRequest request) {
        return courseService.create(request.name(), request.endsOn());
    }

    @PostMapping("/courses/{id}/join-code")
    public CourseService.CourseSummary regenerateJoinCode(@PathVariable("id") Long id) {
        return courseService.regenerateJoinCode(id);
    }

    @PostMapping("/courses/{id}/accounts")
    public List<CourseService.GeneratedAccount> createAccounts(@PathVariable("id") Long id, @RequestBody CreateAccountsRequest request) {
        return courseService.createAccounts(id, request.prefix(), request.count());
    }

    @PostMapping("/courses/{id}/end")
    public Map<String, Integer> endCourse(@PathVariable("id") Long id) {
        return Map.of("deletedAccounts", courseService.endCourse(id));
    }

    @PostMapping("/sessions/logout-students")
    public Map<String, Integer> logoutAllStudents() {
        return Map.of("sessionsEnded", sessionAdminService.logoutAllStudents());
    }

    @PutMapping("/students/{username}/reset-password")
    public void resetPassword(@PathVariable("username") String username, @RequestBody Map<String, String> body) {
        userService.resetPassword(username, body.get("password"));
        sessionAdminService.logout(username);
    }
}

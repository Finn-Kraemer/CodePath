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

    /** months: Frist bis zur automatischen Löschung, Standard 6 */
    public record ArchiveRequest(Integer months) {
    }

    /** Fehlende Schalter gelten als „nein“. */
    public record PromoteRequest(@NotBlank String name, LocalDate endsOn, Boolean keepProgress, Boolean keepReleases,
                                 Boolean copyCodePaths, Boolean restartLeaderboard) {
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

    @PostMapping("/courses/{id}/archive")
    public CourseService.CourseSummary archive(@PathVariable("id") Long id, @RequestBody(required = false) ArchiveRequest request) {
        return courseService.archive(id, request == null ? null : request.months());
    }

    @PostMapping("/courses/{id}/restore")
    public CourseService.CourseSummary restore(@PathVariable("id") Long id) {
        return courseService.restore(id);
    }

    /** Neuer Kurs mit denselben Mitgliedern (z. B. ins nächste Schuljahr); liefert den neuen Kurs. */
    @PostMapping("/courses/{id}/promote")
    public CourseService.CourseSummary promote(@PathVariable("id") Long id, @RequestBody PromoteRequest request) {
        return courseService.promote(id, new CourseService.PromoteOptions(request.name(), request.endsOn(),
                Boolean.TRUE.equals(request.keepProgress()), Boolean.TRUE.equals(request.keepReleases()),
                Boolean.TRUE.equals(request.copyCodePaths()), Boolean.TRUE.equals(request.restartLeaderboard())));
    }

    /** Archivierte Kurse, deren Daten in den nächsten 14 Tagen gelöscht werden. */
    @GetMapping("/courses/purging-soon")
    public List<CourseService.CourseSummary> purgingSoon() {
        return courseService.purgingSoon(14);
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

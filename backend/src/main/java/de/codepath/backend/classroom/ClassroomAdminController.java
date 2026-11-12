package de.codepath.backend.classroom;

import de.codepath.backend.identity.CourseService;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Modulfreigaben je Kurs, Ankündigungen, Feedback-Runde und Zeugnis-Schalter. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ClassroomAdminController {

    private final ReleaseService releaseService;
    private final ClassroomService classroomService;
    private final CourseService courseService;
    private final SettingsService settings;
    private final UserService userService;
    private final UserReleaseService userReleaseService;

    /**
     * Freigabe für eine oder mehrere Personen. Genau eins von moduleId und taskId; mode SHOWN, HIDDEN oder
     * NONE (aufheben); availableUntil optional (ISO-Datum mit Uhrzeit); bonus: Punkte ohne Ranglistenwirkung.
     */
    public record UserReleaseRequest(List<String> usernames, Long moduleId, Long taskId, String mode,
                                     String availableUntil, Boolean bonus) {
    }

    @GetMapping("/modules")
    public List<ReleaseService.ModuleReleaseView> modules(@RequestParam(name = "courseId", required = false) Long courseId) {
        return releaseService.forCourse(resolveCourse(courseId));
    }

    @GetMapping("/modules/{id}/tasks")
    public List<ReleaseService.TaskReleaseView> moduleTasks(@PathVariable("id") Long moduleId,
                                                            @RequestParam(name = "courseId", required = false) Long courseId) {
        return releaseService.tasks(resolveCourse(courseId), moduleId);
    }

    /** mode: SHOWN, HIDDEN oder MODULE (folgt wieder dem Modul). */
    @PutMapping("/modules/{id}/tasks/{taskId}/release")
    public ReleaseService.TaskReleaseView setTaskRelease(@PathVariable("id") Long moduleId, @PathVariable("taskId") Long taskId,
                                                         @RequestParam("mode") String mode,
                                                         @RequestParam(name = "courseId", required = false) Long courseId) {
        TaskReleaseMode parsed;
        try {
            parsed = "MODULE".equals(mode) ? null : TaskReleaseMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("mode: SHOWN, HIDDEN oder MODULE");
        }
        return releaseService.setTaskMode(resolveCourse(courseId), moduleId, taskId, parsed);
    }

    @PutMapping("/modules/{id}/toggle")
    public Map<String, Boolean> toggle(@PathVariable("id") Long moduleId, @RequestParam(name = "courseId", required = false) Long courseId) {
        return Map.of("isUnlocked", releaseService.toggle(resolveCourse(courseId), moduleId));
    }

    @PutMapping("/modules/{id}/deadline")
    public void deadline(@PathVariable("id") Long moduleId, @RequestParam(name = "courseId", required = false) Long courseId,
                         @RequestBody Map<String, String> body) {
        releaseService.setDeadline(resolveCourse(courseId), moduleId, parseDeadline(body.get("deadline")));
    }

    @GetMapping("/students/{username}/releases")
    public List<UserReleaseService.UserReleaseView> studentReleases(@PathVariable("username") String username) {
        return userReleaseService.forStudent(username);
    }

    @DeleteMapping("/students/{username}/releases/{id}")
    public void deleteStudentRelease(@PathVariable("username") String username, @PathVariable("id") Long id) {
        userReleaseService.delete(username, id);
    }

    /** Freigaben einzelner Personen eines Kurses in einem Modul (für die Modul-Steuerung). */
    @GetMapping("/modules/{id}/user-releases")
    public List<UserReleaseService.UserReleaseView> moduleUserReleases(@PathVariable("id") Long moduleId,
                                                                       @RequestParam(name = "courseId", required = false) Long courseId) {
        return userReleaseService.forModule(resolveCourse(courseId), moduleId);
    }

    @PostMapping("/user-releases")
    public List<UserReleaseService.UserReleaseView> setUserReleases(@RequestBody UserReleaseRequest request) {
        TaskReleaseMode mode;
        try {
            mode = request.mode() == null || "NONE".equals(request.mode()) ? null : TaskReleaseMode.valueOf(request.mode());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("mode: SHOWN, HIDDEN oder NONE");
        }
        return userReleaseService.apply(new UserReleaseService.Change(request.usernames(), request.moduleId(), request.taskId(),
                mode, parseDeadline(request.availableUntil()), Boolean.TRUE.equals(request.bonus())));
    }

    @PutMapping("/announcement")
    public void announcement(@RequestBody Map<String, String> body) {
        AnnouncementDisplayMode mode;
        try {
            mode = AnnouncementDisplayMode.valueOf(body.getOrDefault("displayMode", "HEADER"));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unbekannter Anzeigemodus.");
        }
        classroomService.updateAnnouncement(body.get("content"), mode, userService.currentUser());
    }

    @GetMapping("/feedback")
    public List<Map<String, Object>> feedback() {
        return classroomService.allFeedback();
    }

    @GetMapping("/feedback/status")
    public Map<String, Boolean> feedbackStatus() {
        return Map.of("enabled", settings.isEnabled(SettingsService.FEEDBACK_ENABLED));
    }

    @PutMapping("/feedback/toggle")
    public Map<String, Boolean> toggleFeedback() {
        return Map.of("enabled", settings.toggle(SettingsService.FEEDBACK_ENABLED));
    }

    @GetMapping("/settings/certificates")
    public Map<String, Boolean> certificates() {
        return Map.of("enabled", settings.isEnabled(SettingsService.CERTIFICATES_ENABLED));
    }

    @PutMapping("/settings/certificates/toggle")
    public Map<String, Boolean> toggleCertificates() {
        return Map.of("enabled", settings.toggle(SettingsService.CERTIFICATES_ENABLED));
    }

    /** Ohne Angabe gilt der neueste aktive Kurs – so bleibt die Oberfläche mit einem Kurs einfach. */
    private Long resolveCourse(Long courseId) {
        if (courseId != null) return courseId;
        return courseService.activeCourses().stream().findFirst()
                .orElseThrow(() -> ApiException.badRequest("Bitte zuerst einen Kurs anlegen."))
                .getId();
    }

    static LocalDateTime parseDeadline(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.contains("T")) return LocalDateTime.parse(value);
            return LocalDateTime.of(LocalDate.now(), LocalTime.parse(value));
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Ungültige Uhrzeit.");
        }
    }
}

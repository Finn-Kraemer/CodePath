package de.codepath.backend.classroom;

import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Für alle angemeldeten Nutzer. */
@RestController
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;
    private final SettingsService settings;
    private final UserService userService;

    @GetMapping("/api/common/announcement")
    public Map<String, String> announcement() {
        return classroomService.currentAnnouncement();
    }

    @GetMapping("/api/common/settings/certificates")
    public Map<String, Boolean> certificates() {
        return Map.of("enabled", settings.isEnabled(SettingsService.CERTIFICATES_ENABLED));
    }

    @GetMapping("/api/feedback/status")
    public Map<String, Boolean> feedbackStatus() {
        return classroomService.feedbackStatus(userService.currentUser());
    }

    @PostMapping("/api/feedback")
    public void submitFeedback(@RequestBody Map<String, String> body) {
        classroomService.submitFeedback(userService.currentUser(), body.get("content"));
    }
}

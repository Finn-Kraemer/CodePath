package de.codepath.backend.features.feedback;

import de.codepath.backend.common.SystemSetting;
import de.codepath.backend.common.SystemSettingRepository;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final GeneralFeedbackRepository feedbackRepository;
    private final SystemSettingRepository settingRepository;
    private final UserService userService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getFeedbackStatus() {
        User user = userService.getCurrentUser();
        boolean enabled = isFeedbackEnabled();
        boolean alreadySubmitted = feedbackRepository.existsByUserId(user.getId());
        
        return ResponseEntity.ok(Map.of(
            "enabled", enabled,
            "alreadySubmitted", alreadySubmitted
        ));
    }

    @PostMapping
    public ResponseEntity<Void> submitFeedback(@RequestBody Map<String, String> body) {
        if (!isFeedbackEnabled()) {
            return ResponseEntity.badRequest().build();
        }

        User user = userService.getCurrentUser();
        if (feedbackRepository.existsByUserId(user.getId())) {
            return ResponseEntity.status(409).build();
        }

        GeneralFeedback feedback = new GeneralFeedback();
        feedback.setUser(user);
        feedback.setContent(body.get("content"));
        feedback.setSubmittedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);

        return ResponseEntity.ok().build();
    }

    private boolean isFeedbackEnabled() {
        return settingRepository.findById("feedback_enabled")
                .map(s -> "true".equalsIgnoreCase(s.getValue()))
                .orElse(false);
    }
}

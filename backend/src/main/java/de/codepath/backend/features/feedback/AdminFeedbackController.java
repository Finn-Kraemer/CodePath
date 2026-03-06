package de.codepath.backend.features.feedback;

import de.codepath.backend.common.SystemSetting;
import de.codepath.backend.common.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final GeneralFeedbackRepository feedbackRepository;
    private final SystemSettingRepository settingRepository;

    @GetMapping
    public List<Map<String, Object>> getAllFeedback() {
        return feedbackRepository.findAll(Sort.by(Sort.Direction.DESC, "submittedAt")).stream()
                .map(f -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", f.getId());
                    map.put("username", f.getUser().getUsername());
                    map.put("displayName", f.getUser().getDisplayName() != null ? f.getUser().getDisplayName() : f.getUser().getUsername());
                    map.put("content", f.getContent());
                    map.put("submittedAt", f.getSubmittedAt());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/status")
    public Map<String, Boolean> getStatus() {
        boolean enabled = settingRepository.findById("feedback_enabled")
                .map(s -> "true".equalsIgnoreCase(s.getValue()))
                .orElse(false);
        return Map.of("enabled", enabled);
    }

    @PutMapping("/toggle")
    public ResponseEntity<Map<String, Boolean>> toggleFeedback() {
        SystemSetting setting = settingRepository.findById("feedback_enabled")
                .orElse(new SystemSetting("feedback_enabled", "false"));
        
        boolean current = "true".equalsIgnoreCase(setting.getValue());
        setting.setValue(current ? "false" : "true");
        settingRepository.save(setting);
        
        return ResponseEntity.ok(Map.of("enabled", !current));
    }
}

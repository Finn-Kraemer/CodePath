package de.codepath.backend.classroom;

import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.platform.events.RealtimeUpdateService;
import de.codepath.backend.platform.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ankündigungen und Feedback-Runde. */
@Service
@RequiredArgsConstructor
public class ClassroomService {

    static final int MAX_FEEDBACK_LENGTH = 5000;
    static final int MAX_ANNOUNCEMENT_LENGTH = 2000;

    private final AnnouncementRepository announcementRepository;
    private final FeedbackRepository feedbackRepository;
    private final SettingsService settings;
    private final RealtimeUpdateService realtime;
    private final AuditService auditService;
    private final UserService userService;

    public Map<String, String> currentAnnouncement() {
        return announcementRepository.findFirstByOrderByUpdatedAtDesc()
                .map(a -> Map.of("content", a.getContent(), "displayMode", a.getDisplayMode().name(),
                        "updatedAt", a.getUpdatedAt().toString()))
                .orElse(Map.of("content", "", "displayMode", AnnouncementDisplayMode.HEADER.name()));
    }

    @Transactional
    public void updateAnnouncement(String content, AnnouncementDisplayMode mode, User teacher) {
        String text = content == null ? "" : content;
        if (text.length() > MAX_ANNOUNCEMENT_LENGTH) throw ApiException.badRequest("Die Ankündigung ist zu lang.");
        Announcement announcement = announcementRepository.findFirstByOrderByUpdatedAtDesc().orElseGet(Announcement::new);
        announcement.setContent(text);
        announcement.setDisplayMode(mode);
        announcement.setUpdatedAt(LocalDateTime.now());
        announcement.setUpdatedBy(teacher.getId());
        announcementRepository.save(announcement);
        auditService.log("ANNOUNCEMENT_UPDATED", mode.name(), Map.of("length", text.length()));
        realtime.publishAnnouncement(text, mode.name());
    }

    public Map<String, Boolean> feedbackStatus(User user) {
        return Map.of("enabled", settings.isEnabled(SettingsService.FEEDBACK_ENABLED),
                "alreadySubmitted", feedbackRepository.existsByUserId(user.getId()));
    }

    @Transactional
    public void submitFeedback(User user, String content) {
        if (!settings.isEnabled(SettingsService.FEEDBACK_ENABLED)) throw ApiException.badRequest("Die Feedback-Runde ist nicht geöffnet.");
        if (feedbackRepository.existsByUserId(user.getId())) throw ApiException.conflict("Du hast bereits Feedback gegeben.");
        if (content == null || content.isBlank()) throw ApiException.badRequest("Das Feedback ist leer.");
        if (content.length() > MAX_FEEDBACK_LENGTH) throw ApiException.badRequest("Das Feedback ist zu lang.");
        Feedback feedback = new Feedback();
        feedback.setUserId(user.getId());
        feedback.setContent(content.strip());
        feedbackRepository.save(feedback);
    }

    public List<Map<String, Object>> allFeedback() {
        return feedbackRepository.findAllByOrderBySubmittedAtDesc().stream().map(f -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", f.getId());
            var user = userService.byId(f.getUserId());
            map.put("username", user.map(User::getUsername).orElse("gelöscht"));
            map.put("displayName", user.map(User::getVisibleName).orElse("gelöscht"));
            map.put("content", f.getContent());
            map.put("submittedAt", f.getSubmittedAt());
            return map;
        }).toList();
    }
}

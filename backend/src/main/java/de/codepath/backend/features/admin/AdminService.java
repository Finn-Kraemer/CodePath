package de.codepath.backend.features.admin;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.modules.ModuleRepository;
import de.codepath.backend.features.modules.ModuleResponse;
import de.codepath.backend.features.tasks.*;
import de.codepath.backend.common.AnnouncementDisplayMode;
import de.codepath.backend.common.SystemSetting;
import de.codepath.backend.common.SystemSettingRepository;
import de.codepath.backend.features.messaging.RealtimeUpdateService;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRepository;
import de.codepath.backend.users.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final ModuleRepository moduleRepository;
    private final PracticeSubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final UserTaskCompletionRepository completionRepository;
    private final PasswordEncoder passwordEncoder;
    private final de.codepath.backend.common.GlobalAnnouncementRepository announcementRepository;
    private final RealtimeUpdateService realtimeUpdateService;
    private final SystemSettingRepository settingRepository;

    @Transactional
    public boolean toggleCertificates() {
        SystemSetting setting = settingRepository.findById("certificates_enabled")
                .orElse(new SystemSetting("certificates_enabled", "false"));
        
        boolean current = "true".equalsIgnoreCase(setting.getValue());
        setting.setValue(current ? "false" : "true");
        settingRepository.save(setting);
        return !current;
    }

    public boolean areCertificatesEnabled() {
        return settingRepository.findById("certificates_enabled")
                .map(s -> "true".equalsIgnoreCase(s.getValue()))
                .orElse(false);
    }

    @Transactional
    public boolean toggleModule(Long id) {
        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modul nicht gefunden: " + id));
        
        module.setIsUnlocked(!Boolean.TRUE.equals(module.getIsUnlocked()));
        Module saved = moduleRepository.save(module);
        
        log.info("Modul {} ist nun unlocked={}", saved.getTitle(), saved.getIsUnlocked());
        return saved.getIsUnlocked();
    }

    @Transactional
    public void setModuleDeadline(Long id, LocalDateTime deadline) {
        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modul nicht gefunden: " + id));
        module.setAvailableUntil(deadline);
        moduleRepository.save(module);
    }

    public List<ModuleResponse> getAllModules() {
        return moduleRepository.findAll(Sort.by("sortOrder")).stream()
                .map(m -> ModuleResponse.builder()
                        .id(m.getId())
                        .slug(m.getSlug())
                        .title(m.getTitle())
                        .description(m.getDescription())
                        .iconEmoji(m.getIconEmoji())
                        .isUnlocked(m.getIsUnlocked())
                        .availableUntil(m.getAvailableUntil())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void approveSubmission(Long id, User admin, boolean halfPoints) {
        PracticeSubmission submission = submissionRepository.findById(id).orElseThrow();
        if (submission.getStatus() == SubmissionStatus.APPROVED) return;

        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setReviewedAt(LocalDateTime.now());
        submission.setReviewer(admin);
        
        // If admin manually chooses half points, we override the supportUsed flag for point calculation
        if (halfPoints) {
            submission.setSupportUsed(true);
        }
        submissionRepository.save(submission);

        saveCompletion(submission.getUser(), submission.getTask(), submission.isSupportUsed());
        
        // Push Leaderboard Update
        realtimeUpdateService.publishLeaderboardUpdate();
    }

    @Transactional
    public void rejectSubmission(Long id, String comment, boolean lockTask) {
        PracticeSubmission submission = submissionRepository.findById(id).orElseThrow();
        
        User user = submission.getUser();
        Task task = submission.getTask();

        // If it was already approved, we need to remove the completion and points
        if (submission.getStatus() == SubmissionStatus.APPROVED) {
            completionRepository.findByUser_IdAndTask_Id(user.getId(), task.getId()).ifPresent(completion -> {
                user.setTotalPoints(user.getTotalPoints() - completion.getPointsAwarded());
                completion.setCompleted(false);
                completion.setPointsAwarded(0);
                completionRepository.save(completion);
                userRepository.save(user);
            });
            
            // Push Leaderboard Update since points changed
            realtimeUpdateService.publishLeaderboardUpdate();
        }
        
        if (lockTask) {
            UserTaskCompletion completion = completionRepository.findByUser_IdAndTask_Id(user.getId(), task.getId())
                    .orElseGet(() -> {
                        UserTaskCompletion c = new UserTaskCompletion();
                        c.setUser(user);
                        c.setTask(task);
                        return c;
                    });
            completion.setLocked(true);
            completionRepository.save(completion);
        }

        submission.setStatus(SubmissionStatus.REJECTED);
        submission.setAdminComment(comment);
        submission.setReviewedAt(LocalDateTime.now());
        submissionRepository.save(submission);
    }

    @Transactional
    public void updateAnnouncement(String content, AnnouncementDisplayMode displayMode, User admin) {
        de.codepath.backend.common.GlobalAnnouncement announcement = announcementRepository.findFirstByOrderByUpdatedAtDesc()
                .orElse(new de.codepath.backend.common.GlobalAnnouncement());
        announcement.setContent(content);
        announcement.setDisplayMode(displayMode);
        announcement.setUpdatedAt(LocalDateTime.now());
        announcement.setUpdatedBy(admin);
        announcementRepository.save(announcement);
        
        // Echtzeit-Push via MQTT
        realtimeUpdateService.publishAnnouncement(content, displayMode.name());
    }

    private void saveCompletion(User user, Task task, boolean supportUsed) {
        UserTaskCompletion completion = completionRepository.findByUser_IdAndTask_Id(user.getId(), task.getId())
                .orElse(new UserTaskCompletion());
        
        if (completion.isCompleted()) return;

        int points = task.getPoints();
        if (supportUsed) {
            points = Math.max(1, points / 2);
        }

        completion.setUser(user);
        completion.setTask(task);
        completion.setPointsAwarded(points);
        completion.setCompletedAt(LocalDateTime.now());
        completion.setCompleted(true);
        completion.setSupportUsed(supportUsed);
        completionRepository.save(completion);

        user.setTotalPoints(user.getTotalPoints() + points);
        userRepository.save(user);
    }

    public List<StudentOverviewResponse> getStudentOverview() {
        List<User> students = userRepository.findByRole(UserRole.STUDENT);
        if (students.isEmpty()) {
            return List.of();
        }
        
        List<Long> userIds = students.stream().map(User::getId).collect(Collectors.toList());
        List<Object[]> counts = completionRepository.countTasksByUserIds(userIds);
        
        Map<Long, Long> completedCounts = new HashMap<>();
        for (Object[] row : counts) {
            completedCounts.put((Long) row[0], (Long) row[1]);
        }

        return students.stream()
                .map(u -> StudentOverviewResponse.builder()
                        .username(u.getUsername())
                        .displayName(u.getDisplayName())
                        .totalPoints(u.getTotalPoints())
                        .completedTasks(completedCounts.getOrDefault(u.getId(), 0L))
                        .build()
                )
                .collect(Collectors.toList());
    }

    public List<TaskResponse> getTasksForStudent(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return taskRepository.findAll(Sort.by("module.sortOrder", "sortOrder")).stream()
                .map(t -> {
                    UserTaskCompletion completion = completionRepository.findByUser_IdAndTask_Id(user.getId(), t.getId())
                            .orElse(null);
                    return TaskResponse.builder()
                            .id(t.getId())
                            .slug(t.getSlug())
                            .title(t.getTitle())
                            .type(t.getType().name())
                            .difficulty(t.getDifficulty().name())
                            .points(t.getPoints())
                            .moduleTitle(t.getModule().getTitle())
                            .isCompleted(completion != null && completion.isCompleted())
                            .isLocked(completion != null && completion.isLocked())
                            .supportUsed(completion != null && completion.isSupportUsed())
                            .build();
                })
                .collect(Collectors.toList());
    }


    public List<Map<String, Object>> getPendingSubmissions() {
        return submissionRepository.findByStatusWithDetails(SubmissionStatus.PENDING).stream()
                .map(this::mapSubmission)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getSubmissionsForStudent(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return submissionRepository.findByUserIdWithDetails(user.getId()).stream()
                .map(this::mapSubmission)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getSubmission(Long id) {
        PracticeSubmission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Abgabe nicht gefunden"));
        return mapSubmission(submission);
    }

    private Map<String, Object> mapSubmission(PracticeSubmission s) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", s.getId());
        map.put("username", s.getUser().getUsername());
        map.put("displayName", s.getUser().getDisplayName() != null ? s.getUser().getDisplayName() : s.getUser().getUsername());
        map.put("taskTitle", s.getTask().getTitle());
        map.put("moduleTitle", s.getTask().getModule().getTitle());
        map.put("submittedAt", s.getSubmittedAt());
        map.put("content", s.getContent());
        map.put("status", s.getStatus().name());
        map.put("adminComment", s.getAdminComment());
        map.put("supportUsed", s.isSupportUsed());
        return map;
    }

    @Transactional
    public void resetPassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void toggleTaskCompletion(String username, Long taskId, boolean halfPoints) {
        User user = userRepository.findByUsername(username).orElseThrow();
        Task task = taskRepository.findById(taskId).orElseThrow();
        
        completionRepository.findByUser_IdAndTask_Id(user.getId(), taskId).ifPresentOrElse(
            completion -> {
                // If it exists, we either delete it or update the points
                int currentPoints = completion.getPointsAwarded();
                int targetPoints = halfPoints ? Math.max(1, task.getPoints() / 2) : task.getPoints();

                if (currentPoints == targetPoints) {
                    // Same status, so we delete (toggle off)
                    user.setTotalPoints(user.getTotalPoints() - currentPoints);
                    completionRepository.delete(completion);
                } else {
                    // Update points (e.g., from full to half or vice versa)
                    user.setTotalPoints(user.getTotalPoints() - currentPoints + targetPoints);
                    completion.setPointsAwarded(targetPoints);
                    completion.setSupportUsed(halfPoints);
                    completionRepository.save(completion);
                }
            },
            () -> {
                // Not completed yet, so we create it
                saveCompletion(user, task, halfPoints);
            }
        );
        userRepository.save(user);
        
        // Push Leaderboard Update
        realtimeUpdateService.publishLeaderboardUpdate();
    }

    @Transactional
    public void toggleTaskLock(String username, Long taskId) {
        User user = userRepository.findByUsername(username).orElseThrow();
        
        UserTaskCompletion completion = completionRepository.findByUser_IdAndTask_Id(user.getId(), taskId)
                .orElseGet(() -> {
                    Task task = taskRepository.findById(taskId).orElseThrow();
                    UserTaskCompletion c = new UserTaskCompletion();
                    c.setUser(user);
                    c.setTask(task);
                    return c;
                });
        
        completion.setLocked(!completion.isLocked());
        completionRepository.save(completion);
    }
}

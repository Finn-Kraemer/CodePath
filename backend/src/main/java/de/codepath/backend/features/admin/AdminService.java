package de.codepath.backend.features.admin;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.modules.ModuleRepository;
import de.codepath.backend.features.modules.ModuleResponse;
import de.codepath.backend.features.tasks.*;
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

    @Transactional
    public boolean toggleModule(Long id) {
        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modul nicht gefunden: " + id));
        
        module.setIsUnlocked(!Boolean.TRUE.equals(module.getIsUnlocked()));
        Module saved = moduleRepository.save(module);
        
        log.info("Modul {} ist nun unlocked={}", saved.getTitle(), saved.getIsUnlocked());
        return saved.getIsUnlocked();
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
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void approveSubmission(Long id, User admin) {
        PracticeSubmission submission = submissionRepository.findById(id).orElseThrow();
        if (submission.getStatus() == SubmissionStatus.APPROVED) return;

        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setReviewedAt(LocalDateTime.now());
        submission.setReviewer(admin);
        submissionRepository.save(submission);

        saveCompletion(submission.getUser(), submission.getTask());
    }

    @Transactional
    public void rejectSubmission(Long id) {
        PracticeSubmission submission = submissionRepository.findById(id).orElseThrow();
        
        // If it was already approved, we need to remove the completion and points
        if (submission.getStatus() == SubmissionStatus.APPROVED) {
            User user = submission.getUser();
            Task task = submission.getTask();
            
            if (completionRepository.existsByUser_IdAndTask_Id(user.getId(), task.getId())) {
                UserTaskCompletion completion = completionRepository.findById(new UserTaskCompletionId(user.getId(), task.getId())).orElseThrow();
                user.setTotalPoints(user.getTotalPoints() - completion.getPointsAwarded());
                completionRepository.delete(completion);
                userRepository.save(user);
            }
        }
        
        // Delete the submission entirely so the student can submit again
        submissionRepository.delete(submission);
    }

    private void saveCompletion(User user, Task task) {
        if (completionRepository.existsByUser_IdAndTask_Id(user.getId(), task.getId())) return;

        UserTaskCompletion completion = new UserTaskCompletion();
        completion.setUser(user);
        completion.setTask(task);
        completion.setPointsAwarded(task.getPoints());
        completion.setCompletedAt(LocalDateTime.now());
        completionRepository.save(completion);

        user.setTotalPoints(user.getTotalPoints() + task.getPoints());
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
                .map(t -> TaskResponse.builder()
                        .id(t.getId())
                        .slug(t.getSlug())
                        .title(t.getTitle())
                        .type(t.getType().name())
                        .difficulty(t.getDifficulty().name())
                        .points(t.getPoints())
                        .moduleTitle(t.getModule().getTitle())
                        .isCompleted(completionRepository.existsByUser_IdAndTask_Id(user.getId(), t.getId()))
                        .build()
                )
                .collect(Collectors.toList());
    }


    public List<Map<String, Object>> getPendingSubmissions() {
        return submissionRepository.findByStatusWithDetails(SubmissionStatus.PENDING).stream()
                .map(this::mapSubmission)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getPendingSubmissionsForStudent(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return submissionRepository.findByStatusWithDetails(SubmissionStatus.PENDING).stream()
                .filter(s -> s.getUser().getId().equals(user.getId()))
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
        return map;
    }

    @Transactional
    public void resetPassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void toggleTaskCompletion(String username, Long taskId) {
        User user = userRepository.findByUsername(username).orElseThrow();
        Task task = taskRepository.findById(taskId).orElseThrow();
        
        if (completionRepository.existsByUser_IdAndTask_Id(user.getId(), taskId)) {
            UserTaskCompletion completion = completionRepository.findById(new UserTaskCompletionId(user.getId(), taskId)).orElseThrow();
            user.setTotalPoints(user.getTotalPoints() - completion.getPointsAwarded());
            completionRepository.delete(completion);
        } else {
            saveCompletion(user, task);
        }
        userRepository.save(user);
    }
}

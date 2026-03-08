package de.codepath.backend.features.tasks;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.modules.ModuleRepository;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRole;
import de.codepath.backend.common.GlobalExceptionHandler.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserTaskCompletionRepository completionRepository;
    private final ModuleRepository moduleRepository;
    private final PracticeSubmissionRepository practiceSubmissionRepository;

    public List<TaskResponse> getTasksForModule(Module module, User user) {
        return taskRepository.findByModuleIdOrderBySortOrderAsc(module.getId()).stream()
                .map(t -> mapToResponse(t, user))
                .collect(Collectors.toList());
    }

    public TaskResponse getTask(String moduleSlug, String taskSlug, User user) {
        Module module = moduleRepository.findBySlug(moduleSlug)
                .orElseThrow(() -> new RuntimeException("Module not found"));
        
        if (user.getRole() != UserRole.ADMIN && !Boolean.TRUE.equals(module.getIsUnlocked())) {
            throw new AccessDeniedException("Dieses Modul ist aktuell gesperrt.");
        }

        Task task = taskRepository.findByModuleIdAndSlug(module.getId(), taskSlug)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return mapToResponse(task, user);
    }

    private TaskResponse mapToResponse(Task t, User user) {
        String submissionStatus = null;
        String submissionContent = null;
        String adminComment = null;
        if (t.getType() == TaskType.PRACTICE) {
            PracticeSubmission sub = practiceSubmissionRepository.findTopByUser_IdAndTask_IdOrderBySubmittedAtDesc(user.getId(), t.getId())
                    .orElse(null);
            if (sub != null) {
                submissionStatus = sub.getStatus().name();
                submissionContent = sub.getContent();
                adminComment = sub.getAdminComment();
            } else {
                submissionStatus = "NOT_SUBMITTED";
            }
        }

        UserTaskCompletion completion = completionRepository.findByUser_IdAndTask_Id(user.getId(), t.getId())
                .orElse(null);

        return TaskResponse.builder()
                .id(t.getId())
                .slug(t.getSlug())
                .title(t.getTitle())
                .story(t.getStory())
                .description(t.getDescription())
                .type(t.getType().name())
                .difficulty(t.getDifficulty().name())
                .points(t.getPoints())
                .config(t.getConfig())
                .isCompleted(completion != null && completion.isCompleted())
                .isLocked(completion != null && completion.isLocked())
                .supportUsed(completion != null && completion.isSupportUsed())
                .submissionStatus(submissionStatus)
                .submissionContent(submissionContent)
                .adminComment(adminComment)
                .failedAttempts(completion != null ? completion.getFailedAttempts() : 0)
                .timeSpentSeconds(completion != null ? completion.getTimeSpentSeconds() : 0L)
                .build();
    }
}

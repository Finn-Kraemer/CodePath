package de.codepath.backend.features.tasks;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.modules.ModuleRepository;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaskSubmissionService {

    private final TaskRepository taskRepository;
    private final UserTaskCompletionRepository completionRepository;
    private final PracticeSubmissionRepository practiceSubmissionRepository;
    private final UserRepository userRepository;
    private final ModuleRepository moduleRepository;

    @Transactional
    public SubmitResponse submitTask(String moduleSlug, String taskSlug, User user, SubmitRequest request) {
        Module module = moduleRepository.findBySlug(moduleSlug)
                .orElseThrow(() -> new RuntimeException("Module not found"));
        Task task = taskRepository.findByModuleIdAndSlug(module.getId(), taskSlug)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        boolean isCorrect = validatePayload(task, request.getPayload());

        if (isCorrect) {
            if (task.getType() == TaskType.PRACTICE) {
                return handlePracticeSubmission(task, user, request.getPayload());
            } else {
                // Award points only if not already completed
                boolean alreadyDone = completionRepository.existsByUser_IdAndTask_Id(user.getId(), task.getId());
                if (!alreadyDone) {
                    saveCompletion(user, task);
                    return SubmitResponse.builder()
                            .isCorrect(true)
                            .pointsAwarded(task.getPoints())
                            .feedback("Super! Das war richtig.")
                            .build();
                } else {
                    return SubmitResponse.builder()
                            .isCorrect(true)
                            .pointsAwarded(0)
                            .feedback("Korrekt! Du hast diese Aufgabe bereits gelöst.")
                            .build();
                }
            }
        }

        return SubmitResponse.builder()
                .isCorrect(false)
                .pointsAwarded(0)
                .feedback("Leider falsch. Tipp: " + task.getConfig().getOrDefault("hint", "Probiere es noch einmal!"))
                .build();
    }

    private boolean validatePayload(Task task, Map<String, Object> payload) {
        if (payload == null) return false;
        
        try {
            return switch (task.getType()) {
                case MULTIPLE_CHOICE -> validateMultipleChoice(task, payload);
                case FILL_BLANK -> validateFillBlank(task, payload);
                case FILL_CODE, CODE -> validateCode(task, payload);
                case PRACTICE -> true;
            };
        } catch (Exception e) {
            return false; // Any malformed payload or config results in "wrong"
        }
    }

    private boolean validateMultipleChoice(Task task, Map<String, Object> payload) {
        List<?> selected = (List<?>) payload.get("selected");
        List<?> correct = (List<?>) task.getConfig().get("correct");
        
        if (selected == null || correct == null) return false;
        return selected.size() == correct.size() && selected.containsAll(correct);
    }

    private boolean validateFillBlank(Task task, Map<String, Object> payload) {
        List<?> answers = (List<?>) payload.get("answers");
        List<?> blanks = (List<?>) task.getConfig().get("blanks");
        
        if (answers == null || blanks == null || answers.size() != blanks.size()) return false;

        for (int i = 0; i < blanks.size(); i++) {
            Map<?, ?> blankConfig = (Map<?, ?>) blanks.get(i);
            String expected = (String) blankConfig.get("answer");
            Object caseSensitiveObj = blankConfig.get("caseSensitive");
            boolean caseSensitive = caseSensitiveObj instanceof Boolean && (Boolean) caseSensitiveObj;
            
            String actual = answers.get(i) != null ? answers.get(i).toString() : "";
            
            if (expected == null) return false;
            
            if (caseSensitive) {
                if (!expected.equals(actual)) return false;
            } else {
                if (!expected.equalsIgnoreCase(actual)) return false;
            }
        }
        return true;
    }

    private boolean validateCode(Task task, Map<String, Object> payload) {
        String output = payload.get("output") != null ? payload.get("output").toString() : null;
        String expected = task.getConfig().get("expectedOutput") != null ? task.getConfig().get("expectedOutput").toString() : null;
        
        if (expected == null) return false;
        return output != null && output.trim().equals(expected.trim());
    }

    private SubmitResponse handlePracticeSubmission(Task task, User user, Map<String, Object> payload) {
        // Prevent spam: check if a PENDING submission already exists
        boolean hasPending = practiceSubmissionRepository.existsByUser_IdAndTask_IdAndStatus(user.getId(), task.getId(), SubmissionStatus.PENDING);
        
        if (hasPending) {
            return SubmitResponse.builder()
                    .isCorrect(true)
                    .pointsAwarded(0)
                    .status("PENDING")
                    .feedback("Du hast bereits eine Abgabe eingereicht, die noch geprüft wird.")
                    .build();
        }

        PracticeSubmission submission = new PracticeSubmission();
        submission.setTask(task);
        submission.setUser(user);
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setSubmittedAt(LocalDateTime.now());
        
        // Content from TipTap is sent in practiceContent payload key
        if (payload != null && payload.containsKey("practiceContent")) {
            submission.setContent((String) payload.get("practiceContent"));
        }

        practiceSubmissionRepository.save(submission);

        return SubmitResponse.builder()
                .isCorrect(true)
                .pointsAwarded(0)
                .status("PENDING")
                .feedback("Abgabe eingereicht! Die Lehrkraft wird sie prüfen.")
                .build();
    }

    private void saveCompletion(User user, Task task) {
        UserTaskCompletion completion = new UserTaskCompletion();
        completion.setUser(user);
        completion.setTask(task);
        completion.setPointsAwarded(task.getPoints());
        completion.setCompletedAt(LocalDateTime.now());
        completionRepository.save(completion);

        user.setTotalPoints(user.getTotalPoints() + task.getPoints());
        userRepository.save(user);
    }
}

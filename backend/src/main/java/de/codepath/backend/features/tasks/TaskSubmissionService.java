package de.codepath.backend.features.tasks;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.modules.ModuleRepository;
import de.codepath.backend.features.tasks.events.PracticeSubmissionEvent;
import de.codepath.backend.features.tasks.events.TaskCompletionEvent;
import de.codepath.backend.features.tasks.messaging.SubmissionProducer;
import de.codepath.backend.features.messaging.RealtimeUpdateService;
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
    private final SubmissionProducer submissionProducer;
    private final RealtimeUpdateService realtimeUpdateService;

    @Transactional
    public SubmitResponse submitTask(String moduleSlug, String taskSlug, User user, SubmitRequest request) {
        Module module = moduleRepository.findBySlug(moduleSlug)
                .orElseThrow(() -> new RuntimeException("Module not found"));
        
        // Check Deadline
        if (module.getAvailableUntil() != null && module.getAvailableUntil().isBefore(LocalDateTime.now())) {
            return SubmitResponse.builder()
                    .isCorrect(false)
                    .feedback("Die Bearbeitungszeit für dieses Modul ist abgelaufen.")
                    .build();
        }

        Task task = taskRepository.findByModuleIdAndSlug(module.getId(), taskSlug)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Get or create progress
        UserTaskCompletion progress = completionRepository.findByUser_IdAndTask_Id(user.getId(), task.getId())
                .orElseGet(() -> {
                    UserTaskCompletion c = new UserTaskCompletion();
                    c.setUser(user);
                    c.setTask(task);
                    return c;
                });

        if (progress.isLocked()) {
            return SubmitResponse.builder()
                    .isCorrect(false)
                    .isLocked(true)
                    .feedback("Diese Aufgabe ist gesperrt.")
                    .build();
        }

        // Update time spent locally for calculation, but don't save yet if using MQ
        if (request.getTimeSpentSeconds() != null) {
            progress.setTimeSpentSeconds(progress.getTimeSpentSeconds() + request.getTimeSpentSeconds());
        }

        boolean isCorrect = validatePayload(task, request.getPayload());

        if (isCorrect) {
            if (task.getType() == TaskType.PRACTICE) {
                return handlePracticeSubmission(task, user, request.getPayload(), request.isSupportUsed());
            } else {
                if (!progress.isCompleted()) {
                    // ASYNCHRONOUS COMPLETION
                    int awardedPoints = calculatePoints(task, progress, request.isSupportUsed());
                    
                    submissionProducer.sendTaskCompletion(TaskCompletionEvent.builder()
                            .userId(user.getId())
                            .taskId(task.getId())
                            .pointsAwarded(awardedPoints)
                            .supportUsed(request.isSupportUsed())
                            .timeSpentSeconds(progress.getTimeSpentSeconds())
                            .build());

                    return SubmitResponse.builder()
                            .isCorrect(true)
                            .pointsAwarded(awardedPoints)
                            .failedAttempts(progress.getFailedAttempts())
                            .feedback("Super! Das war richtig. (Punkte werden im Hintergrund verarbeitet)")
                            .build();
                } else {
                    return SubmitResponse.builder()
                            .isCorrect(true)
                            .pointsAwarded(0)
                            .failedAttempts(progress.getFailedAttempts())
                            .feedback("Korrekt! Du hast diese Aufgabe bereits gelöst.")
                            .build();
                }
            }
        }

        // Handle wrong answer
        if (!progress.isCompleted()) {
            progress.setFailedAttempts(progress.getFailedAttempts() + 1);
            
            // Lock Multiple Choice after 3 attempts
            if (task.getType() == TaskType.MULTIPLE_CHOICE && progress.getFailedAttempts() >= 3) {
                progress.setLocked(true);
            }
            
            completionRepository.save(progress);
        }

        SubmitResponse.SubmitResponseBuilder responseBuilder = SubmitResponse.builder()
                .isCorrect(false)
                .isLocked(progress.isLocked())
                .pointsAwarded(0)
                .failedAttempts(progress.getFailedAttempts())
                .feedback("Leider falsch. Tipp: " + task.getConfig().getOrDefault("hint", "Probiere es noch einmal!"));

        if (progress.getFailedAttempts() >= 3) {
            responseBuilder.correctSolution(getSolution(task));
            if (progress.isLocked()) {
                responseBuilder.feedback("Drei Fehlversuche. Diese Aufgabe wurde gesperrt.");
            }
        }

        return responseBuilder.build();
    }

    private int calculatePoints(Task task, UserTaskCompletion progress, boolean supportUsed) {
        int points = task.getPoints();
        
        // Bonus for time (if < 60s and MCQ/Fill)
        if (progress.getTimeSpentSeconds() < 60 && progress.getFailedAttempts() == 0) {
            points += 5; // Fixed bonus for speed
        }
        
        // Deduction for solution (if failed 3+ times)
        if (progress.getFailedAttempts() >= 3) {
            points = Math.max(1, points / 2);
        }

        // Additional deduction if support/help was used
        if (supportUsed) {
            points = Math.max(1, points / 2);
        }
        
        return points;
    }

    private String getSolution(Task task) {
        return switch (task.getType()) {
            case MULTIPLE_CHOICE -> "Korrekte Antworten: " + task.getConfig().get("correct");
            case FILL_BLANK -> "Lösung: " + ((List<?>) task.getConfig().get("blanks")).stream()
                    .map(b -> ((Map<?, ?>) b).get("answer").toString())
                    .reduce((a, b) -> a + ", " + b).orElse("");
            case FILL_CODE, CODE -> "Erwartete Ausgabe: " + task.getConfig().get("expectedOutput");
            case PRACTICE -> "Manuelle Prüfung erforderlich.";
        };
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

    private SubmitResponse handlePracticeSubmission(Task task, User user, Map<String, Object> payload, boolean supportUsed) {
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

        String content = "";
        if (payload != null && payload.containsKey("practiceContent")) {
            content = (String) payload.get("practiceContent");
        }

        // Sende an RabbitMQ Queue anstatt direkt in die DB zu speichern
        submissionProducer.sendPracticeSubmission(PracticeSubmissionEvent.builder()
                .userId(user.getId())
                .taskId(task.getId())
                .content(content)
                .supportUsed(supportUsed)
                .build());

        return SubmitResponse.builder()
                .isCorrect(true)
                .pointsAwarded(0)
                .status("PENDING")
                .feedback("Abgabe eingereicht! Die Lehrkraft wird sie prüfen.")
                .build();
    }

}

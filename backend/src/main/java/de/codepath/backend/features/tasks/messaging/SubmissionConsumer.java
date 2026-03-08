package de.codepath.backend.features.tasks.messaging;

import de.codepath.backend.config.RabbitMQConfig;
import de.codepath.backend.features.messaging.RealtimeUpdateService;
import de.codepath.backend.features.tasks.*;
import de.codepath.backend.features.tasks.events.PracticeSubmissionEvent;
import de.codepath.backend.features.tasks.events.TaskCompletionEvent;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionConsumer {

    private final PracticeSubmissionRepository practiceSubmissionRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final UserTaskCompletionRepository completionRepository;
    private final RealtimeUpdateService realtimeUpdateService;

    @RabbitListener(queues = RabbitMQConfig.PRACTICE_SUBMISSION_QUEUE)
    @Transactional
    public void consumePracticeSubmission(PracticeSubmissionEvent event) {
        log.info("Verarbeite asynchrone Praxis-Abgabe aus Queue: User {}, Task {}", event.getUserId(), event.getTaskId());

        userRepository.findById(event.getUserId()).ifPresent(user -> {
            taskRepository.findById(event.getTaskId()).ifPresent(task -> {
                
                // Prüfen ob bereits eine PENDING Abgabe vorliegt (Idempotenz)
                boolean hasPending = practiceSubmissionRepository.existsByUser_IdAndTask_IdAndStatus(user.getId(), task.getId(), SubmissionStatus.PENDING);
                if (!hasPending) {
                    PracticeSubmission submission = new PracticeSubmission();
                    submission.setTask(task);
                    submission.setUser(user);
                    submission.setStatus(SubmissionStatus.PENDING);
                    submission.setContent(event.getContent());
                    submission.setSupportUsed(event.isSupportUsed());
                    submission.setSubmittedAt(LocalDateTime.now());
                    practiceSubmissionRepository.save(submission);
                    log.info("Praxis-Abgabe in Datenbank gespeichert.");
                } else {
                    log.warn("Doppelte Abgabe verworfen für User {}, Task {}", user.getId(), task.getId());
                }
            });
        });
    }

    @RabbitListener(queues = RabbitMQConfig.TASK_COMPLETION_QUEUE)
    @Transactional
    public void consumeTaskCompletion(TaskCompletionEvent event) {
        log.info("Verarbeite asynchrone Aufgaben-Fertigstellung: User {}, Task {}", event.getUserId(), event.getTaskId());

        userRepository.findById(event.getUserId()).ifPresent(user -> {
            taskRepository.findById(event.getTaskId()).ifPresent(task -> {
                UserTaskCompletion completion = completionRepository.findByUser_IdAndTask_Id(user.getId(), task.getId())
                        .orElse(new UserTaskCompletion());
                
                if (completion.isCompleted()) {
                    log.warn("Aufgabe bereits als erledigt markiert für User {}, Task {}", user.getId(), task.getId());
                    return;
                }

                completion.setUser(user);
                completion.setTask(task);
                completion.setCompleted(true);
                completion.setCompletedAt(LocalDateTime.now());
                completion.setPointsAwarded(event.getPointsAwarded());
                completion.setSupportUsed(event.isSupportUsed());
                completion.setTimeSpentSeconds(event.getTimeSpentSeconds());
                completionRepository.save(completion);

                user.setTotalPoints(user.getTotalPoints() + event.getPointsAwarded());
                userRepository.save(user);

                log.info("Aufgaben-Fertigstellung gespeichert. Punkte vergeben: {}", event.getPointsAwarded());
                
                // Push Leaderboard Update
                realtimeUpdateService.publishLeaderboardUpdate();
            });
        });
    }
}

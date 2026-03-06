package de.codepath.backend.features.tasks.messaging;

import de.codepath.backend.config.RabbitMQConfig;
import de.codepath.backend.features.tasks.PracticeSubmission;
import de.codepath.backend.features.tasks.PracticeSubmissionRepository;
import de.codepath.backend.features.tasks.SubmissionStatus;
import de.codepath.backend.features.tasks.TaskRepository;
import de.codepath.backend.features.tasks.events.PracticeSubmissionEvent;
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
                    submission.setSubmittedAt(LocalDateTime.now());
                    practiceSubmissionRepository.save(submission);
                    log.info("Praxis-Abgabe in Datenbank gespeichert.");
                } else {
                    log.warn("Doppelte Abgabe verworfen für User {}, Task {}", user.getId(), task.getId());
                }
            });
        });
    }
}

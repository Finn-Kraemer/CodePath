package de.codepath.backend.features.tasks.messaging;

import de.codepath.backend.config.RabbitMQConfig;
import de.codepath.backend.features.tasks.events.PracticeSubmissionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendPracticeSubmission(PracticeSubmissionEvent event) {
        log.info("Sende Praxis-Abgabe in die Warteschlange: User {} für Task {}", event.getUserId(), event.getTaskId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.PRACTICE_ROUTING_KEY, event);
    }
}

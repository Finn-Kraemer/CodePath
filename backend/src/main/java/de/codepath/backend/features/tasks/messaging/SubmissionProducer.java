package de.codepath.backend.features.tasks.messaging;

import de.codepath.backend.config.RabbitMQConfig;
import de.codepath.backend.features.tasks.events.PracticeSubmissionEvent;
import de.codepath.backend.features.tasks.events.TaskCompletionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubmissionProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendPracticeSubmission(PracticeSubmissionEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.PRACTICE_ROUTING_KEY, event);
    }

    public void sendTaskCompletion(TaskCompletionEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.COMPLETION_ROUTING_KEY, event);
    }
}

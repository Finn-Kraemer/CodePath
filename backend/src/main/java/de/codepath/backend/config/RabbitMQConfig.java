package de.codepath.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "codepath.exchange";
    public static final String PRACTICE_SUBMISSION_QUEUE = "codepath.practice.submissions";
    public static final String TASK_COMPLETION_QUEUE = "codepath.task.completions";
    public static final String PRACTICE_ROUTING_KEY = "practice.submit";
    public static final String COMPLETION_ROUTING_KEY = "task.complete";

    @Bean
    public Queue practiceSubmissionQueue() {
        return new Queue(PRACTICE_SUBMISSION_QUEUE, true);
    }

    @Bean
    public Queue taskCompletionQueue() {
        return new Queue(TASK_COMPLETION_QUEUE, true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding practiceBinding(Queue practiceSubmissionQueue, DirectExchange exchange) {
        return BindingBuilder.bind(practiceSubmissionQueue).to(exchange).with(PRACTICE_ROUTING_KEY);
    }

    @Bean
    public Binding completionBinding(Queue taskCompletionQueue, DirectExchange exchange) {
        return BindingBuilder.bind(taskCompletionQueue).to(exchange).with(COMPLETION_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

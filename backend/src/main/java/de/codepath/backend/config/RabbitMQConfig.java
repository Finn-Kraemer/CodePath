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
    public static final String PRACTICE_ROUTING_KEY = "practice.submit";

    @Bean
    public Queue practiceSubmissionQueue() {
        return new Queue(PRACTICE_SUBMISSION_QUEUE, true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding binding(Queue practiceSubmissionQueue, DirectExchange exchange) {
        return BindingBuilder.bind(practiceSubmissionQueue).to(exchange).with(PRACTICE_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

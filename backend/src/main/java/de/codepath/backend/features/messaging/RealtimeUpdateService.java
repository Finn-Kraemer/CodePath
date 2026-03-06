package de.codepath.backend.features.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeUpdateService {

    private final RabbitTemplate rabbitTemplate;

    // RabbitMQ Topic Exchange für MQTT
    // Das mqtt-plugin nutzt standardmäßig "amq.topic"
    private static final String MQTT_EXCHANGE = "amq.topic";

    /**
     * Publiziert eine Nachricht an ein Topic, das vom Frontend via MQTT abonniert werden kann.
     * Das Routing-Key Format für das RabbitMQ MQTT Plugin ist einfach der Topic-Name.
     */
    public void publish(String topic, Object payload) {
        log.info("Publiziere Echtzeit-Update an Topic: {}", topic);
        rabbitTemplate.convertAndSend(MQTT_EXCHANGE, topic, payload);
    }

    public void publishAnnouncement(String content, String displayMode) {
        publish("codepath/announcements", Map.of(
            "content", content,
            "displayMode", displayMode,
            "updatedAt", java.time.LocalDateTime.now().toString()
        ));
    }

    public void publishLeaderboardUpdate() {
        publish("codepath/leaderboard/update", Map.of("timestamp", System.currentTimeMillis()));
    }
}

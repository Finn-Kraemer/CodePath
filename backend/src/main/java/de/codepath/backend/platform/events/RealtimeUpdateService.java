package de.codepath.backend.platform.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live-Updates per Server-Sent Events (ADR-2). Ersetzt MQTT über RabbitMQ: kein Broker,
 * keine Zugangsdaten im Browser, derselbe Login wie die übrige API.
 */
@Service
@Slf4j
public class RealtimeUpdateService {

    public static final String ANNOUNCEMENT = "announcement";
    public static final String LEADERBOARD = "leaderboard";

    // Ohne Timeout; Verbindungsabbrüche räumen die Callbacks unten auf
    private static final long NO_TIMEOUT = 0L;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        send(emitter, "connected", Map.of("timestamp", System.currentTimeMillis()));
        return emitter;
    }

    public int connectionCount() {
        return emitters.size();
    }

    public void publishAnnouncement(String content, String displayMode) {
        publish(ANNOUNCEMENT, Map.of(
                "content", content,
                "displayMode", displayMode,
                "updatedAt", LocalDateTime.now().toString()
        ));
    }

    public void publishLeaderboardUpdate() {
        publish(LEADERBOARD, Map.of("timestamp", System.currentTimeMillis()));
    }

    /**
     * Innerhalb einer Transaktion erst nach dem Commit senden – sonst lädt der Browser das
     * Leaderboard neu, bevor die Punkte sichtbar sind.
     */
    public void publish(String event, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast(event, payload);
                }
            });
        } else {
            broadcast(event, payload);
        }
    }

    // Hält Verbindungen über Proxys hinweg offen und entdeckt abgebrochene Clients
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }

    private void broadcast(String event, Object payload) {
        log.debug("Sende Live-Update '{}' an {} Clients", event, emitters.size());
        for (SseEmitter emitter : emitters) {
            send(emitter, event, payload);
        }
    }

    private void send(SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
    }
}

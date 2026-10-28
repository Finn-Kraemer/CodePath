package de.codepath.backend.platform.events;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Live-Updates per Server-Sent Events (ADR-2). Ersetzt MQTT über RabbitMQ: kein Broker,
 * keine Zugangsdaten im Browser, derselbe Login wie die übrige API.
 *
 * <p>Gesendet wird auf einem eigenen Thread: Ein langsamer oder hängender Client (schwaches WLAN,
 * eingeschlafenes Tablet) hält damit nie eine Abgabe oder einen anderen Request auf. Ein einziger
 * Sende-Thread hält die Reihenfolge der Ereignisse ein. Ranglisten-Updates werden zusammengefasst:
 * Geben 30 Schüler gleichzeitig ab, geht trotzdem nur ein „neu laden“ an alle.
 */
@Service
@Slf4j
public class RealtimeUpdateService {

    public static final String ANNOUNCEMENT = "announcement";
    public static final String LEADERBOARD = "leaderboard";

    // Ohne Timeout; Verbindungsabbrüche räumen die Callbacks unten auf
    private static final long NO_TIMEOUT = 0L;
    // Genug für Ankündigungen und Pings; Ranglisten-Updates belegen dank Zusammenfassen höchstens einen Platz
    private static final int QUEUE_CAPACITY = 1000;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicBoolean leaderboardPending = new AtomicBoolean();
    private final ExecutorService sender = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable, "codepath-live-updates");
                thread.setDaemon(true);
                return thread;
            },
            (task, executor) -> log.warn("Live-Update verworfen: Warteschlange voll"));

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        return register(emitter);
    }

    /** Für Tests: eigenen Emitter registrieren. */
    SseEmitter register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        // Die Begrüßung geht nur an den neuen Client und darf direkt gesendet werden
        send(emitter, "connected", Map.of("timestamp", System.currentTimeMillis()));
        return emitter;
    }

    public int connectionCount() {
        return emitters.size();
    }

    public void publishAnnouncement(String content, String displayMode) {
        Map<String, Object> payload = Map.of(
                "content", content,
                "displayMode", displayMode,
                "updatedAt", LocalDateTime.now().toString());
        afterCommit(() -> enqueue(() -> broadcast(ANNOUNCEMENT, payload)));
    }

    public void publishLeaderboardUpdate() {
        afterCommit(() -> {
            // Steht schon ein Ranglisten-Update in der Warteschlange, deckt es dieses mit ab
            if (!leaderboardPending.compareAndSet(false, true)) return;
            enqueue(() -> {
                leaderboardPending.set(false);
                broadcast(LEADERBOARD, Map.of("timestamp", System.currentTimeMillis()));
            });
        });
    }

    /**
     * Innerhalb einer Transaktion erst nach dem Commit senden – sonst lädt der Browser das
     * Leaderboard neu, bevor die Punkte sichtbar sind.
     */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void enqueue(Runnable task) {
        try {
            sender.execute(task);
        } catch (RejectedExecutionException e) {
            log.debug("Live-Update nach dem Herunterfahren verworfen");
        }
    }

    // Hält Verbindungen über Proxys hinweg offen und entdeckt abgebrochene Clients
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        enqueue(() -> {
            for (SseEmitter emitter : emitters) {
                trySend(emitter, () -> SseEmitter.event().comment("ping"));
            }
        });
    }

    private void broadcast(String event, Object payload) {
        log.debug("Sende Live-Update '{}' an {} Clients", event, emitters.size());
        for (SseEmitter emitter : emitters) {
            send(emitter, event, payload);
        }
    }

    private void send(SseEmitter emitter, String event, Object payload) {
        trySend(emitter, () -> SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
    }

    private void trySend(SseEmitter emitter, Supplier<SseEmitter.SseEventBuilder> event) {
        try {
            emitter.send(event.get());
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        } catch (RuntimeException e) {
            // Ein einzelner kaputter Client darf den Sende-Thread nicht beenden
            emitters.remove(emitter);
            log.debug("Live-Update an einen Client fehlgeschlagen: {}", e.toString());
        }
    }

    /** Für Tests: wartet, bis alles bisher Eingereihte gesendet ist. */
    void awaitIdle(Duration timeout) throws Exception {
        sender.submit(() -> { }).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        sender.shutdownNow();
    }
}

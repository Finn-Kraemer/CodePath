package de.codepath.backend.platform.events;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Live-Updates per Server-Sent Events (ADR-2). Ersetzt MQTT über RabbitMQ: kein Broker,
 * keine Zugangsdaten im Browser, derselbe Login wie die übrige API.
 *
 * <p>Jede Verbindung hat eine eigene kleine Warteschlange, die ein virtueller Thread abarbeitet:
 * Ein langsamer oder hängender Client (schwaches WLAN, eingeschlafenes Tablet) hält damit weder eine
 * Abgabe noch die anderen Clients auf. Die Reihenfolge der Ereignisse bleibt je Verbindung erhalten.
 * Läuft die Warteschlange einer Verbindung über, wird sie geschlossen; der Browser verbindet sich neu.
 *
 * <p>Ranglisten-Updates sind das häufigste Ereignis und lösen im Browser Neuladen aus. Sie werden darum
 * <ul>
 *   <li>gedrosselt: höchstens eins je {@code codepath.live.leaderboard-interval} (Standard 2 s), das erste sofort,</li>
 *   <li>je Verbindung zusammengefasst: Wartet dort schon eins, kommt kein zweites dazu,</li>
 *   <li>auf den Kurs beschränkt: Punkte in Kurs A laden nicht die Seiten von Kurs B neu (Lehrkräfte sehen alle).</li>
 * </ul>
 * So bleibt die Last auch mit mehreren Klassen gleichzeitig begrenzt.
 */
@Service
@Slf4j
public class RealtimeUpdateService {

    public static final String ANNOUNCEMENT = "announcement";
    public static final String LEADERBOARD = "leaderboard";
    public static final String CODEPATH = "codepath";
    public static final String HELP = "help";
    public static final String QUIZ = "quiz";

    // Ohne Timeout; Verbindungsabbrüche räumen die Callbacks unten auf
    private static final long NO_TIMEOUT = 0L;
    /** Mehr unversandte Ereignisse für eine Verbindung = Client hängt; sie wird geschlossen. */
    static final int MAILBOX_CAPACITY = 64;
    static final Duration DEFAULT_LEADERBOARD_INTERVAL = Duration.ofSeconds(2);

    private record Outgoing(Supplier<SseEmitter.SseEventBuilder> event, boolean leaderboard) {
    }

    /** Verbindung mit dem Konto, das sie geöffnet hat – für Ereignisse, die nicht alle sehen dürfen. */
    private final class Client {
        final SseEmitter emitter;
        final String username;
        final boolean teacher;
        final Queue<Outgoing> queue = new ConcurrentLinkedQueue<>();
        final AtomicInteger queued = new AtomicInteger();
        final AtomicBoolean draining = new AtomicBoolean();
        final AtomicBoolean leaderboardQueued = new AtomicBoolean();
        volatile boolean closed;

        Client(SseEmitter emitter, String username, boolean teacher) {
            this.emitter = emitter;
            this.username = username;
            this.teacher = teacher;
        }

        void offer(Outgoing outgoing) {
            if (closed) return;
            // Steht schon ein Ranglisten-Update für diese Verbindung an, deckt es dieses mit ab
            if (outgoing.leaderboard() && !leaderboardQueued.compareAndSet(false, true)) return;
            if (queued.incrementAndGet() > MAILBOX_CAPACITY) {
                log.debug("Live-Verbindung von {} hängt – wird geschlossen", username);
                close(true);
                return;
            }
            queue.add(outgoing);
            startDrain();
        }

        private void startDrain() {
            if (!draining.compareAndSet(false, true)) return;
            try {
                senders.execute(this::drain);
            } catch (RejectedExecutionException e) {
                draining.set(false);
                log.debug("Live-Update nach dem Herunterfahren verworfen");
            }
        }

        private void drain() {
            while (true) {
                Outgoing next;
                while (!closed && (next = queue.poll()) != null) {
                    queued.decrementAndGet();
                    if (next.leaderboard()) leaderboardQueued.set(false);
                    if (!trySend(this, next.event())) return;
                }
                draining.set(false);
                // Ist während des Abschaltens noch etwas hinzugekommen, weiterarbeiten
                if (closed || queue.isEmpty() || !draining.compareAndSet(false, true)) return;
            }
        }

        boolean idle() {
            return closed || (queue.isEmpty() && !draining.get());
        }

        void close(boolean completeEmitter) {
            closed = true;
            clients.remove(this);
            queue.clear();
            if (completeEmitter) {
                try {
                    emitter.complete();
                } catch (RuntimeException ignored) {
                    // Verbindung ist ohnehin weg
                }
            }
        }
    }

    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();
    private final ExecutorService senders = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("codepath-live-updates-", 0).factory());
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "codepath-live-updates-timer");
        thread.setDaemon(true);
        return thread;
    });

    private final ObjectProvider<LeaderboardAudience> audience;
    private final long leaderboardIntervalNanos;
    private final AtomicBoolean leaderboardForAll = new AtomicBoolean();
    private final Set<Long> leaderboardCourses = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean leaderboardScheduled = new AtomicBoolean();
    private volatile long lastLeaderboardNanos = System.nanoTime() - Duration.ofHours(1).toNanos();

    @Autowired
    public RealtimeUpdateService(ObjectProvider<LeaderboardAudience> audience,
                                 @Value("${codepath.live.leaderboard-interval:2s}") Duration leaderboardInterval) {
        this.audience = audience;
        this.leaderboardIntervalNanos = Math.max(0, leaderboardInterval.toNanos());
    }

    /** Für Tests: ohne Kurs-Auflösung (Ranglisten-Updates gehen an alle). */
    RealtimeUpdateService() {
        this((LeaderboardAudience) null, DEFAULT_LEADERBOARD_INTERVAL);
    }

    RealtimeUpdateService(LeaderboardAudience audience, Duration leaderboardInterval) {
        this(new StaticProvider(audience), leaderboardInterval);
    }

    /**
     * @param username angemeldetes Konto (für Ereignisse an einzelne Personen)
     * @param teacher  Lehrkraft? Nur diese Verbindungen bekommen z. B. Hilfe-Rufe der Klasse.
     */
    public SseEmitter subscribe(String username, boolean teacher) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        return register(emitter, username, teacher);
    }

    /** Für Tests: eigenen Emitter ohne Konto registrieren. */
    SseEmitter register(SseEmitter emitter) {
        return register(emitter, null, false);
    }

    SseEmitter register(SseEmitter emitter, String username, boolean teacher) {
        Client client = new Client(emitter, username, teacher);
        emitter.onCompletion(() -> client.close(false));
        emitter.onTimeout(() -> client.close(false));
        emitter.onError(e -> client.close(false));
        // Die Begrüßung geht nur an den neuen Client und darf direkt gesendet werden
        // Gestreute Wartezeit für das automatische Neuverbinden des Browsers: Nach einem Neustart des
        // Servers kommen so nicht alle Klassen im selben Augenblick zurück
        long retryMillis = 2000 + ThreadLocalRandom.current().nextLong(4000);
        Supplier<SseEmitter.SseEventBuilder> hello = () -> SseEmitter.event().name("connected").reconnectTime(retryMillis)
                .data(Map.of("timestamp", System.currentTimeMillis()), MediaType.APPLICATION_JSON);
        if (trySend(client, hello)) {
            clients.add(client);
        }
        return emitter;
    }

    public int connectionCount() {
        return clients.size();
    }

    public void publishAnnouncement(String content, String displayMode) {
        Map<String, Object> payload = Map.of(
                "content", content,
                "displayMode", displayMode,
                "updatedAt", LocalDateTime.now().toString());
        afterCommit(() -> fanOut(c -> true, ANNOUNCEMENT, payload));
    }

    /** Rangliste aller Kurse hat sich geändert. */
    public void publishLeaderboardUpdate() {
        publishLeaderboardUpdate(null);
    }

    /**
     * Rangliste eines Kurses hat sich geändert: Das Update geht nur an dessen Mitglieder und an Lehrkräfte.
     *
     * @param courseId Kurs oder null für alle
     */
    public void publishLeaderboardUpdate(Long courseId) {
        afterCommit(() -> {
            if (courseId == null) leaderboardForAll.set(true);
            else leaderboardCourses.add(courseId);
            scheduleLeaderboard();
        });
    }

    /** Hinweis zum CodePath an eine Person (und die Lehrkräfte); die Rangliste kann sich dadurch ändern. */
    public void publishCodePathUpdate(String username, String message) {
        Map<String, Object> payload = Map.of(
                "username", username == null ? "" : username,
                "message", message == null ? "" : message,
                "timestamp", System.currentTimeMillis());
        afterCommit(() -> fanOut(c -> c.teacher || (username != null && username.equals(c.username)), CODEPATH, payload));
        publishLeaderboardUpdate();
    }

    /** An alle Verbindungen (nichts Vertrauliches im Inhalt!). */
    public void publishToAll(String event, Map<String, Object> payload) {
        afterCommit(() -> fanOut(c -> true, event, payload));
    }

    /** Nur an Verbindungen von Lehrkräften, nie an Schüler (z. B. Hilfe-Rufe). */
    public void publishToTeachers(String event, Map<String, Object> payload) {
        afterCommit(() -> fanOut(c -> c.teacher, event, payload));
    }

    /** Nur an die Verbindungen dieser Konten. */
    public void publishToUsers(Collection<String> usernames, String event, Map<String, Object> payload) {
        if (usernames.isEmpty()) return;
        Set<String> recipients = Set.copyOf(usernames);
        afterCommit(() -> fanOut(c -> c.username != null && recipients.contains(c.username), event, payload));
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

    private void scheduleLeaderboard() {
        if (!leaderboardScheduled.compareAndSet(false, true)) return;
        long delay = Math.max(0, lastLeaderboardNanos + leaderboardIntervalNanos - System.nanoTime());
        try {
            timer.schedule(this::flushLeaderboard, delay, TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException e) {
            leaderboardScheduled.set(false);
            log.debug("Ranglisten-Update nach dem Herunterfahren verworfen");
        }
    }

    private void flushLeaderboard() {
        lastLeaderboardNanos = System.nanoTime();
        leaderboardScheduled.set(false);
        boolean all = leaderboardForAll.getAndSet(false);
        Set<Long> courses = new HashSet<>();
        for (Iterator<Long> it = leaderboardCourses.iterator(); it.hasNext(); ) {
            courses.add(it.next());
            it.remove();
        }
        if (!all && courses.isEmpty()) return;
        Predicate<Client> recipients = c -> true;
        if (!all) {
            Set<String> members = members(courses);
            if (members != null) recipients = c -> c.teacher || c.username == null || members.contains(c.username);
        }
        Map<String, Object> payload = Map.of("timestamp", System.currentTimeMillis());
        fanOut(recipients, LEADERBOARD, payload, true);
    }

    /** null = nicht auflösbar, dann an alle */
    private Set<String> members(Set<Long> courses) {
        LeaderboardAudience resolver = audience == null ? null : audience.getIfAvailable();
        if (resolver == null) return null;
        try {
            return resolver.members(courses);
        } catch (RuntimeException e) {
            log.debug("Kursmitglieder für Ranglisten-Update nicht ermittelbar: {}", e.toString());
            return null;
        }
    }

    // Hält Verbindungen über Proxys hinweg offen und entdeckt abgebrochene oder hängende Clients
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        Outgoing ping = new Outgoing(() -> SseEmitter.event().comment("ping"), false);
        fanOut(c -> true, ping);
    }

    private void fanOut(Predicate<Client> recipients, String event, Object payload) {
        fanOut(recipients, event, payload, false);
    }

    private void fanOut(Predicate<Client> recipients, String event, Object payload, boolean leaderboard) {
        log.debug("Sende Live-Update '{}'", event);
        fanOut(recipients, new Outgoing(event(event, payload), leaderboard));
    }

    // Synchronisiert, damit alle Verbindungen die Ereignisse in derselben Reihenfolge bekommen
    private synchronized void fanOut(Predicate<Client> recipients, Outgoing outgoing) {
        for (Client client : clients) {
            if (recipients.test(client)) client.offer(outgoing);
        }
    }

    private static Supplier<SseEmitter.SseEventBuilder> event(String event, Object payload) {
        return () -> SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON);
    }

    /** @return false, wenn die Verbindung weg ist (sie wird dann entfernt) */
    private boolean trySend(Client client, Supplier<SseEmitter.SseEventBuilder> event) {
        try {
            client.emitter.send(event.get());
            return true;
        } catch (IOException | IllegalStateException e) {
            client.close(false);
        } catch (RuntimeException e) {
            // Ein einzelner kaputter Client darf das Senden an andere nicht stören
            client.close(false);
            log.debug("Live-Update an einen Client fehlgeschlagen: {}", e.toString());
        }
        return false;
    }

    /** Für Tests: wartet, bis alles bisher Eingereihte (auch gedrosselte Ranglisten-Updates) gesendet ist. */
    void awaitIdle(Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            boolean pending = leaderboardScheduled.get() || leaderboardForAll.get() || !leaderboardCourses.isEmpty();
            if (!pending && clients.stream().allMatch(Client::idle)) return;
            Thread.sleep(10);
        }
        throw new TimeoutException("Live-Updates nach " + timeout + " noch nicht versandt");
    }

    @PreDestroy
    void shutdown() {
        timer.shutdownNow();
        senders.shutdownNow();
    }

    /** Minimaler ObjectProvider für die Test-Konstruktoren. */
    private record StaticProvider(LeaderboardAudience value) implements ObjectProvider<LeaderboardAudience> {
        @Override
        public LeaderboardAudience getObject() {
            return value;
        }

        @Override
        public LeaderboardAudience getIfAvailable() {
            return value;
        }
    }
}

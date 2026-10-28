package de.codepath.backend.platform.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RealtimeUpdateServiceTest {

    final RealtimeUpdateService service = new RealtimeUpdateService();

    /** Merkt sich die Namen der gesendeten Ereignisse; kann bremsen oder blockieren. */
    static class Client extends SseEmitter {
        final List<String> events = new CopyOnWriteArrayList<>();
        volatile long delayMillis;
        volatile CountDownLatch gate;
        volatile boolean broken;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            String text = builder.build().stream().map(d -> String.valueOf(d.getData())).reduce("", String::concat);
            if (broken && !text.contains("event:connected")) throw new IOException("Verbindung weg");
            try {
                if (gate != null && !text.contains("event:connected")) gate.await(5, TimeUnit.SECONDS);
                if (delayMillis > 0 && !text.contains("event:connected")) Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            int start = text.indexOf("event:");
            events.add(start < 0 ? "comment" : text.substring(start + 6, text.indexOf('\n', start)));
        }

        long count(String event) {
            return events.stream().filter(event::equals).count();
        }
    }

    @AfterEach
    void stop() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        service.shutdown();
    }

    @Test
    void slowClientDoesNotBlockTheCaller() throws Exception {
        Client slow = new Client();
        slow.delayMillis = 1500;
        Client fast = new Client();
        service.register(slow);
        service.register(fast);

        long start = System.nanoTime();
        service.publishAnnouncement("Pause bis 10 Uhr", "BANNER");
        service.publishLeaderboardUpdate();
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertTrue(millis < 200, "Aufrufer wartete " + millis + " ms auf einen langsamen Client");

        service.awaitIdle(Duration.ofSeconds(10));
        for (Client client : List.of(slow, fast)) {
            assertEquals(List.of("connected", "announcement", "leaderboard"), client.events, "Reihenfolge bleibt erhalten");
        }
    }

    @Test
    void leaderboardUpdatesAreCoalesced() throws Exception {
        Client client = new Client();
        client.gate = new CountDownLatch(1);
        service.register(client);

        // Der erste Versand hängt am Client fest; währenddessen geben 30 Schüler ab
        service.publishAnnouncement("Start", "BANNER");
        for (int i = 0; i < 30; i++) service.publishLeaderboardUpdate();
        client.gate.countDown();
        service.awaitIdle(Duration.ofSeconds(10));

        assertEquals(1, client.count("announcement"));
        assertEquals(1, client.count("leaderboard"), "30 Abgaben, ein Neuladen");

        // Danach kommen neue Updates wieder an
        service.publishLeaderboardUpdate();
        service.awaitIdle(Duration.ofSeconds(10));
        assertEquals(2, client.count("leaderboard"));
    }

    @Test
    void brokenClientsAreDroppedOthersStillReceive() throws Exception {
        Client broken = new Client();
        broken.broken = true;
        Client healthy = new Client();
        service.register(broken);
        service.register(healthy);
        assertEquals(2, service.connectionCount());

        service.publishAnnouncement("Hallo", "BANNER");
        service.awaitIdle(Duration.ofSeconds(10));
        assertEquals(1, service.connectionCount());
        assertEquals(1, healthy.count("announcement"));
    }

    @Test
    void sendsOnlyAfterCommit() throws Exception {
        Client client = new Client();
        service.register(client);
        TransactionSynchronizationManager.initSynchronization();
        service.publishLeaderboardUpdate();
        service.awaitIdle(Duration.ofSeconds(10));
        assertEquals(0, client.count("leaderboard"), "Vor dem Commit sieht der Browser die Punkte noch nicht");

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        service.awaitIdle(Duration.ofSeconds(10));
        assertEquals(1, client.count("leaderboard"));
    }
}

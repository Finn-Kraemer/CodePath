package de.codepath.backend.identity;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login-Sperre: nach 10 Fehlversuchen innerhalb einer Minute je Nutzername werden weitere Versuche für
 * den Rest der Minute abgelehnt. Je IP-Adresse liegt die Grenze höher, weil eine ganze Klasse über
 * dieselbe Schul-IP kommt. Im Speicher, da es nur eine Instanz gibt.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 10;
    static final int MAX_FAILURES_PER_IP = 100;
    static final long WINDOW_MILLIS = 60_000;

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String username, String ip) {
        return count("u:" + normalize(username)) >= MAX_FAILURES || count("ip:" + ip) >= MAX_FAILURES_PER_IP;
    }

    public void recordFailure(String username, String ip) {
        add("u:" + normalize(username));
        add("ip:" + ip);
    }

    public void recordSuccess(String username) {
        failures.remove("u:" + normalize(username));
    }

    private int count(String key) {
        Deque<Long> times = failures.get(key);
        if (times == null) return 0;
        synchronized (times) {
            prune(times);
            return times.size();
        }
    }

    private void add(String key) {
        Deque<Long> times = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            prune(times);
            times.addLast(clock.millis());
        }
        // Speicher klein halten
        if (failures.size() > 10_000) failures.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private void prune(Deque<Long> times) {
        long cutoff = clock.millis() - WINDOW_MILLIS;
        while (!times.isEmpty() && times.peekFirst() < cutoff) times.removeFirst();
    }

    private static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase();
    }
}

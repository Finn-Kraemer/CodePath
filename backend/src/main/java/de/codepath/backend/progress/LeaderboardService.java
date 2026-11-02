package de.codepath.backend.progress;

import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rangliste innerhalb eines Kurses; die Lehrkraft kann einen Kurs wählen oder alle sehen.
 *
 * <p>Nach jeder Punktebuchung laden alle Browser eines Kurses die Rangliste neu. Die Rangfolge je Kurs
 * wird darum kurz zwischengespeichert: Sie verfällt mit jeder Punktebuchung ({@link PointsService#generation()}) und
 * spätestens nach {@link #MAX_AGE} (neue Mitglieder, geänderte Anzeigenamen). 30 Browser = eine Berechnung.
 */
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    static final int MAX_ENTRIES = 15;
    static final Duration MAX_AGE = Duration.ofSeconds(5);
    private static final int MAX_CACHED_SCOPES = 256;

    private final UserService userService;
    private final PointsService pointsService;

    private record Ranking(long generation, long createdAt, List<TaskViews.LeaderboardEntry> entries) {
    }

    private final Map<Long, Ranking> cache = new ConcurrentHashMap<>();

    public TaskViews.Leaderboard leaderboard(User viewer, Long requestedCourseId) {
        List<TaskViews.LeaderboardEntry> ranked;
        if (viewer.isTeacher()) {
            ranked = requestedCourseId != null ? courseRanking(requestedCourseId) : allStudentsRanking();
        } else {
            Long courseId = userService.courseIdOf(viewer.getId());
            ranked = courseId != null ? courseRanking(courseId) : rank(List.of(viewer));
        }
        TaskViews.LeaderboardEntry own = ranked.stream().filter(e -> e.username().equals(viewer.getUsername())).findFirst()
                .orElseGet(() -> new TaskViews.LeaderboardEntry(viewer.getUsername(), viewer.getVisibleName(), pointsService.total(viewer.getId()), 0));
        return new TaskViews.Leaderboard(ranked.subList(0, Math.min(3, ranked.size())),
                ranked.subList(0, Math.min(MAX_ENTRIES, ranked.size())), own);
    }

    // Schlüssel: Kurs-ID, -1 für „alle Schüler“
    private List<TaskViews.LeaderboardEntry> courseRanking(Long courseId) {
        return cached(courseId, () -> rank(userService.byIds(userService.memberIds(courseId))));
    }

    private List<TaskViews.LeaderboardEntry> allStudentsRanking() {
        return cached(-1L, () -> rank(userService.students()));
    }

    private List<TaskViews.LeaderboardEntry> cached(Long key, java.util.function.Supplier<List<TaskViews.LeaderboardEntry>> compute) {
        long gen = pointsService.generation();
        long now = System.nanoTime();
        Ranking hit = cache.get(key);
        if (hit != null && hit.generation() == gen && now - hit.createdAt() < MAX_AGE.toNanos()) return hit.entries();
        List<TaskViews.LeaderboardEntry> entries = List.copyOf(compute.get());
        if (cache.size() >= MAX_CACHED_SCOPES) cache.clear();
        cache.put(key, new Ranking(gen, now, entries));
        return entries;
    }

    private List<TaskViews.LeaderboardEntry> rank(List<User> members) {
        Map<Long, Long> totals = pointsService.totals(members.stream().map(User::getId).toList());
        List<User> users = new ArrayList<>(members);
        users.sort(Comparator.<User>comparingLong(u -> -totals.getOrDefault(u.getId(), 0L)).thenComparing(User::getUsername));

        List<TaskViews.LeaderboardEntry> ranked = new ArrayList<>(users.size());
        long previous = Long.MIN_VALUE;
        int rank = 0;
        for (int i = 0; i < users.size(); i++) {
            long points = totals.getOrDefault(users.get(i).getId(), 0L);
            if (points != previous) rank = i + 1; // Gleichstand = gleicher Platz
            previous = points;
            ranked.add(new TaskViews.LeaderboardEntry(users.get(i).getUsername(), users.get(i).getVisibleName(), points, rank));
        }
        return ranked;
    }
}

package de.codepath.backend.progress;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.CourseService;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/** Rangliste innerhalb eines Kurses; die Lehrkraft kann einen Kurs wählen oder alle sehen. */
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    static final int MAX_ENTRIES = 15;

    private final UserService userService;
    private final PointsService pointsService;
    private final CourseService courseService;

    public TaskViews.Leaderboard leaderboard(User viewer, Long requestedCourseId) {
        List<Long> ids;
        java.time.LocalDateTime since = null;
        if (viewer.isTeacher()) {
            ids = requestedCourseId != null ? userService.memberIds(requestedCourseId)
                    : userService.students().stream().map(User::getId).toList();
            if (requestedCourseId != null) since = courseService.get(requestedCourseId).getLeaderboardSince();
        } else {
            Optional<Course> course = userService.courseOf(viewer.getId());
            ids = course.map(Course::getId).map(userService::memberIds).orElse(List.of(viewer.getId()));
            since = course.map(Course::getLeaderboardSince).orElse(null);
        }
        // Rangliste neu gestartet (z. B. nach der Übernahme ins nächste Schuljahr): nur neue Punkte zählen
        Map<Long, Long> totals = pointsService.totalsSince(ids, since);
        List<User> users = new ArrayList<>();
        for (Long id : ids) userService.byId(id).ifPresent(users::add);
        users.sort(Comparator.<User>comparingLong(u -> -totals.getOrDefault(u.getId(), 0L)).thenComparing(User::getUsername));

        List<TaskViews.LeaderboardEntry> ranked = new ArrayList<>();
        long previous = Long.MIN_VALUE;
        int rank = 0;
        for (int i = 0; i < users.size(); i++) {
            long points = totals.getOrDefault(users.get(i).getId(), 0L);
            if (points != previous) rank = i + 1; // Gleichstand = gleicher Platz
            previous = points;
            ranked.add(new TaskViews.LeaderboardEntry(users.get(i).getUsername(), users.get(i).getVisibleName(), points, rank));
        }
        TaskViews.LeaderboardEntry own = ranked.stream().filter(e -> e.username().equals(viewer.getUsername())).findFirst()
                .orElse(new TaskViews.LeaderboardEntry(viewer.getUsername(), viewer.getVisibleName(),
                        pointsService.totalsSince(List.of(viewer.getId()), since).getOrDefault(viewer.getId(), 0L), 0));
        return new TaskViews.Leaderboard(ranked.subList(0, Math.min(3, ranked.size())),
                ranked.subList(0, Math.min(MAX_ENTRIES, ranked.size())), own);
    }
}

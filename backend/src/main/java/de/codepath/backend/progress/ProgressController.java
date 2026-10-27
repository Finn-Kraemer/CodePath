package de.codepath.backend.progress;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ProgressController {

    private final LeaderboardService leaderboardService;
    private final UserService userService;
    private final PointsService pointsService;
    private final ProgressRepository progressRepository;

    @GetMapping("/api/leaderboard")
    public TaskViews.Leaderboard leaderboard(@RequestParam(name = "courseId", required = false) Long courseId) {
        return leaderboardService.leaderboard(userService.currentUser(), courseId);
    }

    /** Eigenes Profil; die Lehrkraft kann mit ?username= jedes Profil abrufen. */
    @GetMapping("/api/users/profile")
    public TaskViews.Profile profile(@RequestParam(name = "username", required = false) String username) {
        User requester = userService.currentUser();
        User target = username != null && requester.isTeacher() ? userService.byUsername(username) : requester;
        return new TaskViews.Profile(target.getUsername(), target.getVisibleName(), target.getRole().name(),
                userService.courseOf(target.getId()).map(Course::getName).orElse(null),
                pointsService.total(target.getId()),
                progressRepository.countByUserIdAndStatus(target.getId(), ProgressStatus.SOLVED),
                progressRepository.countByUserIdAndStatus(target.getId(), ProgressStatus.PENDING_REVIEW));
    }
}

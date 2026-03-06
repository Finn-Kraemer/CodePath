package de.codepath.backend.features.leaderboard;

import de.codepath.backend.users.User;
import de.codepath.backend.users.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final UserService userService;

    @GetMapping
    public LeaderboardResponse getLeaderboard() {
        User user = userService.getCurrentUser();
        return leaderboardService.getLeaderboard(user);
    }
}

package de.codepath.backend.features.leaderboard;

import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final UserRepository userRepository;

    public LeaderboardResponse getLeaderboard(User currentUser) {
        List<User> topUsers = userRepository.findAll(
                PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "totalPoints", "username"))
        ).getContent();

        List<LeaderboardEntry> topThree = IntStream.range(0, topUsers.size())
                .mapToObj(i -> mapToEntry(topUsers.get(i), i + 1))
                .collect(Collectors.toList());

        // Simple rank calculation for current user (this is fine for ~30 students)
        // In a large system, we'd use a more efficient query
        List<User> allSorted = userRepository.findAll(Sort.by(Sort.Direction.DESC, "totalPoints", "username"));
        int rank = 0;
        for (int i = 0; i < allSorted.size(); i++) {
            if (allSorted.get(i).getId().equals(currentUser.getId())) {
                rank = i + 1;
                break;
            }
        }

        return LeaderboardResponse.builder()
                .topThree(topThree)
                .ownEntry(mapToEntry(currentUser, rank))
                .build();
    }

    private LeaderboardEntry mapToEntry(User user, int rank) {
        return LeaderboardEntry.builder()
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .points(user.getTotalPoints())
                .rank(rank)
                .build();
    }
}

package de.codepath.backend.features.leaderboard;

import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRepository;
import de.codepath.backend.users.UserRole;
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
        List<User> topUsers = userRepository.findAllByRoleNot(
                UserRole.ADMIN,
                PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "totalPoints", "username"))
        ).getContent();

        List<LeaderboardEntry> topThree = IntStream.range(0, topUsers.size())
                .mapToObj(i -> mapToEntry(topUsers.get(i), i + 1))
                .collect(Collectors.toList());

        // Filter out admins for ranking
        List<User> allStudentsSorted = userRepository.findAllByRoleNot(UserRole.ADMIN, Sort.by(Sort.Direction.DESC, "totalPoints", "username"));
        int rank = 0;
        for (int i = 0; i < allStudentsSorted.size(); i++) {
            if (allStudentsSorted.get(i).getId().equals(currentUser.getId())) {
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

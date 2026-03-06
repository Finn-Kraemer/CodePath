package de.codepath.backend.features.leaderboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LeaderboardResponse {
    private List<LeaderboardEntry> topThree;
    private LeaderboardEntry ownEntry;
}

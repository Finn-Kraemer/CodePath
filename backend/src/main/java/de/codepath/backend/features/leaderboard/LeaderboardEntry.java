package de.codepath.backend.features.leaderboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LeaderboardEntry {
    private String username;
    private String displayName;
    private int points;
    private int rank;
}

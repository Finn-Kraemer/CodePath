package de.codepath.backend.users;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfileResponse {
    private String username;
    private String displayName;
    private String role;
    private int totalPoints;
    private long completedTasksCount;
    private long pendingSubmissionsCount;
}

package de.codepath.backend.features.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StudentOverviewResponse {
    private String username;
    private String displayName;
    private int totalPoints;
    private long completedTasks;
}

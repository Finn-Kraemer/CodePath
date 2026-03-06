package de.codepath.backend.features.tasks;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmitResponse {
    @JsonProperty("isCorrect")
    private boolean isCorrect;
    private Integer pointsAwarded;
    private String status; // For PRACTICE (PENDING, APPROVED, REJECTED)
    private String feedback;
    private Integer failedAttempts;
    private String correctSolution;
}

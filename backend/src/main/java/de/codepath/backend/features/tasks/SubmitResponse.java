package de.codepath.backend.features.tasks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmitResponse {
    private boolean isCorrect;
    private int pointsAwarded;
    private String status; // For PRACTICE (PENDING, APPROVED)
    private String feedback;
}

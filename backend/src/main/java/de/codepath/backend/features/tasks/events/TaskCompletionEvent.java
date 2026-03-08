package de.codepath.backend.features.tasks.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCompletionEvent implements Serializable {
    private Long userId;
    private Long taskId;
    private int pointsAwarded;
    private boolean supportUsed;
    private long timeSpentSeconds;
}

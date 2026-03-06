package de.codepath.backend.features.tasks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmitRequest {
    private Map<String, Object> payload;
    private Long timeSpentSeconds;
}

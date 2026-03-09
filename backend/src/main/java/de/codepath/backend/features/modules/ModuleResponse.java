package de.codepath.backend.features.modules;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModuleResponse {
    private Long id;
    private String slug;
    private String title;
    private String description;
    private String iconEmoji;
    
    @JsonProperty("isUnlocked")
    private boolean isUnlocked;

    private java.time.LocalDateTime availableUntil;
    
    private long totalTasks;
    private long completedTasks;
}

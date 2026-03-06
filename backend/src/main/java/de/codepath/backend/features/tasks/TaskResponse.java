package de.codepath.backend.features.tasks;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskResponse {
    private Long id;
    private String slug;
    private String title;
    private String story;
    private String description;
    private String type;
    private String difficulty;
    private int points;
    private Map<String, Object> config;
    
    @JsonProperty("isCompleted")
    private boolean isCompleted;
    
    private String submissionStatus;
    
    private String moduleTitle;
}

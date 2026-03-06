package de.codepath.backend.features.tasks;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserTaskCompletionId implements Serializable {
    private Long user;
    private Long task;
}

package de.codepath.backend;

import de.codepath.backend.features.tasks.Task;
import de.codepath.backend.features.tasks.TaskConfigFilter;
import de.codepath.backend.features.tasks.TaskType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TaskConfigFilterTest {

    private static final Set<String> SOLUTION_FIELDS = Set.of(
            "correct", "blanks", "expectedOutput", "solutionSql", "schemaSql", "correctOrder", "expectedState", "hint", "support");

    private static Task task(TaskType type, Map<String, Object> config) {
        Task task = new Task();
        task.setType(type);
        task.setConfig(config);
        return task;
    }

    @Test
    void shouldNeverExposeSolutionFields() {
        Map<String, Object> everything = Map.of(
                "options", List.of("a", "b"), "template", "___", "starterCode", "", "starterSql", "",
                "initialFs", List.of(), "instructions", "", "items", List.of("x", "y"),
                "correct", List.of(1), "blanks", List.of("a"), "expectedOutput", "42");
        Map<String, Object> withSolutions = new java.util.HashMap<>(everything);
        withSolutions.putAll(Map.of("solutionSql", "SELECT 1", "schemaSql", "CREATE TABLE t(x INT)",
                "correctOrder", List.of(0, 1), "expectedState", "a,b", "hint", "tipp", "support", "hilfe"));

        for (TaskType type : TaskType.values()) {
            Map<String, Object> publicConfig = TaskConfigFilter.publicConfig(task(type, withSolutions), 1L);
            for (String field : SOLUTION_FIELDS) {
                assertFalse(publicConfig.containsKey(field), type + " gibt " + field + " preis");
            }
        }
    }

    @Test
    void shouldShuffleSortingItemsAwayFromSolution() {
        List<String> items = List.of("eins", "zwei", "drei", "vier", "fünf");
        Task sorting = task(TaskType.SORTING, Map.of("items", items, "correctOrder", List.of(0, 1, 2, 3, 4)));

        for (long seed = 0; seed < 50; seed++) {
            List<?> shuffled = (List<?>) TaskConfigFilter.publicConfig(sorting, seed).get("items");
            List<?> ids = shuffled.stream().map(item -> ((Map<?, ?>) item).get("id")).toList();
            assertEquals(5, ids.size());
            assertNotEquals(List.of(0, 1, 2, 3, 4), ids);
        }
        // Gleicher Seed -> gleiche Reihenfolge (stabil beim Neuladen)
        assertEquals(TaskConfigFilter.publicConfig(sorting, 7L), TaskConfigFilter.publicConfig(sorting, 7L));
    }
}

package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Aufgaben umsortieren: gilt sofort für Schüler und übersteht den Import der mitgelieferten Inhalte. */
class TaskOrderTest extends IntegrationTestBase {

    List<Long> order(long moduleId) {
        return jdbc.queryForList("select id from tasks where module_id = ? order by sort_order, id", Long.class, moduleId);
    }

    List<String> studentSlugs(User user, String module) throws Exception {
        List<String> slugs = new ArrayList<>();
        for (JsonNode t : body(getAs(as(user), "/api/modules/" + module)).get("tasks")) slugs.add(t.get("slug").asText());
        return slugs;
    }

    @Test
    void reorderTasksAndKeepOrderOnBundledImport() throws Exception {
        long moduleId = contentService.module("git").getId();
        List<Long> original = order(moduleId);
        Course course = course("8a");
        User lena = student("lena", course);
        unlock(course, "git");
        try {
            // Aufgabe 1 ans Ende ziehen
            List<Long> moved = new ArrayList<>(original.subList(1, original.size()));
            moved.add(original.getFirst());
            putAs(teacher(), "/api/admin/content/modules/" + moduleId + "/order", Map.of("taskIds", moved)).andExpect(status().isOk());
            assertEquals(moved, order(moduleId));
            List<String> slugs = studentSlugs(lena, "git");
            assertEquals("g1", slugs.getLast());
            assertEquals("g3", slugs.getFirst());

            int versions = jdbc.queryForObject("select count(*) from task_versions", Integer.class);
            postAs(teacher(), "/api/admin/content/import-bundled", null).andExpect(status().isOk());
            assertEquals(moved, order(moduleId), "Import überschreibt die Reihenfolge der Lehrkraft");
            assertEquals(versions, jdbc.queryForObject("select count(*) from task_versions", Integer.class),
                    "Nur die Reihenfolge geändert – keine neue Version");

            // Unvollständige oder fremde Listen werden abgelehnt
            putAs(teacher(), "/api/admin/content/modules/" + moduleId + "/order", Map.of("taskIds", moved.subList(1, moved.size())))
                    .andExpect(status().isBadRequest());
            List<Long> duplicate = new ArrayList<>(moved);
            duplicate.set(0, duplicate.get(1));
            putAs(teacher(), "/api/admin/content/modules/" + moduleId + "/order", Map.of("taskIds", duplicate))
                    .andExpect(status().isBadRequest());
            putAs(as(lena), "/api/admin/content/modules/" + moduleId + "/order", Map.of("taskIds", original))
                    .andExpect(status().isForbidden());
        } finally {
            putAs(teacher(), "/api/admin/content/modules/" + moduleId + "/order", Map.of("taskIds", original)).andExpect(status().isOk());
        }
        assertEquals(original, order(moduleId));
    }
}

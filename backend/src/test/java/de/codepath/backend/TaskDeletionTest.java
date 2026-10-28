package de.codepath.backend;

import de.codepath.backend.content.ContentImporter;
import de.codepath.backend.content.ContentPackage;
import de.codepath.backend.content.Difficulty;
import de.codepath.backend.engine.TaskType;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Aufgaben endgültig löschen: nur archiviert, mit Bestätigung, samt allen Daten – und gemerkt. */
class TaskDeletionTest extends IntegrationTestBase {

    @Autowired
    ContentImporter importer;

    @Test
    void deleteArchivedTaskWithAllItsData() throws Exception {
        String moduleSlug = "loeschen-" + System.nanoTime();
        long moduleId = body(postAs(teacher(), "/api/admin/content/modules",
                Map.of("slug", moduleSlug, "title", "Löschtest", "sortOrder", 98))).get("id").asLong();
        long taskId = body(postAs(teacher(), "/api/admin/content/modules/" + moduleId + "/tasks",
                Map.of("slug", "frage", "type", "MULTIPLE_CHOICE", "title", "Frage"))).get("taskId").asLong();
        postAs(teacher(), "/api/admin/content/tasks/" + taskId + "/publish", null).andExpect(status().isOk());

        Course course = course("8a");
        User lena = student("lena", course);
        unlock(course, moduleSlug);
        postAs(as(lena), "/api/modules/" + moduleSlug + "/frage/submit", Map.of("selected", List.of("a")))
                .andExpect(jsonPath("$.isCorrect").value(true));
        long before = points(lena);
        assertTrue(before > 0);

        String url = "/api/admin/content/tasks/" + taskId;
        // Erst archivieren, dann löschen
        mvc.perform(delete(url).param("confirm", "frage").with(teacher()).with(csrf())).andExpect(status().isConflict());
        putAs(teacher(), url + "/archived?value=true", null).andExpect(status().isOk());

        JsonNode impact = body(getAs(teacher(), url + "/delete-impact").andExpect(status().isOk()));
        assertEquals(1, impact.get("attempts").asInt());
        assertEquals(1, impact.get("students").asInt());
        assertEquals(before, impact.get("points").asLong());

        // Falsche Bestätigung, falsche Rolle
        mvc.perform(delete(url).param("confirm", "falsch").with(teacher()).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(delete(url).param("confirm", "frage").with(as(lena)).with(csrf())).andExpect(status().isForbidden());

        mvc.perform(delete(url).param("confirm", "frage").with(teacher()).with(csrf())).andExpect(status().isNoContent());
        getAs(teacher(), url).andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("select count(*) from task_versions where task_id = ?", Integer.class, taskId));
        assertEquals(0, jdbc.queryForObject("select count(*) from attempts where task_id = ?", Integer.class, taskId));
        assertEquals(0, jdbc.queryForObject("select count(*) from progress where task_id = ?", Integer.class, taskId));
        assertEquals(before - impact.get("points").asLong(), points(lena));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_events where action = 'TASK_DELETED'", Integer.class));

        // Mitgelieferte Inhalte legen sie nicht wieder an – ein bewusster Import schon
        ContentPackage.ModuleBundle bundle = new ContentPackage.ModuleBundle(
                new ContentPackage.ModuleFile(moduleSlug, "Löschtest", null, null, 98),
                List.of(new ContentPackage.TaskFile("frage", TaskType.MULTIPLE_CHOICE, "Frage", Difficulty.EASY, 10, 1,
                        null, null, null, List.of(), null, Map.of(),
                        Map.of("options", List.of(Map.of("id", "a", "text", "A"), Map.of("id", "b", "text", "B")), "correct", List.of("a")), null)));
        assertEquals(0, importer.importBundles(List.of(bundle), ContentImporter.Mode.FILL_MISSING, null).tasksCreated());
        assertEquals(0, jdbc.queryForObject("select count(*) from tasks where slug = 'frage' and module_id = ?", Integer.class, moduleId));
        assertEquals(1, importer.importBundles(List.of(bundle), ContentImporter.Mode.UPDATE, null, true).tasksCreated());
        assertEquals(0, jdbc.queryForObject("select count(*) from deleted_tasks where module_slug = ?", Integer.class, moduleSlug));
    }
}

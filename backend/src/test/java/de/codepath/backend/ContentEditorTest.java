package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gate von Phase 3: eine neue Aufgabe ohne Deployment anlegen, prüfen und freischalten. */
class ContentEditorTest extends IntegrationTestBase {

    @Test
    void createCheckPublishAndVersionATask() throws Exception {
        String slug = "test-" + System.nanoTime();
        JsonNode module = body(postAs(teacher(), "/api/admin/content/modules",
                Map.of("slug", slug, "title", "Testmodul", "description", "Nur für Tests", "icon", "🧪", "sortOrder", 99))
                .andExpect(status().isOk()));
        long moduleId = module.get("id").asLong();

        JsonNode created = body(postAs(teacher(), "/api/admin/content/modules/" + moduleId + "/tasks",
                Map.of("slug", "frage-1", "type", "MULTIPLE_CHOICE", "title", "Erste Frage")).andExpect(status().isOk()));
        long taskId = created.get("taskId").asLong();
        assertTrue(created.get("draft").get("config").has("options"), "Vorlage des Typs fehlt");
        assertTrue(created.get("published").isNull());

        // Schüler sehen Entwürfe nicht
        Course course = course("8a");
        User student = student("lena", course);
        unlock(course, slug);
        getAs(as(student), "/api/modules/" + slug + "/frage-1").andExpect(status().isNotFound());

        postAs(teacher(), "/api/admin/content/tasks/" + taskId + "/check", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.dryRun.sampleOutcome").value("CORRECT"));

        // Vorschau wie bei Schülern, ohne Lösung, und Probebewertung ohne Speichern
        String preview = getAs(teacher(), "/api/admin/content/tasks/" + taskId + "/preview").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(preview.contains("\"correct\""));
        postAs(teacher(), "/api/admin/content/tasks/" + taskId + "/preview/evaluate", Map.of("selected", List.of("a")))
                .andExpect(jsonPath("$.isCorrect").value(true));
        assertEquals(0, jdbc.queryForObject("select count(*) from attempts", Integer.class));

        postAs(teacher(), "/api/admin/content/tasks/" + taskId + "/publish", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentVersion").value(1));
        postAs(as(student), "/api/modules/" + slug + "/frage-1/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());

        // Ungültiger Entwurf lässt sich speichern, aber nicht veröffentlichen
        ObjectNode draft = (ObjectNode) body(getAs(teacher(), "/api/admin/content/tasks/" + taskId)).get("published");
        ((ObjectNode) draft.get("config")).putArray("correct").add("zz");
        putAs(teacher(), "/api/admin/content/tasks/" + taskId + "/draft", draft).andExpect(status().isOk());
        postAs(teacher(), "/api/admin/content/tasks/" + taskId + "/publish", null).andExpect(status().isUnprocessableContent());

        // Korrigierte Fassung wird Version 2; der alte Versuch bleibt an Version 1
        ((ObjectNode) draft.get("config")).putArray("correct").add("b");
        draft.put("title", "Erste Frage, korrigiert");
        putAs(teacher(), "/api/admin/content/tasks/" + taskId + "/draft", draft).andExpect(status().isOk());
        postAs(teacher(), "/api/admin/content/tasks/" + taskId + "/publish", null)
                .andExpect(jsonPath("$.currentVersion").value(2))
                .andExpect(jsonPath("$.versions.length()").value(2));
        Integer oldVersion = jdbc.queryForObject("select v.version from attempts a join task_versions v on v.id = a.task_version_id", Integer.class);
        assertEquals(1, oldVersion);
        getAs(as(student), "/api/modules/" + slug + "/frage-1").andExpect(jsonPath("$.title").value("Erste Frage, korrigiert"));

        // Archivieren blendet die Aufgabe für Schüler aus
        putAs(teacher(), "/api/admin/content/tasks/" + taskId + "/archived?value=true", null).andExpect(status().isOk());
        getAs(as(student), "/api/modules/" + slug + "/frage-1").andExpect(status().isNotFound());
    }

    @Test
    void exportAndImportRoundTrip() throws Exception {
        byte[] zip = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/content/export").with(teacher()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(zip.length > 10_000);

        JsonNode report = body(mvc.perform(multipart("/api/admin/content/import")
                        .file(new MockMultipartFile("file", "content.zip", "application/zip", zip)).with(teacher()).with(csrf()))
                .andExpect(status().isOk()));
        assertEquals(0, report.get("versionsPublished").asInt(), report.toString());
        assertTrue(report.get("unchanged").asInt() >= 123);
        assertEquals(0, report.get("errors").size());

        // Mitgelieferte Inhalte entsprechen dem Startinhalt: nichts Neues
        postAs(teacher(), "/api/admin/content/import-bundled", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionsPublished").value(0));
    }
}

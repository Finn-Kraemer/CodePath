package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stundenabläufe: anlegen, starten, jeden Schritt genau einmal ausführen, Fehler melden, Freigaben zurücksetzen. */
class LessonTest extends IntegrationTestBase {

    @BeforeEach
    void clearLessons() {
        jdbc.update("delete from lessons");
        jdbc.update("delete from global_announcements");
    }

    long taskId(String slug) {
        return jdbc.queryForObject("select id from tasks where slug = ?", Long.class, slug);
    }

    static Map<String, Object> step(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void runExecutesEachStepOnceReportsFailuresAndRestoresReleases() throws Exception {
        Course course = course("8a");
        User lena = student("lena", course);

        List<Object> steps = List.of(
                step("type", "RELEASE", "moduleSlug", "git", "action", "UNLOCK", "deadline", "23:59", "minutes", 10),
                step("type", "ANNOUNCEMENT", "text", "Heute: Git!", "displayMode", "HEADER"),
                step("type", "QUIZ", "text", "Warm-up", "taskIds", List.of(taskId("g1"), taskId("g5"))),
                step("type", "QUIZ", "text", "Noch eins", "taskIds", List.of(taskId("g6"))),
                step("type", "NOTE", "text", "Fragen sammeln"),
                step("type", "RELEASE", "moduleSlug", "git", "taskSlug", "g3", "action", "HIDE"));
        JsonNode lesson = body(postAs(teacher(), "/api/admin/lessons", Map.of("title", "Git-Stunde", "courseId", course.getId(),
                "restoreReleases", true, "steps", steps)).andExpect(status().isOk()));
        long id = lesson.get("id").asLong();
        assertEquals(6, lesson.get("steps").size());
        assertEquals("Modul freigeben: Git & Versionskontrolle (Frist 23:59)", lesson.get("labels").get(0).asString());
        getAs(as(lena), "/api/modules/git").andExpect(status().isForbidden());

        // Ein Klick: Ablauf läuft, erster Schritt ausgeführt
        JsonNode run = body(postAs(teacher(), "/api/admin/lessons/" + id + "/start", null).andExpect(status().isOk()));
        assertEquals("RUNNING", run.get("status").asString());
        assertEquals(0, run.get("currentStep").asInt());
        assertTrue(run.get("log").get(0).get("ok").asBoolean());
        getAs(as(lena), "/api/modules/git").andExpect(status().isOk());
        postAs(teacher(), "/api/admin/lessons/" + id + "/start", null).andExpect(status().isConflict());

        // „Weiter“ führt jeden Schritt genau einmal aus
        body(postAs(teacher(), "/api/admin/lessons/" + id + "/next", Map.of("expectedStep", 1)).andExpect(status().isOk()));
        postAs(teacher(), "/api/admin/lessons/" + id + "/next", Map.of("expectedStep", 1)).andExpect(status().isConflict());
        assertEquals("Heute: Git!", body(getAs(as(lena), "/api/common/announcement")).get("content").asString());
        run = body(postAs(teacher(), "/api/admin/lessons/" + id + "/next", Map.of("expectedStep", 2)).andExpect(status().isOk()));
        assertTrue(run.get("log").get(2).get("ok").asBoolean(), "Quiz gestartet");

        // Zweites Quiz scheitert verständlich, der Ablauf geht weiter
        run = body(postAs(teacher(), "/api/admin/lessons/" + id + "/next", Map.of("expectedStep", 3)).andExpect(status().isOk()));
        JsonNode failed = run.get("log").get(3);
        assertFalse(failed.get("ok").asBoolean());
        assertTrue(failed.get("message").asString().contains("läuft schon"));
        assertEquals("RUNNING", run.get("status").asString());
        postAs(teacher(), "/api/admin/lessons/" + id + "/next", Map.of("expectedStep", 4)).andExpect(status().isOk());
        postAs(teacher(), "/api/admin/lessons/" + id + "/next", Map.of("expectedStep", 5)).andExpect(status().isOk());
        getAs(as(lena), "/api/modules/git/g3").andExpect(status().isForbidden());
        postAs(teacher(), "/api/admin/lessons/" + id + "/next", null).andExpect(status().isConflict());

        // Beenden: Freigaben wie vor dem Start
        JsonNode done = body(postAs(teacher(), "/api/admin/lessons/" + id + "/finish", null).andExpect(status().isOk()));
        assertEquals("DONE", done.get("status").asString());
        getAs(as(lena), "/api/modules/git").andExpect(status().isForbidden());

        assertEquals(6, jdbc.queryForObject("select count(*) from audit_events where action = 'LESSON_STEP_EXECUTED'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_events where action = 'LESSON_STEP_EXECUTED' and details::text like '%\"ok\": false%'", Integer.class));

        // Zurücksetzen für die nächste Stunde
        assertEquals("DRAFT", body(postAs(teacher(), "/api/admin/lessons/" + id + "/reset", null)).get("status").asString());
    }

    @Test
    void manageSortDuplicateDeleteAndValidate() throws Exception {
        Course course = course("8a");
        long a = body(postAs(teacher(), "/api/admin/lessons", Map.of("title", "A", "courseId", course.getId()))).get("id").asLong();
        long b = body(postAs(teacher(), "/api/admin/lessons", Map.of("title", "Vorlage B"))).get("id").asLong();
        long copy = body(postAs(teacher(), "/api/admin/lessons/" + a + "/copy", null).andExpect(status().isOk())).get("id").asLong();
        assertEquals("A (Kopie)", body(getAs(teacher(), "/api/admin/lessons/" + copy)).get("title").asString());

        putAs(teacher(), "/api/admin/lessons/order", List.of(b, copy, a)).andExpect(status().isOk());
        JsonNode list = body(getAs(teacher(), "/api/admin/lessons?courseId=" + course.getId()));
        assertEquals(List.of(b, copy, a), List.of(list.get(0).get("id").asLong(), list.get(1).get("id").asLong(), list.get(2).get("id").asLong()));

        // Ungültige Schritte, Vorlage ohne Kurs und leerer Ablauf lassen sich nicht starten
        putAs(teacher(), "/api/admin/lessons/" + a, Map.of("steps", List.of(step("type", "RELEASE")))).andExpect(status().isBadRequest());
        putAs(teacher(), "/api/admin/lessons/" + a, Map.of("steps", List.of(step("type", "RELEASE", "moduleSlug", "gibtsnicht"))))
                .andExpect(status().isNotFound());
        putAs(teacher(), "/api/admin/lessons/" + a, Map.of("steps", List.of(step("type", "QUIZ")))).andExpect(status().isBadRequest());
        postAs(teacher(), "/api/admin/lessons/" + a + "/start", null).andExpect(status().isBadRequest());
        putAs(teacher(), "/api/admin/lessons/" + b, Map.of("steps", List.of(step("type", "NOTE", "text", "x")))).andExpect(status().isOk());
        postAs(teacher(), "/api/admin/lessons/" + b + "/start", null).andExpect(status().isBadRequest());
        getAs(as(student("lena", course)), "/api/admin/lessons").andExpect(status().isForbidden());

        mvc.perform(delete("/api/admin/lessons/" + copy).with(teacher()).with(csrf())).andExpect(status().isOk());
        assertEquals(2, body(getAs(teacher(), "/api/admin/lessons")).size());

        // Vorlagen im zentralen Export
        JsonNode sections = body(getAs(teacher(), "/api/admin/transfer/sections"));
        boolean found = false;
        for (JsonNode s : sections) found |= "lessons".equals(s.get("key").asString());
        assertTrue(found, "Bereich „Stundenabläufe“ im Export");
    }
}

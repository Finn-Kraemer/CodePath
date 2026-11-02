package de.codepath.backend;

import de.codepath.backend.help.HelpService;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stiller Hilfe-Ruf „Ich hänge fest“ (#38). */
class HelpTest extends IntegrationTestBase {

    @Autowired
    HelpService helpService;

    @AfterEach
    void restoreRateLimit() {
        ReflectionTestUtils.setField(helpService, "minIntervalSeconds", 30L);
    }

    static Map<String, Object> call(String taskSlug, String draft) {
        Map<String, Object> body = new HashMap<>();
        body.put("moduleSlug", "programmierung");
        body.put("taskSlug", taskSlug);
        body.put("category", "ERROR_MESSAGE");
        body.put("note", "Ich verstehe die Fehlermeldung nicht");
        body.put("draft", draft);
        body.put("lastOutput", "NameError: name 'x' is not defined");
        return body;
    }

    @Test
    void studentCallsTeacherClaimsAndResolves() throws Exception {
        Course course = course("8a");
        unlock(course, "programmierung");
        User lena = student("lena", course);
        User tom = student("tom", course);

        postAs(as(lena), "/api/help", call("p1", "print(x)")).andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("OPEN"))
                .andExpect(jsonPath("$.request.ahead").value(0))
                .andExpect(jsonPath("$.request.draft").doesNotExist());
        postAs(as(tom), "/api/help", call("p2", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.request.ahead").value(1));
        // Der Entwurf zählt nicht als Versuch
        assertEquals(0, jdbc.queryForObject("select count(*) from attempts", Integer.class));

        // Schüler sehen die Warteschlange nicht
        getAs(as(tom), "/api/admin/help").andExpect(status().isForbidden());

        JsonNode queue = body(getAs(teacher(), "/api/admin/help").andExpect(status().isOk()));
        assertEquals(2, queue.get("open").size());
        JsonNode first = queue.get("open").get(0);
        assertEquals("lena", first.get("username").asString());
        assertEquals("8a", first.get("courseName").asString());
        assertEquals("p1", first.get("taskSlug").asString());
        assertEquals("print(x)", first.get("draft").asString());
        assertEquals("ERROR_MESSAGE", first.get("category").asString());
        assertTrue(first.get("lastOutput").asString().contains("NameError"));
        long id = first.get("id").asLong();

        postAs(teacher(), "/api/admin/help/" + id + "/claim", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLAIMED"));
        postAs(teacher(), "/api/admin/help/" + id + "/message", Map.of("text", "Schau dir Tipp 2 an")).andExpect(status().isOk());
        getAs(as(lena), "/api/help").andExpect(jsonPath("$.request.status").value("CLAIMED"))
                .andExpect(jsonPath("$.request.teacherMessage").value("Schau dir Tipp 2 an"));

        postAs(teacher(), "/api/admin/help/" + id + "/resolve", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("TEACHER"));
        getAs(as(lena), "/api/help").andExpect(jsonPath("$.request").doesNotExist());
        getAs(as(tom), "/api/help").andExpect(jsonPath("$.request.ahead").value(0));
        postAs(teacher(), "/api/admin/help/" + id + "/resolve", null).andExpect(status().isConflict());

        queue = body(getAs(teacher(), "/api/admin/help"));
        assertEquals(1, queue.get("open").size());
        assertEquals("RESOLVED", queue.get("recent").get(0).get("status").asString());

        // Schüler bricht selbst ab
        mvc.perform(delete("/api/help").with(as(tom)).with(csrf())).andExpect(status().isOk());
        assertEquals(0, body(getAs(teacher(), "/api/admin/help")).get("open").size());
    }

    @Test
    void oneOpenRequestPerStudentAndRateLimit() throws Exception {
        Course course = course("8b");
        unlock(course, "programmierung");
        User lena = student("lena", course);

        postAs(as(lena), "/api/help", call("p1", "a")).andExpect(status().isOk());
        postAs(as(lena), "/api/help", call("p2", "b")).andExpect(status().isTooManyRequests());

        ReflectionTestUtils.setField(helpService, "minIntervalSeconds", 0L);
        postAs(as(lena), "/api/help", call("p2", "b")).andExpect(status().isOk())
                .andExpect(jsonPath("$.request.taskSlug").value("p2"));
        JsonNode open = body(getAs(teacher(), "/api/admin/help")).get("open");
        assertEquals(1, open.size(), "Ein neuer Klick aktualisiert den offenen Ruf");
        assertEquals("b", open.get(0).get("draft").asString());

        Map<String, Object> tooLong = call("p2", "c");
        tooLong.put("note", "x".repeat(281));
        postAs(as(lena), "/api/help", tooLong).andExpect(status().isBadRequest());
        Map<String, Object> huge = call("p2", "y".repeat(50_000));
        postAs(as(lena), "/api/help", huge).andExpect(status().isOk());
        assertTrue(body(getAs(teacher(), "/api/admin/help")).get("open").get(0).get("draft").asString().length() < 20_100);
    }

    @Test
    void onlyCourseMembersForVisibleTasks() throws Exception {
        Course course = course("8c");
        User lena = student("lena", course);
        User nobody = student("ohne-kurs", null);

        // Modul nicht freigegeben
        postAs(as(lena), "/api/help", call("p1", "")).andExpect(status().isForbidden());
        postAs(as(nobody), "/api/help", call("p1", "")).andExpect(status().isForbidden());
        postAs(teacher(), "/api/help", call("p1", "")).andExpect(status().isBadRequest());
        assertEquals(0, body(getAs(teacher(), "/api/admin/help")).get("open").size());
    }

    @Test
    void solvingTheTaskClosesTheRequestAndCourseEndDeletesIt() throws Exception {
        Course course = course("8d");
        unlock(course, "programmierung");
        User lena = student("lena", course);

        postAs(as(lena), "/api/help", call("p1", "")).andExpect(status().isOk());
        postAs(as(lena), "/api/modules/programmierung/p1/submit", answer("selected", java.util.List.of("b")))
                .andExpect(jsonPath("$.outcome").value("CORRECT"));
        getAs(as(lena), "/api/help").andExpect(jsonPath("$.request").doesNotExist());
        JsonNode recent = body(getAs(teacher(), "/api/admin/help")).get("recent");
        assertEquals("SELF_SOLVED", recent.get(0).get("resolution").asString());

        // Lernanalyse: Hilfe-Rufe je Aufgabe
        long taskId = contentService.publishedTask("programmierung", "p1").taskId();
        JsonNode stats = body(getAs(teacher(), "/api/admin/help/statistics").andExpect(status().isOk()));
        assertEquals(taskId, stats.get(0).get("taskId").asLong());
        assertEquals(1, stats.get(0).get("calls").asInt());
        assertEquals(1, stats.get(0).get("selfSolved").asInt());
        getAs(as(lena), "/api/admin/help/statistics").andExpect(status().isForbidden());

        courseService.endCourse(course.getId());
        assertEquals(0, jdbc.queryForObject("select count(*) from help_requests", Integer.class));
    }
}

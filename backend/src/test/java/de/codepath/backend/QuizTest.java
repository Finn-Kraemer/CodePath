package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quiz-Runde am Beamer: Lobby → Frage → Auflösung → Ende, anonyme Verteilung, keine Punkte. */
class QuizTest extends IntegrationTestBase {

    long task(String slug) {
        return contentService.publishedTask("programmierung", slug).taskId();
    }

    @Test
    void fullRoundWithAnonymousDistribution() throws Exception {
        Course course = course("8a");
        Course other = course("8b");
        User lena = student("lena", course);
        User tom = student("tom", course);
        User fremd = student("fremd", other);

        JsonNode candidates = body(getAs(teacher(), "/api/admin/quiz/candidates").andExpect(status().isOk()));
        assertTrue(candidates.size() > 5);
        candidates.forEach(c -> assertNotNull(c.get("question").asString()));
        getAs(as(lena), "/api/admin/quiz/candidates").andExpect(status().isForbidden());

        postAs(teacher(), "/api/admin/quiz", Map.of("title", "Warm-up", "courseId", course.getId(),
                "taskIds", List.of(task("p1"), task("p2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quiz.phase").value("LOBBY"))
                .andExpect(jsonPath("$.quiz.total").value(2));
        // Nur eine Runde gleichzeitig
        postAs(teacher(), "/api/admin/quiz", Map.of("taskIds", List.of(task("p1")))).andExpect(status().isConflict());

        // Andere Kurse sehen das Quiz nicht
        getAs(as(fremd), "/api/quiz/current").andExpect(jsonPath("$.quiz").doesNotExist());
        getAs(as(lena), "/api/quiz/current").andExpect(jsonPath("$.quiz.phase").value("LOBBY"))
                .andExpect(jsonPath("$.quiz.question").doesNotExist());

        postAs(teacher(), "/api/admin/quiz/current/next", null).andExpect(jsonPath("$.quiz.phase").value("QUESTION"));
        // Vor der Auflösung keine Lösung und keine Verteilung für Schüler
        getAs(as(lena), "/api/quiz/current")
                .andExpect(jsonPath("$.quiz.options.length()").value(4))
                .andExpect(jsonPath("$.quiz.correct").doesNotExist())
                .andExpect(jsonPath("$.quiz.distribution").doesNotExist());

        postAs(as(lena), "/api/quiz/current/answer", Map.of("index", 0, "selected", List.of("a"))).andExpect(status().isOk());
        // Ändern bis zur Auflösung erlaubt
        postAs(as(lena), "/api/quiz/current/answer", Map.of("index", 0, "selected", List.of("b"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.quiz.myAnswer[0]").value("b"))
                .andExpect(jsonPath("$.quiz.myScore").value(0));
        postAs(as(tom), "/api/quiz/current/answer", Map.of("index", 0, "selected", List.of("c"))).andExpect(status().isOk());
        postAs(as(tom), "/api/quiz/current/answer", Map.of("index", 0, "selected", List.of("a", "b"))).andExpect(status().isBadRequest());
        postAs(as(fremd), "/api/quiz/current/answer", Map.of("index", 0, "selected", List.of("b"))).andExpect(status().isNotFound());
        postAs(as(lena), "/api/quiz/current/answer", Map.of("index", 1, "selected", List.of("b"))).andExpect(status().isConflict());

        JsonNode teacherView = body(getAs(teacher(), "/api/admin/quiz/current")).get("quiz");
        assertEquals(2, teacherView.get("answered").asInt());
        assertEquals(1, teacherView.get("distribution").get("b").asInt());
        assertEquals(1, teacherView.get("distribution").get("c").asInt());
        assertFalse(teacherView.toString().contains("lena"), "Antworten bleiben anonym");

        postAs(teacher(), "/api/admin/quiz/current/next", null).andExpect(status().isConflict());
        postAs(teacher(), "/api/admin/quiz/current/reveal", null).andExpect(jsonPath("$.quiz.phase").value("REVEAL"));
        getAs(as(lena), "/api/quiz/current")
                .andExpect(jsonPath("$.quiz.correct[0]").value("b"))
                .andExpect(jsonPath("$.quiz.myAnswerCorrect").value(true))
                .andExpect(jsonPath("$.quiz.myScore").value(1))
                .andExpect(jsonPath("$.quiz.distribution.b").value(1));
        postAs(as(lena), "/api/quiz/current/answer", Map.of("index", 0, "selected", List.of("a"))).andExpect(status().isConflict());

        postAs(teacher(), "/api/admin/quiz/current/next", null).andExpect(jsonPath("$.quiz.index").value(1));
        postAs(teacher(), "/api/admin/quiz/current/reveal", null).andExpect(status().isOk());
        // Nach der letzten Frage ist die Runde vorbei
        postAs(teacher(), "/api/admin/quiz/current/next", null)
                .andExpect(jsonPath("$.quiz.phase").value("FINISHED"))
                .andExpect(jsonPath("$.quiz.results.length()").value(2))
                .andExpect(jsonPath("$.quiz.results[0].correct").value(1))
                .andExpect(jsonPath("$.quiz.results[0].answered").value(2));
        getAs(as(lena), "/api/quiz/current").andExpect(jsonPath("$.quiz").doesNotExist());

        // Keine Punkte, keine Versuche
        assertEquals(0, points(lena));
        assertEquals(0, jdbc.queryForObject("select count(*) from attempts", Integer.class));
    }

    @Test
    void onlyMultipleChoiceTasks() throws Exception {
        long code = contentService.publishedTask("programmierung", "p13").taskId();
        postAs(teacher(), "/api/admin/quiz", Map.of("taskIds", List.of(code))).andExpect(status().isBadRequest());
        postAs(teacher(), "/api/admin/quiz", Map.of("taskIds", List.of())).andExpect(status().isBadRequest());
        User lena = student("lena", null);
        postAs(as(lena), "/api/admin/quiz", Map.of("taskIds", List.of(task("p1")))).andExpect(status().isForbidden());
    }
}

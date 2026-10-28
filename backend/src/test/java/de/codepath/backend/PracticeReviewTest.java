package de.codepath.backend;

import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PracticeReviewTest extends IntegrationTestBase {

    @Test
    void submitReviewWithRubricAndResubmitAfterRejection() throws Exception {
        Course course = course("8a");
        User user = student("lena", course);
        unlock(course, "web-entwicklung");
        PublishedTask task = contentService.publishedTask("web-entwicklung", "w13");
        String url = "/api/modules/web-entwicklung/w13";

        postAs(as(user), url + "/submit", Map.of("content", "")).andExpect(jsonPath("$.outcome").value("INVALID"));
        postAs(as(user), url + "/submit", Map.of("content", "<p>Meine erste Seite</p>"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        postAs(as(user), url + "/submit", Map.of("content", "<p>nochmal</p>")).andExpect(status().isConflict());

        JsonNode pending = body(getAs(teacher(), "/api/admin/submissions").andExpect(status().isOk()));
        assertEquals(1, pending.size());
        long reviewId = pending.get(0).get("id").asLong();

        putAs(teacher(), "/api/admin/submissions/" + reviewId + "/reject", Map.of("comment", "Bitte Liste ergänzen", "lockTask", false))
                .andExpect(status().isOk());
        getAs(as(user), url).andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reviewComment").value("Bitte Liste ergänzen"))
                .andExpect(jsonPath("$.lastAnswer.content").value("<p>Meine erste Seite</p>"));

        postAs(as(user), url + "/submit", Map.of("content", "<p>Verbessert</p>")).andExpect(status().isOk());
        reviewId = body(getAs(teacher(), "/api/admin/submissions")).get(0).get("id").asLong();
        JsonNode detail = body(getAs(teacher(), "/api/admin/submissions/" + reviewId));
        JsonNode rubric = detail.get("rubric");

        Map<String, Integer> tooMany = Map.of(rubric.get(0).get("id").asString(), rubric.get(0).get("points").asInt() + 1);
        putAs(teacher(), "/api/admin/submissions/" + reviewId + "/approve", Map.of("scores", tooMany)).andExpect(status().isBadRequest());

        Map<String, Integer> scores = new HashMap<>();
        int expected = 0;
        for (JsonNode c : rubric) {
            int value = c.get("points").asInt() - 1;
            scores.put(c.get("id").asString(), value);
            expected += value;
        }
        putAs(teacher(), "/api/admin/submissions/" + reviewId + "/approve", Map.of("scores", scores, "comment", "Gut gemacht"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.awardedPoints").value(expected));
        assertEquals(expected, points(user));
        getAs(as(user), url).andExpect(jsonPath("$.status").value("SOLVED")).andExpect(jsonPath("$.pointsEarned").value(expected));

        // Neubewertung gleicht nur die Differenz aus
        putAs(teacher(), "/api/admin/submissions/" + reviewId + "/approve", Map.of()).andExpect(status().isOk());
        assertEquals(task.version().getPoints(), points(user));
    }

    @Test
    void teacherCorrectionsAreJournalEntries() throws Exception {
        Course course = course("8a");
        User user = student("lena", course);
        PublishedTask task = contentService.publishedTask("git", "g1");
        String toggle = "/api/admin/students/lena/tasks/" + task.taskId() + "/toggle";
        putAs(teacher(), toggle, Map.of("halfPoints", true)).andExpect(status().isOk());
        assertEquals(Math.max(1, task.version().getPoints() / 2), points(user));
        putAs(teacher(), toggle, Map.of("halfPoints", false)).andExpect(status().isOk());
        assertEquals(task.version().getPoints(), points(user));
        putAs(teacher(), toggle, Map.of("halfPoints", false)).andExpect(status().isOk());
        assertEquals(0, points(user));
        assertEquals(3, jdbc.queryForObject("select count(*) from points_entries where reason = 'CORRECTION'", Integer.class));
    }
}

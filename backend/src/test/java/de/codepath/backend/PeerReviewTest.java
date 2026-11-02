package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Peer-Review: nach der eigenen Abgabe zwei anonyme Abgaben anderer nach der Rubrik bewerten. */
class PeerReviewTest extends IntegrationTestBase {

    static final String TASK = "/api/modules/web-entwicklung/w13";
    static final String PEER = "/api/peer-reviews/web-entwicklung/w13";

    void submit(User user, String html) throws Exception {
        postAs(as(user), TASK + "/submit", Map.of("content", html)).andExpect(status().isOk());
    }

    Map<String, Integer> scores(JsonNode rubric, int minus) {
        Map<String, Integer> scores = new HashMap<>();
        rubric.forEach(c -> scores.put(c.get("id").asString(), Math.max(0, c.get("points").asInt() - minus)));
        return scores;
    }

    @Test
    void reviewTwoAnonymousSubmissionsAndSeeFeedback() throws Exception {
        Course course = course("9a");
        Course other = course("9b");
        unlock(course, "web-entwicklung");
        unlock(other, "web-entwicklung");
        User lena = student("lena", course);
        User tom = student("tom", course);
        User mia = student("mia", course);
        User fremd = student("fremd", other);

        // Erst nach der eigenen Abgabe
        getAs(as(lena), PEER).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.canReview").value(false));
        postAs(as(lena), PEER + "/assign", null).andExpect(status().isConflict());

        submit(lena, "<p>Lenas Seite über Hamburg</p>");
        submit(tom, "<p>Toms Seite</p>");
        submit(mia, "<p>Mias Seite</p>");
        submit(fremd, "<p>Fremde Seite</p>");

        JsonNode state = body(postAs(as(lena), PEER + "/assign", null).andExpect(status().isOk()));
        JsonNode assignments = state.get("assignments");
        assertEquals(2, assignments.size());
        String all = assignments.toString();
        assertFalse(all.contains("Lenas"), "Die eigene Abgabe wird nicht zugeteilt");
        assertFalse(all.contains("Fremde"), "Nur Abgaben aus dem eigenen Kurs");
        assertFalse(all.contains("tom") || all.contains("mia"), "Anonym: keine Namen");
        assertFalse(state.get("canReview").asBoolean(), "Höchstens zwei");

        JsonNode rubric = state.get("rubric");
        long first = assignments.get(0).get("id").asLong();
        Map<String, Integer> tooMany = scores(rubric, 0);
        tooMany.put(rubric.get(0).get("id").asString(), 99);
        putAs(as(lena), "/api/peer-reviews/assignments/" + first, Map.of("scores", tooMany)).andExpect(status().isBadRequest());
        putAs(as(lena), "/api/peer-reviews/assignments/" + first, Map.of("scores", scores(rubric, 1), "comment", "Schöne Liste!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[0].submitted").value(true));
        // Fremde Zuteilungen kann man nicht bearbeiten
        putAs(as(tom), "/api/peer-reviews/assignments/" + first, Map.of("scores", scores(rubric, 0))).andExpect(status().isNotFound());

        // Die bewertete Person sieht die Rückmeldung – ohne Namen
        String reviewedContent = assignments.get(0).get("content").asString();
        User reviewed = reviewedContent.contains("Tom") ? tom : mia;
        JsonNode received = body(getAs(as(reviewed), PEER)).get("received");
        assertEquals(1, received.size());
        assertEquals("Schöne Liste!", received.get(0).get("comment").asString());
        assertFalse(received.toString().contains("lena"));

        // Die Lehrkraft sieht, wer wie bewertet hat; keine Punkte für niemanden
        long reviewId = 0;
        for (JsonNode s : body(getAs(teacher(), "/api/admin/submissions"))) {
            if (s.get("username").asString().equals(reviewed.getUsername())) reviewId = s.get("id").asLong();
        }
        JsonNode forTeacher = body(getAs(teacher(), "/api/admin/submissions/" + reviewId + "/peer-reviews"));
        assertEquals("lena", forTeacher.get(0).get("reviewer").asString());
        assertEquals(0, points(reviewed));
        assertEquals(0, points(lena));
    }

    @Test
    void disabledWithoutConfig() throws Exception {
        Course course = course("9c");
        unlock(course, "web-entwicklung");
        User lena = student("lena", course);
        // Aufgaben ohne peerReview: nichts zu tun
        getAs(as(lena), "/api/peer-reviews/web-entwicklung/w1").andExpect(jsonPath("$.enabled").value(false));
    }
}

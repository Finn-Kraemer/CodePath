package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static de.codepath.backend.LearningPathTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fehler aus dem Praxisbetrieb von Lernpfade 2.0 (Epic #20), die die ersten Tests nicht abgedeckt haben. */
class LearningPathRegressionTest extends IntegrationTestBase {

    long create(String title, String slug) throws Exception {
        return body(postAs(teacher(), "/api/admin/learning-paths", Map.of("title", title, "slug", slug)).andExpect(status().isOk()))
                .get("id").asLong();
    }

    static JsonNode find(JsonNode view, String id) {
        for (JsonNode n : view.get("nodes")) if (n.get("id").asString().equals(id)) return n;
        throw new AssertionError("Station " + id + " fehlt");
    }

    static String state(JsonNode view, String id) {
        return find(view, id).get("state").asString();
    }

    @Test
    void conditionsAndGatesAreRecordedOnReadOnlyRequests() throws Exception {
        Course course = course("r1");
        unlock(course, "git");
        User mia = student("mia", course);
        // Erst lösen, dann wird der Pfad angelegt: Das Ergebnis muss beim ersten Lesen festgeschrieben werden
        postAs(as(mia), "/api/modules/git/g1/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());

        long id = create("Lesepfad", "lesepfad");
        var graph = Map.of(
                "nodes", List.of(
                        node("t1", "TASK", "git", "g1", "Start", 0),
                        node("t2", "TASK", "git", "g5", "Normal", 100),
                        node("fast", "TASK", "git", "g3", "Abkürzung", 200),
                        gateNode("tor", "Tor", 300, 50, null, null, null, null)),
                "edges", List.of(
                        conditionalEdge("t1", "t2", true, "DEFAULT", null, null, null, null),
                        conditionalEdge("t1", "fast", true, "PERFECT_FIRST_TRY", "g1", null, null, null),
                        requiredEdge("t2", "fast"),
                        requiredEdge("fast", "tor")));
        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of(
                "graph", graph, "courseId", course.getId(), "enforce", true, "published", true)).andExpect(status().isOk());

        getAs(as(mia), "/api/learning/next").andExpect(status().isOk());
        getAs(as(mia), "/api/modules").andExpect(status().isOk());
        getAs(as(mia), "/api/modules/git/g3").andExpect(status().isOk());
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from learning_path_branch_results where user_id = ? and edge_key like 't1->%'",
                Integer.class, mia.getId()));
        getAs(teacher(), "/api/admin/students/mia/learning-path").andExpect(status().isOk());
        getAs(teacher(), "/api/admin/learning-paths/" + id + "/gate-stats").andExpect(status().isOk());
    }

    @Test
    void remedialNodeIsInsertedBeforeTheNormalPathWithoutExtraEdge() throws Exception {
        Course course = course("r2");
        unlock(course, "git");
        User bob = student("bob", course);
        long id = create("Stütze", "stuetze");
        var graph = Map.of(
                "nodes", List.of(
                        node("s1", "TASK", "git", "g1", "Start", 0),
                        remedialNode("rem", "git", "g5", null, null, 100),
                        node("s2", "TASK", "git", "g3", "Weiter", 200)),
                "edges", List.of(
                        conditionalEdge("s1", "s2", true, "DEFAULT", null, null, null, null),
                        conditionalEdge("s1", "rem", true, "STRUGGLED", "g1", null, 1, 1)));
        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of(
                "graph", graph, "courseId", course.getId(), "enforce", true, "published", true)).andExpect(status().isOk());

        postAs(as(bob), "/api/modules/git/g1/submit", Map.of("selected", List.of("a"))).andExpect(status().isOk());
        postAs(as(bob), "/api/modules/git/g1/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());

        JsonNode view = body(getAs(as(bob), "/api/learning-paths/stuetze"));
        assertEquals("AVAILABLE", state(view, "rem"));
        assertEquals("BLOCKED", state(view, "s2"), "Stütz-Knoten liegt vor dem Normalweg");
        getAs(as(bob), "/api/modules/git/g3").andExpect(status().isForbidden());
        assertEquals("g5", body(getAs(as(bob), "/api/learning/next")).get("taskSlug").asString());

        postAs(as(bob), "/api/modules/git/g5/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        view = body(getAs(as(bob), "/api/learning-paths/stuetze"));
        assertEquals("AVAILABLE", state(view, "s2"));
    }

    @Test
    void recommendationComesFromTheEnforcedPath() throws Exception {
        Course course = course("r3");
        unlock(course, "git");
        User ida = student("ida", course);
        long free = create("Frei", "frei-pfad");
        putAs(teacher(), "/api/admin/learning-paths/" + free, Map.of(
                "graph", Map.of("nodes", List.of(node("x", "MODULE", "datenbanken", null, null, 0)), "edges", List.of()),
                "courseId", course.getId(), "published", true)).andExpect(status().isOk());
        long enforced = create("Pflicht", "pflicht-pfad");
        putAs(teacher(), "/api/admin/learning-paths/" + enforced, Map.of(
                "graph", Map.of("nodes", List.of(node("a", "TASK", "git", "g1", null, 0), node("b", "TASK", "git", "g5", null, 100)),
                        "edges", List.of(requiredEdge("a", "b"))),
                "enforce", true, "published", true)).andExpect(status().isOk());

        assertEquals("g1", body(getAs(as(ida), "/api/learning/next")).get("taskSlug").asString());
        assertEquals("pflicht-pfad", body(getAs(as(ida), "/api/learning-paths")).get(0).get("slug").asString());
    }
}

package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lernpfade: Editor der Lehrkraft, Karte mit Stand und empfohlene nächste Aufgabe. */
class LearningPathTest extends IntegrationTestBase {

    @BeforeEach
    void clearPaths() {
        jdbc.update("delete from learning_paths");
    }

    static Map<String, Object> node(String id, String kind, String module, String task, String label, int y) {
        Map<String, Object> node = new java.util.HashMap<>(Map.of("id", id, "kind", kind, "x", 100, "y", y));
        if (module != null) node.put("module", module);
        if (task != null) node.put("task", task);
        if (label != null) node.put("label", label);
        return node;
    }

    static Map<String, Object> edge(String from, String to) {
        return Map.of("from", from, "to", to);
    }

    long create(String title, String slug) throws Exception {
        return body(postAs(teacher(), "/api/admin/learning-paths", Map.of("title", title, "slug", slug)).andExpect(status().isOk()))
                .get("id").asLong();
    }

    @Test
    void newPathStartsWithAllModulesAsAChainAndGraphIsValidated() throws Exception {
        long id = create("Einstieg", "einstieg");
        JsonNode path = body(getAs(teacher(), "/api/admin/learning-paths/" + id));
        int modules = contentService.modules().size();
        assertEquals(modules, path.at("/graph/nodes").size());
        assertEquals(modules - 1, path.at("/graph/edges").size());
        assertFalse(path.get("published").asBoolean());

        var cycle = Map.of("graph", Map.of(
                "nodes", List.of(node("a", "MODULE", "git", null, null, 0), node("b", "MODULE", "datenbanken", null, null, 100)),
                "edges", List.of(edge("a", "b"), edge("b", "a"))));
        putAs(teacher(), "/api/admin/learning-paths/" + id, cycle).andExpect(status().isBadRequest());
        var unknown = Map.of("graph", Map.of("nodes", List.of(node("a", "TASK", "git", "gibt-es-nicht", null, 0)), "edges", List.of()));
        putAs(teacher(), "/api/admin/learning-paths/" + id, unknown).andExpect(status().isBadRequest());
        var milestone = Map.of("graph", Map.of("nodes", List.of(node("a", "MILESTONE", null, null, " ", 0)), "edges", List.of()));
        putAs(teacher(), "/api/admin/learning-paths/" + id, milestone).andExpect(status().isBadRequest());

        postAs(teacher(), "/api/admin/learning-paths", Map.of("title", "Doppelt", "slug", "einstieg")).andExpect(status().isConflict());
        User student = student("ben", course("7a"));
        postAs(as(student), "/api/admin/learning-paths", Map.of("title", "X", "slug", "x")).andExpect(status().isForbidden());
    }

    @Test
    void mapShowsProgressAndRecommendsTheNextStation() throws Exception {
        Course course = course("8a");
        unlock(course, "git");
        User lena = student("lena", course);
        long id = create("Git-Pfad", "git-pfad");
        var graph = Map.of(
                "nodes", List.of(node("start", "MILESTONE", null, null, "Los geht's", 0),
                        node("t1", "TASK", "git", "g1", null, 100), node("t2", "TASK", "git", "g2", "Zweite Frage", 200),
                        node("db", "MODULE", "datenbanken", null, null, 300), node("ziel", "MILESTONE", null, null, "Geschafft", 400)),
                "edges", List.of(edge("start", "t1"), edge("t1", "t2"), edge("t2", "db"), edge("db", "ziel")));
        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of("graph", graph, "description", "Erst Git, dann Datenbanken"))
                .andExpect(status().isOk());

        // Unveröffentlicht: nur die Lehrkraft sieht die Karte, Schüler bekommen die Empfehlung aus der Modulreihenfolge
        getAs(as(lena), "/api/learning-paths/git-pfad").andExpect(status().isNotFound());
        getAs(teacher(), "/api/learning-paths/git-pfad").andExpect(status().isOk());
        assertEquals(0, body(getAs(as(lena), "/api/learning-paths")).size());
        JsonNode fallback = body(getAs(as(lena), "/api/learning/next"));
        assertEquals("git", fallback.get("moduleSlug").asString());
        assertTrue(fallback.get("pathSlug").isNull());

        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of("published", true)).andExpect(status().isOk());
        assertEquals("git-pfad", body(getAs(as(lena), "/api/learning-paths")).at("/0/slug").asString());

        JsonNode view = body(getAs(as(lena), "/api/learning-paths/git-pfad"));
        assertEquals("DONE", state(view, "start"), "Meilenstein ohne Vorgänger ist erreicht");
        assertEquals("AVAILABLE", state(view, "t1"));
        assertTrue(find(view, "t1").get("recommended").asBoolean());
        assertEquals("UPCOMING", state(view, "t2"));
        assertEquals("Zweite Frage", find(view, "t2").get("label").asString());
        assertEquals("LOCKED", state(view, "db"), "Datenbanken ist für den Kurs nicht freigegeben");
        assertTrue(find(view, "db").get("href").isNull());
        assertEquals("g1", view.at("/recommendation/taskSlug").asString());
        assertEquals("UPCOMING", state(view, "ziel"), "Gesperrte Station vor dem Ziel lässt offene Stationen davor nicht durch");

        postAs(as(lena), "/api/modules/git/g1/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        view = body(getAs(as(lena), "/api/learning-paths/git-pfad"));
        assertEquals("DONE", state(view, "t1"));
        assertEquals("AVAILABLE", state(view, "t2"));
        assertEquals(1, view.get("stationsDone").asInt());
        assertEquals(3, view.get("stations").asInt());
        JsonNode next = body(getAs(as(lena), "/api/learning/next"));
        assertEquals("g2", next.get("taskSlug").asString());
        assertEquals("git-pfad", next.get("pathSlug").asString());

        // Pfad geschafft (gesperrte Stationen halten nicht auf): weiter mit den übrigen Aufgaben im begonnenen Modul
        postAs(as(lena), "/api/modules/git/g2/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        view = body(getAs(as(lena), "/api/learning-paths/git-pfad"));
        assertEquals("DONE", state(view, "ziel"));
        assertTrue(view.get("recommendation").isNull());
        next = body(getAs(as(lena), "/api/learning/next"));
        assertEquals("git", next.get("moduleSlug").asString());
        assertNotEquals("g2", next.get("taskSlug").asString());
        assertTrue(next.get("pathSlug").isNull());
        assertTrue(next.get("reason").asString().contains("schon angefangen"));

        mvc.perform(delete("/api/admin/learning-paths/" + id).with(teacher()).with(csrf())).andExpect(status().isNoContent());
        assertEquals(0, body(getAs(as(lena), "/api/learning-paths")).size());
    }

    @Test
    void coursePathSplitsModulesAndIsAppliedToModuleControl() throws Exception {
        Course course = course("9a");
        Course other = course("9b");
        unlock(course, "programmierung");
        User lena = student("lena", course);
        User tom = student("tom", other);
        long id = create("Projekttag 9a", "projekttag-9a");
        Map<String, Object> git1 = node("git1", "MODULE", "git", null, "Git 1", 0);
        git1.put("tasks", List.of("g1", "g2"));
        Map<String, Object> git2 = node("git2", "MODULE", "git", null, "Git 2", 200);
        git2.put("tasks", List.of("g3"));
        var graph = Map.of(
                "nodes", List.of(git1, node("db", "MODULE", "datenbanken", null, null, 100), git2),
                "edges", List.of(edge("git1", "db"), edge("db", "git2")));
        Map<String, Object> update = new java.util.HashMap<>(Map.of("graph", graph, "courseId", course.getId(), "lockOthers", true));
        // Entwurf: noch nichts in der Modul-Steuerung
        JsonNode draft = body(putAs(teacher(), "/api/admin/learning-paths/" + id, update).andExpect(status().isOk()));
        assertEquals(0, draft.get("applied").size());
        assertEquals("9a", draft.get("courseName").asString());

        update.put("published", true);
        JsonNode saved = body(putAs(teacher(), "/api/admin/learning-paths/" + id, update).andExpect(status().isOk()));
        assertTrue(saved.get("applied").size() > 0, "Freigaben wurden übernommen");

        JsonNode modules = body(getAs(teacher(), "/api/admin/modules?courseId=" + course.getId()));
        assertTrue(module(modules, "git").get("isUnlocked").asBoolean());
        assertTrue(module(modules, "datenbanken").get("isUnlocked").asBoolean());
        assertFalse(module(modules, "programmierung").get("isUnlocked").asBoolean(), "Andere Module wurden gesperrt");
        assertEquals(3, module(modules, "git").get("taskCount").asInt(), "Nur die drei Git-Aufgaben aus dem Lernpfad");
        long gitId = module(modules, "git").get("id").asLong();
        for (JsonNode task : body(getAs(teacher(), "/api/admin/modules/" + gitId + "/tasks?courseId=" + course.getId()))) {
            boolean used = List.of("g1", "g2", "g3").contains(task.get("slug").asString());
            assertEquals(used, task.get("visible").asBoolean(), task.get("slug").asString());
        }
        assertEquals(0, body(putAs(teacher(), "/api/admin/learning-paths/" + id, update)).get("applied").size(),
                "Zweites Speichern ändert nichts mehr");

        // Nur der Kurs sieht den Lernpfad; Stationen zählen nur ihre Aufgaben
        assertEquals("projekttag-9a", body(getAs(as(lena), "/api/learning-paths")).at("/0/slug").asString());
        assertEquals(0, body(getAs(as(tom), "/api/learning-paths")).size());
        getAs(as(tom), "/api/learning-paths/projekttag-9a").andExpect(status().isNotFound());
        JsonNode view = body(getAs(as(lena), "/api/learning-paths/projekttag-9a"));
        assertEquals(2, find(view, "git1").get("total").asInt());
        assertEquals(1, find(view, "git2").get("total").asInt());
        assertEquals("g1", body(getAs(as(lena), "/api/learning/next")).get("taskSlug").asString());

        postAs(as(lena), "/api/modules/git/g1/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        postAs(as(lena), "/api/modules/git/g2/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        view = body(getAs(as(lena), "/api/learning-paths/projekttag-9a"));
        assertEquals("DONE", state(view, "git1"), "Teil 1 ist mit zwei Aufgaben fertig");
        assertEquals("UPCOMING", state(view, "git2"));
        assertEquals("datenbanken", body(getAs(as(lena), "/api/learning/next")).get("moduleSlug").asString(),
                "Erst Datenbanken, dann Git Teil 2");

        var badTasks = new java.util.HashMap<>(git1);
        badTasks.put("tasks", List.of("gibt-es-nicht"));
        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of("graph", Map.of("nodes", List.of(badTasks), "edges", List.of())))
                .andExpect(status().isBadRequest());
    }

    static JsonNode module(JsonNode modules, String slug) {
        for (JsonNode m : modules) if (m.get("slug").asString().equals(slug)) return m;
        throw new AssertionError("Modul " + slug + " fehlt");
    }

    @Test
    void nothingToRecommendWithoutReleasedModules() throws Exception {
        User tom = student("tom", course("8b"));
        getAs(as(tom), "/api/learning/next").andExpect(status().isNoContent());
    }

    static JsonNode find(JsonNode view, String id) {
        for (JsonNode node : view.get("nodes")) if (node.get("id").asString().equals(id)) return node;
        throw new AssertionError("Station " + id + " fehlt");
    }

    static String state(JsonNode view, String id) {
        return find(view, id).get("state").asString();
    }
}

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

    static Map<String, Object> requiredEdge(String from, String to) {
        return Map.of("from", from, "to", to, "required", true);
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
                        node("t1", "TASK", "git", "g1", null, 100), node("t2", "TASK", "git", "g5", "Zweite Frage", 200),
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
        assertEquals("g5", next.get("taskSlug").asString());
        assertEquals("git-pfad", next.get("pathSlug").asString());

        // Pfad geschafft (gesperrte Stationen halten nicht auf): weiter mit den übrigen Aufgaben im begonnenen Modul
        postAs(as(lena), "/api/modules/git/g5/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        view = body(getAs(as(lena), "/api/learning-paths/git-pfad"));
        assertEquals("DONE", state(view, "ziel"));
        assertTrue(view.get("recommendation").isNull());
        next = body(getAs(as(lena), "/api/learning/next"));
        assertEquals("git", next.get("moduleSlug").asString());
        assertNotEquals("g5", next.get("taskSlug").asString());
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
        git1.put("tasks", List.of("g1", "g5"));
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
            boolean used = List.of("g1", "g5", "g3").contains(task.get("slug").asString());
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
        postAs(as(lena), "/api/modules/git/g5/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
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

    @Test
    void enforcedPathBlocksSuccessorsAndUnlocksRestAfterStation() throws Exception {
        Course course = course("8c");
        User lena = student("lena", course);
        User tom = student("tom", course);
        long id = create("Pflichtpfad", "pflichtpfad");

        var graph = Map.of(
                "nodes", List.of(
                        node("t1", "TASK", "git", "g1", "Aufgabe 1", 0),
                        node("t2", "TASK", "git", "g5", "Aufgabe 2", 100),
                        node("t3", "TASK", "git", "g3", "Aufgabe 3", 200),
                        node("opt", "TASK", "git", "g6", "Zusatz", 250),
                        node("frei", "MILESTONE", null, null, "Freies Arbeiten", 300)),
                "edges", List.of(
                        requiredEdge("t1", "t2"),
                        requiredEdge("t2", "t3"),
                        edge("t1", "opt"),
                        requiredEdge("t3", "frei")));

        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of(
                "graph", graph,
                "courseId", course.getId(),
                "enforce", true,
                "unlockRestAfter", "frei",
                "published", true)).andExpect(status().isOk());

        // Vor Erledigung von Aufgabe 1: Aufgabe 2 & 3 sind BLOCKED, Empfehlungs-Kante (opt) ist UPCOMING & bearbeitbar
        JsonNode view = body(getAs(as(lena), "/api/learning-paths/pflichtpfad"));
        assertTrue(view.get("enforce").asBoolean());
        assertEquals("AVAILABLE", state(view, "t1"));
        assertEquals("BLOCKED", state(view, "t2"));
        assertTrue(find(view, "t2").get("href").isNull());
        assertEquals("Erst „Aufgabe 1“ abschließen.", find(view, "t2").get("reason").asString());
        assertEquals("BLOCKED", state(view, "t3"));
        assertEquals("UPCOMING", state(view, "opt"), "Empfehlungs-Verbindung blockiert nicht");
        assertFalse(find(view, "opt").get("href").isNull());
        assertEquals("BLOCKED", state(view, "frei"));

        // Vorschau der Lehrkraft „als Schüler“
        JsonNode previewAsLena = body(getAs(teacher(), "/api/learning-paths/pflichtpfad?student=lena").andExpect(status().isOk()));
        assertEquals("BLOCKED", state(previewAsLena, "t2"));

        // Serverseitige Durchsetzung: Aufgabe 2 weder lesbar noch abgebbar, Tipp/Lösung abgewiesen
        getAs(as(lena), "/api/modules/git/g1").andExpect(status().isOk());
        getAs(as(lena), "/api/modules/git/g6").andExpect(status().isOk());
        getAs(as(lena), "/api/modules/git/g5").andExpect(status().isForbidden());
        postAs(as(lena), "/api/modules/git/g5/submit", Map.of("selected", List.of("b"))).andExpect(status().isForbidden());
        postAs(as(lena), "/api/modules/git/g5/hint", null).andExpect(status().isForbidden());
        postAs(as(lena), "/api/modules/git/g5/reveal", null).andExpect(status().isForbidden());

        // Aufgaben & Module außerhalb des Pfads sind vor Erreichen von „frei“ gesperrt
        getAs(as(lena), "/api/modules/datenbanken").andExpect(status().isForbidden());
        JsonNode gitDetail = body(getAs(as(lena), "/api/modules/git").andExpect(status().isOk()));
        assertEquals(4, gitDetail.get("tasks").size(), "Nur die 4 Aufgaben des Pfads sind vorab im Modul sichtbar");
        for (JsonNode t : gitDetail.get("tasks")) {
            if (t.get("slug").asString().equals("g5")) {
                assertTrue(t.get("isBlocked").asBoolean());
                assertEquals("Erst „Aufgabe 1“ abschließen.", t.get("blockedReason").asString());
            }
        }

        // Schritt für Schritt lösen: 1 → 2 → 3
        postAs(as(lena), "/api/modules/git/g1/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        assertEquals("g5", body(getAs(as(lena), "/api/learning/next")).get("taskSlug").asString());
        getAs(as(lena), "/api/modules/git/g5").andExpect(status().isOk());
        getAs(as(lena), "/api/modules/git/g3").andExpect(status().isForbidden());

        postAs(as(lena), "/api/modules/git/g5/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        assertEquals("g3", body(getAs(as(lena), "/api/learning/next")).get("taskSlug").asString());
        getAs(as(lena), "/api/modules/git/g3").andExpect(status().isOk());

        postAs(as(lena), "/api/modules/git/g3/submit", Map.of("answers", List.of("Working Directory", "Staging Area", "Repository"))).andExpect(status().isOk());

        // Meilenstein „frei“ erreicht → alle übrigen Module und Aufgaben sind für Lena frei (ohne Änderung an module_releases!)
        view = body(getAs(as(lena), "/api/learning-paths/pflichtpfad"));
        assertEquals("DONE", state(view, "frei"));
        getAs(as(lena), "/api/modules/datenbanken").andExpect(status().isOk());
        assertTrue(body(getAs(as(lena), "/api/modules")).size() > 1, "Übrige Module sind für Lena freigeschaltet");
        JsonNode dbModuleInAdmin = module(body(getAs(teacher(), "/api/admin/modules?courseId=" + course.getId())), "datenbanken");
        assertFalse(dbModuleInAdmin.get("isUnlocked").asBoolean(), "module_releases bleibt unverändert");

        // Für Tom im selben Kurs ist Datenbanken weiterhin gesperrt
        getAs(as(tom), "/api/modules/datenbanken").andExpect(status().isForbidden());
    }

    @Test
    void rejectsSecondEnforcedPathAndHandlesDuplicateTasksAndKnowledgeVisibility() throws Exception {
        Course courseA = course("10a");
        Course courseB = course("10b");
        unlock(courseA, "git");
        unlock(courseA, "datenbanken");
        User lena = student("lena", courseA);

        long p1 = create("Pfad A", "pfad-a");
        var graph = Map.of(
                "nodes", List.of(
                        node("n1", "TASK", "git", "g1", "Start", 0),
                        node("n2", "TASK", "git", "g1", "Wiederholung", 100),
                        node("db", "MODULE", "datenbanken", null, "Datenbanken", 200)),
                "edges", List.of(requiredEdge("n1", "n2"), requiredEdge("n1", "db")));
        putAs(teacher(), "/api/admin/learning-paths/" + p1, Map.of(
                "graph", graph, "courseId", courseA.getId(), "enforce", true, "published", true))
                .andExpect(status().isOk());

        // Ungültige unlockRestAfter-Station wird abgelehnt
        putAs(teacher(), "/api/admin/learning-paths/" + p1, Map.of("unlockRestAfter", "gibt-es-nicht"))
                .andExpect(status().isBadRequest());

        // Zweiter verbindlicher Pfad im selben Kurs oder für alle Kurse wird abgelehnt; anderer Kurs erlaubt
        long p2 = create("Pfad A2", "pfad-a2");
        putAs(teacher(), "/api/admin/learning-paths/" + p2, Map.of("courseId", courseA.getId(), "enforce", true))
                .andExpect(status().isConflict());
        putAs(teacher(), "/api/admin/learning-paths/" + p2, Map.of("courseId", 0, "enforce", true))
                .andExpect(status().isConflict());
        putAs(teacher(), "/api/admin/learning-paths/" + p2, Map.of("courseId", courseB.getId(), "enforce", true))
                .andExpect(status().isOk());

        // Aufgabe g1 kommt in n1 (AVAILABLE) und n2 (BLOCKED) vor → trotzdem bearbeitbar
        getAs(as(lena), "/api/modules/git/g1").andExpect(status().isOk());

        // Modul „datenbanken“ ist komplett durch n1 blockiert → Wissensartikel zu datenbanken noch nicht sichtbar
        JsonNode articlesBefore = body(getAs(as(lena), "/api/knowledge"));
        assertFalse(hasArticleForModule(articlesBefore, "datenbanken"), "Artikel eines komplett blockierten Moduls ist noch ausgeblendet");

        // Nach Erledigung von n1 wird Modul „datenbanken“ frei → Wissensartikel sichtbar
        postAs(as(lena), "/api/modules/git/g1/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        JsonNode articlesAfter = body(getAs(as(lena), "/api/knowledge"));
        assertTrue(hasArticleForModule(articlesAfter, "datenbanken"), "Artikel wird mit Freischaltung der Station sichtbar");
    }

    static boolean hasArticleForModule(JsonNode articles, String moduleSlug) {
        for (JsonNode a : articles) {
            if (moduleSlug.equals(a.path("moduleSlug").asString(null))) return true;
        }
        return false;
    }

    static JsonNode find(JsonNode view, String id) {
        for (JsonNode node : view.get("nodes")) if (node.get("id").asString().equals(id)) return node;
        throw new AssertionError("Station " + id + " fehlt");
    }

    static String state(JsonNode view, String id) {
        return find(view, id).get("state").asString();
    }
}

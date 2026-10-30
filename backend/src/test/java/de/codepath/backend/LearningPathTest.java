package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import de.codepath.backend.progress.PointsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lernpfade: Editor der Lehrkraft, Karte mit Stand und empfohlene nächste Aufgabe. */
class LearningPathTest extends IntegrationTestBase {

    @Autowired
    private PointsService pointsService;

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

    @Test
    void andOrGatesEvaluateInputsLockedBranchesAndRecommendation() throws Exception {
        Course course = course("10c");
        unlock(course, "git");
        User lena = student("lena", course);
        long id = create("Logikpfad", "logikpfad");

        // Ungültige Gatter ablehnen: < 2 Eingänge, < 1 Ausgang, ungültiges min
        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of("graph", Map.of(
                "nodes", List.of(
                        node("t1", "TASK", "git", "g1", "A1", 0),
                        node("g", "AND", null, null, "Zu wenig Eingänge", 100),
                        node("t2", "TASK", "git", "g5", "Ziel", 200)),
                "edges", List.of(requiredEdge("t1", "g"), requiredEdge("g", "t2")))))
                .andExpect(status().isBadRequest());

        Map<String, Object> badOr = new java.util.HashMap<>(node("g", "OR", null, null, "Ungültiges min", 100));
        badOr.put("min", 3);
        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of("graph", Map.of(
                "nodes", List.of(
                        node("t1", "TASK", "git", "g1", "A1", 0),
                        node("t2", "TASK", "git", "g5", "A2", 0),
                        badOr,
                        node("t3", "TASK", "git", "g3", "Ziel", 200)),
                "edges", List.of(requiredEdge("t1", "g"), requiredEdge("t2", "g"), requiredEdge("g", "t3")))))
                .andExpect(status().isBadRequest());

        // Gültiger Pfad mit ODER-Verzweigung (2 Zweige à 2 Aufgaben), UND-Zusammenführung und 2-von-3-ODER
        Map<String, Object> orMin2 = new java.util.HashMap<>(node("or2", "OR", null, null, "2 von 3", 100));
        orMin2.put("min", 2);
        var graph = Map.of(
                "nodes", List.of(
                        // Zweig B liegt höher auf der Karte (y=0), Zweig A tiefer (y=50)
                        node("b1", "TASK", "git", "g5", "Zweig B1", 0),
                        node("b2", "TASK", "git", "g6", "Zweig B2", 150),
                        node("a1", "TASK", "git", "g1", "Zweig A1", 50),
                        node("a2", "TASK", "git", "g3", "Zweig A2", 150),
                        node("or1", "OR", null, null, null, 250),
                        // Gesperrtes Modul „datenbanken“ als zweiter Eingang an einem weiteren ODER-Gatter
                        node("lockedMod", "MODULE", "datenbanken", null, "Gesperrt", 250),
                        node("orLocked", "OR", null, null, null, 350),
                        node("and1", "AND", null, null, null, 450),
                        node("ziel", "TASK", "git", "g7", "Abschluss", 550)),
                "edges", List.of(
                        requiredEdge("b1", "b2"),
                        requiredEdge("a1", "a2"),
                        requiredEdge("b2", "or1"),
                        requiredEdge("a2", "or1"),
                        requiredEdge("or1", "orLocked"),
                        requiredEdge("lockedMod", "orLocked"),
                        requiredEdge("orLocked", "and1"),
                        requiredEdge("or1", "and1"),
                        requiredEdge("and1", "ziel")));

        // Hinweis: ohne courseId testen wir auch die Handhabung gesperrter Module (datenbanken ist nicht freigegeben)
        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of(
                "graph", graph, "enforce", true, "published", true)).andExpect(status().isOk());

        JsonNode view = body(getAs(as(lena), "/api/learning-paths/logikpfad"));
        assertEquals(6, view.get("stations").asInt(), "Gatter zählen nicht als Station");
        assertEquals("BLOCKED", state(view, "or1"));
        assertEquals(0, find(view, "or1").get("done").asInt());
        assertEquals(2, find(view, "or1").get("total").asInt());
        assertEquals("BLOCKED", state(view, "orLocked"), "Gesperrter Eingang an ODER öffnet das Gatter nicht sofort");
        assertEquals("BLOCKED", state(view, "and1"));
        assertEquals("BLOCKED", state(view, "ziel"));

        // Bei Gleichstand (0 Fortschritt in beiden Zweigen) entscheidet die Lage auf der Karte -> b1 (y=0 vor a1 y=50)
        assertEquals("g5", view.at("/recommendation/taskSlug").asString());

        // Lena löst stattdessen a1 im Zweig A -> Zweig A hat nun mehr Fortschritt (1 > 0), daher wird a2 (g3) empfohlen!
        postAs(as(lena), "/api/modules/git/g1/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        view = body(getAs(as(lena), "/api/learning-paths/logikpfad"));
        assertEquals("g3", view.at("/recommendation/taskSlug").asString(), "Zweig mit dem meisten Fortschritt wird empfohlen");
        assertEquals("BLOCKED", state(view, "or1"));

        // Lena schließt Zweig A mit a2 (g3) ab -> or1 (1 von 2), orLocked und and1 sind erfüllt, ziel wird AVAILABLE!
        postAs(as(lena), "/api/modules/git/g3/submit", Map.of("answers", List.of("Working Directory", "Staging Area", "Repository")))
                .andExpect(status().isOk());
        view = body(getAs(as(lena), "/api/learning-paths/logikpfad"));
        assertEquals("DONE", state(view, "or1"));
        assertEquals(1, find(view, "or1").get("done").asInt());
        assertEquals("DONE", state(view, "orLocked"));
        assertEquals("DONE", state(view, "and1"));
        assertEquals("AVAILABLE", state(view, "ziel"));

        // Test für ODER mit min = 2 (2 von 3 Eingängen erforderlich)
        var graphMin2 = Map.of(
                "nodes", List.of(
                        node("x1", "TASK", "git", "g1", "X1", 0),
                        node("x2", "TASK", "git", "g5", "X2", 0),
                        node("x3", "TASK", "git", "g6", "X3", 0),
                        orMin2,
                        node("end", "TASK", "git", "g7", "Ende", 200)),
                "edges", List.of(
                        requiredEdge("x1", "or2"),
                        requiredEdge("x2", "or2"),
                        requiredEdge("x3", "or2"),
                        requiredEdge("or2", "end")));
        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of("graph", graphMin2)).andExpect(status().isOk());

        // x1 (g1) ist schon erledigt (1 von 2) -> or2 und end noch BLOCKED
        view = body(getAs(as(lena), "/api/learning-paths/logikpfad"));
        assertEquals("BLOCKED", state(view, "or2"));
        assertEquals(1, find(view, "or2").get("done").asInt());
        assertEquals(2, find(view, "or2").get("min").asInt());
        assertEquals("BLOCKED", state(view, "end"));

        // Nach x2 (g5) sind 2 von 3 erledigt -> or2 ist DONE, end ist AVAILABLE
        postAs(as(lena), "/api/modules/git/g5/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        view = body(getAs(as(lena), "/api/learning-paths/logikpfad"));
        assertEquals("DONE", state(view, "or2"));
        assertEquals(2, find(view, "or2").get("done").asInt());
        assertEquals("AVAILABLE", state(view, "end"));
    }

    @Test
    void choiceNodeLocksOtherBranchesMergesAndCleansUpOnCourseEnd() throws Exception {
        Course course = course("10d");
        User lena = student("lena", course);
        long id = body(postAs(teacher(), "/api/admin/learning-paths",
                Map.of("title", "Weichen-Pfad", "slug", "weichenpfad")).andExpect(status().isOk())).get("id").asLong();

        // Ungültige Weiche ablehnen: < 2 Ausgänge oder fehlendes Label an einer ausgehenden Kante
        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of("graph", Map.of(
                "nodes", List.of(
                        node("c", "CHOICE", null, null, "Zu wenig Wege", 0),
                        node("a", "TASK", "git", "g1", "Einziger Weg", 100)),
                "edges", List.of(Map.of("from", "c", "to", "a", "required", true, "label", "Nur A")))))
                .andExpect(status().isBadRequest());

        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of("graph", Map.of(
                "nodes", List.of(
                        node("c", "CHOICE", null, null, "Ohne Label", 0),
                        node("a", "TASK", "git", "g1", "Weg A", 100),
                        node("b", "TASK", "git", "g5", "Weg B", 100)),
                "edges", List.of(
                        Map.of("from", "c", "to", "a", "required", true, "label", "Weg A"),
                        Map.of("from", "c", "to", "b", "required", true)))))
                .andExpect(status().isBadRequest());

        // Gültiger Pfad: Start (git/g1) -> Weiche c -> Weg A (git/g5 -> git/g6) ODER Weg B (Modul datenbanken) -> Zusammenführung (git/g3)
        // Zusätzlich: Aufgabe git/g6 kommt SOWOHL im nicht gewählten Zweig als auch hinter der Zusammenführung vor,
        // um zu prüfen, dass eine Aufgabe nur gesperrt wird, wenn ALLE ihre Stationen EXCLUDED sind.
        var graph = Map.of(
                "nodes", List.of(
                        node("start", "TASK", "git", "g1", "Einstieg", 0),
                        node("weiche", "CHOICE", null, null, "Schwerpunkt wählen", 100),
                        node("wegA", "TASK", "git", "g5", "Git-Vertiefung", 200),
                        node("wegB", "MODULE", "datenbanken", null, "Datenbanken-Pfad", 200),
                        node("wegBTask", "TASK", "git", "g7", "Nur in B", 250),
                        node("wegB2", "TASK", "git", "g6", "Zusatz in B", 300),
                        node("merge", "TASK", "git", "g3", "Gemeinsamer Abschluss", 400),
                        node("sharedG6", "TASK", "git", "g6", "Gemeinsame Nachbereitung", 500)),
                "edges", List.of(
                        requiredEdge("start", "weiche"),
                        Map.of("from", "weiche", "to", "wegA", "required", true, "label", "Git-Weg", "hint", "Kompakt"),
                        Map.of("from", "weiche", "to", "wegB", "required", true, "label", "Datenbank-Weg", "hint", "Ausführlich"),
                        requiredEdge("wegB", "wegBTask"),
                        requiredEdge("wegBTask", "wegB2"),
                        requiredEdge("wegA", "merge"),
                        requiredEdge("wegB2", "merge"),
                        requiredEdge("merge", "sharedG6")));

        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of(
                "courseId", course.getId(),
                "published", true,
                "enforce", true,
                "unlockRestAfter", "merge",
                "graph", graph)).andExpect(status().isOk());

        // 1. Vor Abschluss von "start" ist die Weiche BLOCKED -> Wahl wird mit 403 abgelehnt
        JsonNode view = body(getAs(as(lena), "/api/learning-paths/weichenpfad"));
        assertEquals("AVAILABLE", state(view, "start"));
        assertEquals("BLOCKED", state(view, "weiche"));
        assertEquals("BLOCKED", state(view, "wegA"));
        assertEquals("BLOCKED", state(view, "wegB"));
        assertEquals("BLOCKED", state(view, "merge"));
        postAs(as(lena), "/api/learning-paths/weichenpfad/choices", Map.of("nodeId", "weiche", "to", "wegA"))
                .andExpect(status().isForbidden());

        // 2. Lena löst "start" (git/g1) -> Weiche ist AVAILABLE, aber beide Wege vor der Wahl noch BLOCKED!
        postAs(as(lena), "/api/modules/git/g1/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        view = body(getAs(as(lena), "/api/learning-paths/weichenpfad"));
        assertEquals("DONE", state(view, "start"));
        assertEquals("AVAILABLE", state(view, "weiche"));
        assertEquals(2, find(view, "weiche").get("options").size());
        assertEquals("BLOCKED", state(view, "wegA"));
        assertTrue(find(view, "wegA").get("reason").asString().contains("Schwerpunkt wählen"));
        assertEquals("BLOCKED", state(view, "wegB"));
        assertEquals("BLOCKED", state(view, "merge"));
        getAs(as(lena), "/api/modules/git/g5").andExpect(status().isForbidden());
        getAs(as(lena), "/api/modules/datenbanken/db1").andExpect(status().isForbidden());

        // 3. Lena wählt "wegA" -> wegA wird AVAILABLE, wegB und wegB2 werden EXCLUDED!
        view = body(postAs(as(lena), "/api/learning-paths/weichenpfad/choices", Map.of("nodeId", "weiche", "to", "wegA"))
                .andExpect(status().isOk()));
        assertEquals("DONE", state(view, "weiche"));
        assertEquals("wegA", find(view, "weiche").get("chosenTo").asString());
        assertEquals("AVAILABLE", state(view, "wegA"));
        assertEquals("EXCLUDED", state(view, "wegB"));
        assertEquals("EXCLUDED", state(view, "wegB2"));
        assertEquals("BLOCKED", state(view, "merge"), "Zusammenführung wartet noch auf den gewählten Zweig wegA");

        // Zweite Wahl oder Wechsel durch Schülerin wird mit 409 Conflict abgelehnt
        postAs(as(lena), "/api/learning-paths/weichenpfad/choices", Map.of("nodeId", "weiche", "to", "wegB"))
                .andExpect(status().isConflict());

        // Nicht gewählter Zweig (Modul datenbanken) ist überall unsichtbar und per API/Wissensartikel mit 403 gesperrt
        JsonNode modules = body(getAs(as(lena), "/api/modules"));
        for (JsonNode m : modules) {
            assertNotEquals("datenbanken", m.get("slug").asString(), "Ausgeschlossenes Modul darf nicht in der Übersicht erscheinen");
        }
        JsonNode forbiddenMod = body(getAs(as(lena), "/api/modules/datenbanken").andExpect(status().isForbidden()));
        assertEquals("Du hast dich für einen anderen Weg entschieden.", forbiddenMod.get("message").asString());
        JsonNode forbiddenTask = body(getAs(as(lena), "/api/modules/git/g7").andExpect(status().isForbidden()));
        assertEquals("Du hast dich für einen anderen Weg entschieden.", forbiddenTask.get("message").asString());
        postAs(as(lena), "/api/modules/git/g7/submit", Map.of("selected", List.of("a"))).andExpect(status().isForbidden());
        assertFalse(hasArticleForModule(body(getAs(as(lena), "/api/knowledge")), "datenbanken"));

        // 4. Lena schließt wegA (git/g5) ab -> Zusammenführung "merge" (git/g3) wird AVAILABLE!
        postAs(as(lena), "/api/modules/git/g5/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        view = body(getAs(as(lena), "/api/learning-paths/weichenpfad"));
        assertEquals("DONE", state(view, "wegA"));
        assertEquals("AVAILABLE", state(view, "merge"), "EXCLUDED-Vorgänger wegB2 hält die Zusammenführung nicht auf");

        // 5. Lena schließt "merge" (git/g3) ab -> unlockRestAfter ("merge") gibt Module außerhalb frei,
        //    ABER das ausgeschlossene Modul "datenbanken" bleibt weiterhin gesperrt!
        //    Und git/g6 (kommt in wegB2 [EXCLUDED] UND sharedG6 [AVAILABLE] vor) ist frei!
        postAs(as(lena), "/api/modules/git/g3/submit", Map.of("answers", List.of("Working Directory", "Staging Area", "Repository")))
                .andExpect(status().isOk());
        view = body(getAs(as(lena), "/api/learning-paths/weichenpfad"));
        assertEquals("DONE", state(view, "merge"));
        assertEquals("AVAILABLE", state(view, "sharedG6"));
        getAs(as(lena), "/api/modules/git/g6").andExpect(status().isOk());
        assertTrue(body(getAs(as(lena), "/api/modules")).size() > 1, "Übrige Module außerhalb des Pfads sind freigeschaltet");
        getAs(as(lena), "/api/modules/datenbanken").andExpect(status().isForbidden());

        // 6. Editor-Warnung bei bestehenden Schüler-Wahlen prüfen
        JsonNode adminAfterChoice = body(getAs(teacher(), "/api/admin/learning-paths/" + id));
        assertTrue(adminAfterChoice.get("warnings").toString().contains("1 Schüler haben an dieser Weiche"));

        // 7. Kurs beenden löscht learning_path_choices der Kursmitglieder
        postAs(teacher(), "/api/admin/courses/" + course.getId() + "/end", Map.of("confirmation", "10d"))
                .andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("select count(*) from learning_path_choices", Integer.class));
    }

    @Test
    void subpathsWorkAsBuildingBlocksWithChoiceAndProtectAgainstCyclesDepthAndDelete() throws Exception {
        Course course = course("11a");
        User max = student("max_subpath", course);

        long cloudId = body(postAs(teacher(), "/api/admin/learning-paths", Map.of("title", "Schwerpunkt Cloud", "slug", "cloud-block"))
                .andExpect(status().isOk())).get("id").asLong();
        putAs(teacher(), "/api/admin/learning-paths/" + cloudId, Map.of(
                "published", true,
                "templateOnly", true,
                "enforce", false,
                "graph", Map.of(
                        "nodes", List.of(
                                node("c1", "TASK", "git", "g5", null, 0),
                                node("c2", "TASK", "git", "g3", null, 120)),
                        "edges", List.of(
                                requiredEdge("c1", "c2"))))).andExpect(status().isOk());

        long webId = body(postAs(teacher(), "/api/admin/learning-paths", Map.of("title", "Schwerpunkt Web", "slug", "web-block"))
                .andExpect(status().isOk())).get("id").asLong();
        putAs(teacher(), "/api/admin/learning-paths/" + webId, Map.of(
                "published", true,
                "templateOnly", true,
                "graph", Map.of(
                        "nodes", List.of(
                                node("w1", "MODULE", "datenbanken", null, null, 0)),
                        "edges", List.of()))).andExpect(status().isOk());

        // Selbst-Referenz wird abgelehnt (400)
        putAs(teacher(), "/api/admin/learning-paths/" + cloudId, Map.of(
                "graph", Map.of(
                        "nodes", List.of(pathNode("self", "cloud-block", 0)),
                        "edges", List.of()))).andExpect(status().isBadRequest());

        // Hauptpfad: Aufgabe 1 -> Weiche -> Baustein Cloud | Baustein Web -> Meilenstein
        long mainId = body(postAs(teacher(), "/api/admin/learning-paths", Map.of("title", "Hauptpfad", "slug", "hauptpfad"))
                .andExpect(status().isOk())).get("id").asLong();
        putAs(teacher(), "/api/admin/learning-paths/" + mainId, Map.of(
                "courseId", course.getId(),
                "published", true,
                "enforce", true,
                "lockOthers", true,
                "graph", Map.of(
                        "nodes", List.of(
                                node("start", "TASK", "git", "g1", null, 0),
                                node("weiche", "CHOICE", null, null, "Schwerpunkt wählen", 100),
                                pathNode("pCloud", "cloud-block", 220),
                                pathNode("pWeb", "web-block", 220),
                                node("ziel", "MILESTONE", null, null, "Abschluss", 360)),
                        "edges", List.of(
                                requiredEdge("start", "weiche"),
                                Map.of("from", "weiche", "to", "pCloud", "required", true, "label", "Cloud"),
                                Map.of("from", "weiche", "to", "pWeb", "required", true, "label", "Web"),
                                requiredEdge("pCloud", "ziel"),
                                requiredEdge("pWeb", "ziel"))))).andExpect(status().isOk());

        // Zyklische Einbindung (cloud-block -> hauptpfad) wird mit 400 abgelehnt
        putAs(teacher(), "/api/admin/learning-paths/" + cloudId, Map.of(
                "graph", Map.of(
                        "nodes", List.of(pathNode("cyc", "hauptpfad", 0)),
                        "edges", List.of()))).andExpect(status().isBadRequest());

        // Bis zu 3 Verschachtelungsebenen (l0 -> l1 -> hauptpfad -> cloud-block = Tiefe 3) sind erlaubt, Tiefe 4 wird mit 400 abgelehnt
        long l1Id = body(postAs(teacher(), "/api/admin/learning-paths", Map.of("title", "Ebene 1", "slug", "ebene-1"))
                .andExpect(status().isOk())).get("id").asLong();
        putAs(teacher(), "/api/admin/learning-paths/" + l1Id, Map.of(
                "templateOnly", true,
                "graph", Map.of("nodes", List.of(pathNode("sub", "hauptpfad", 0)), "edges", List.of())))
                .andExpect(status().isOk());
        long l0Id = body(postAs(teacher(), "/api/admin/learning-paths", Map.of("title", "Ebene 0", "slug", "ebene-0"))
                .andExpect(status().isOk())).get("id").asLong();
        putAs(teacher(), "/api/admin/learning-paths/" + l0Id, Map.of(
                "templateOnly", true,
                "graph", Map.of("nodes", List.of(pathNode("sub0", "ebene-1", 0)), "edges", List.of())))
                .andExpect(status().isOk());
        long tooDeepId = body(postAs(teacher(), "/api/admin/learning-paths", Map.of("title", "Zu tief", "slug", "zu-tief"))
                .andExpect(status().isOk())).get("id").asLong();
        putAs(teacher(), "/api/admin/learning-paths/" + tooDeepId, Map.of(
                "templateOnly", true,
                "graph", Map.of("nodes", List.of(pathNode("subDeep", "ebene-0", 0)), "edges", List.of())))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/admin/learning-paths/" + tooDeepId).with(teacher()).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/learning-paths/" + l0Id).with(teacher()).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/learning-paths/" + l1Id).with(teacher()).with(csrf())).andExpect(status().isNoContent());

        // Löschschutz: cloud-block wird in Hauptpfad genutzt -> 409 Conflict mit Hinweis auf "Hauptpfad"
        JsonNode delErr = body(mvc.perform(delete("/api/admin/learning-paths/" + cloudId).with(teacher()).with(csrf()))
                .andExpect(status().isConflict()));
        assertTrue(delErr.get("message").asString().contains("Hauptpfad"));

        // templateOnly-Bausteine tauchen nicht in der Schüler-Auswahl (/api/learning-paths) auf
        JsonNode studentPaths = body(getAs(as(max), "/api/learning-paths"));
        assertEquals(1, studentPaths.size());
        assertEquals("hauptpfad", studentPaths.get(0).get("slug").asString());

        // Vor Abschluss von "start" und Wahl an "weiche" sind beide Bausteine und ihre Aufgaben gesperrt (403)
        JsonNode view = body(getAs(as(max), "/api/learning-paths/hauptpfad"));
        assertEquals("AVAILABLE", state(view, "start"));
        assertEquals("BLOCKED", state(view, "weiche"));
        assertEquals("BLOCKED", state(view, "pCloud"));
        assertEquals("BLOCKED", state(view, "pWeb"));
        getAs(as(max), "/api/learning-paths/cloud-block").andExpect(status().isForbidden());
        getAs(as(max), "/api/modules/git/g5").andExpect(status().isForbidden());
        getAs(as(max), "/api/modules/datenbanken").andExpect(status().isOk()); // vor Wahl BLOCKED im Pfad, Aufgaben darin gesperrt

        // Max löst "start" (git/g1) und wählt "Cloud" (pCloud)
        postAs(as(max), "/api/modules/git/g1/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        view = body(postAs(as(max), "/api/learning-paths/hauptpfad/choices", Map.of("nodeId", "weiche", "to", "pCloud"))
                .andExpect(status().isOk()));
        assertEquals("DONE", state(view, "weiche"));
        assertEquals("AVAILABLE", state(view, "pCloud"));
        assertEquals("cloud-block", find(view, "pCloud").get("path").asString());
        assertEquals(0, find(view, "pCloud").get("done").asInt());
        assertEquals(2, find(view, "pCloud").get("total").asInt());
        assertEquals("EXCLUDED", state(view, "pWeb"));
        assertEquals("BLOCKED", state(view, "ziel"));

        // Nicht gewählter Baustein (web-block / Modul datenbanken) ist gesperrt (403)
        getAs(as(max), "/api/learning-paths/web-block").andExpect(status().isForbidden());
        getAs(as(max), "/api/modules/datenbanken").andExpect(status().isForbidden());

        // Gewählter Baustein (cloud-block) ist für Max einsehbar und erbt Verbindlichkeit vom Oberpfad
        JsonNode subView = body(getAs(as(max), "/api/learning-paths/cloud-block").andExpect(status().isOk()));
        assertTrue(subView.get("enforce").asBoolean(), "Baustein erbt Verbindlichkeit vom verbindlichen Oberpfad");
        assertEquals("AVAILABLE", state(subView, "c1"));
        assertEquals("BLOCKED", state(subView, "c2"));
        getAs(as(max), "/api/modules/git/g5").andExpect(status().isOk());
        getAs(as(max), "/api/modules/git/g3").andExpect(status().isForbidden());

        // Nächste empfohlene Aufgabe auf dem Hauptpfad zeigt auf c1 (git/g5) im Baustein
        JsonNode next = body(getAs(as(max), "/api/learning/next"));
        assertEquals("g5", next.get("taskSlug").asString());

        // Max löst c1 (git/g5) und c2 (git/g3) -> Baustein Cloud und Meilenstein "ziel" sind DONE!
        postAs(as(max), "/api/modules/git/g5/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        getAs(as(max), "/api/modules/git/g3").andExpect(status().isOk());
        postAs(as(max), "/api/modules/git/g3/submit", Map.of("answers", List.of("Working Directory", "Staging Area", "Repository")))
                .andExpect(status().isOk());

        view = body(getAs(as(max), "/api/learning-paths/hauptpfad"));
        assertEquals("DONE", state(view, "pCloud"));
        assertEquals(2, find(view, "pCloud").get("done").asInt());
        assertEquals("DONE", state(view, "ziel"));
    }

    @Test
    void teacherCanResetChoicesUnlockBranchesUnlockStationsAndRunCourseActions() throws Exception {
        Course course = course("10a");
        User max = student("max", course);
        User lena = student("lena", course);

        long id = body(postAs(teacher(), "/api/admin/learning-paths",
                Map.of("title", "Weichen-Kurs", "slug", "weichen-kurs"))).get("id").asLong();

        // Intro (g1) -> Weiche (ch) -> Zweig A (g5) | Zweig B (g3) -> Finale (g6)
        Map<String, Object> graph = Map.of(
                "nodes", List.of(
                        node("intro", "TASK", "git", "g1", null, 80),
                        node("ch", "CHOICE", null, null, "Pfadwahl", 220),
                        node("branchA", "TASK", "git", "g5", null, 360),
                        node("branchB", "TASK", "git", "g3", null, 360),
                        node("finale", "TASK", "git", "g6", null, 520)
                ),
                "edges", List.of(
                        requiredEdge("intro", "ch"),
                        Map.of("from", "ch", "to", "branchA", "required", true, "label", "Weg A"),
                        Map.of("from", "ch", "to", "branchB", "required", true, "label", "Weg B"),
                        requiredEdge("branchA", "finale"),
                        requiredEdge("branchB", "finale")
                )
        );
        putAs(teacher(), "/api/admin/learning-paths/" + id, Map.of(
                "published", true,
                "enforce", true,
                "courseId", course.getId(),
                "lockOthers", true,
                "graph", graph
        )).andExpect(status().isOk());

        // Schüler dürfen keine Lehrkraft-Endpunkte aufrufen
        getAs(as(max), "/api/admin/students/max/learning-path").andExpect(status().isForbidden());
        postAs(as(max), "/api/admin/students/max/learning-paths/weichen-kurs/nodes/unlock",
                Map.of("nodeId", "intro")).andExpect(status().isForbidden());

        // 1. Station freischalten (UNLOCK_NODE): Lehrkraft schaltet "intro" für Max frei -> Weiche "ch" ist direkt AVAILABLE
        postAs(teacher(), "/api/admin/students/max/learning-paths/weichen-kurs/nodes/unlock",
                Map.of("nodeId", "intro")).andExpect(status().isNoContent());

        JsonNode maxView = body(getAs(as(max), "/api/learning-paths/weichen-kurs"));
        assertTrue(find(maxView, "intro").get("teacherUnlocked").asBoolean());
        assertEquals("AVAILABLE", state(maxView, "ch"), "Freigeschaltete Vorgänger-Station gilt als clear für Nachfolger");
        // Lena hat kein Override -> bei ihr ist ch weiter BLOCKED
        JsonNode lenaView = body(getAs(as(lena), "/api/learning-paths/weichen-kurs"));
        assertEquals("BLOCKED", state(lenaView, "ch"));

        // 2. Max wählt Weg A (branchA) und löst dort g5
        postAs(as(max), "/api/learning-paths/weichen-kurs/choices", Map.of("nodeId", "ch", "to", "branchA"))
                .andExpect(status().isOk());
        getAs(as(max), "/api/modules/git/g3").andExpect(status().isForbidden());
        postAs(as(max), "/api/modules/git/g5/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        long pointsAfterG5 = points(max);
        assertTrue(pointsAfterG5 > 0);

        // 3. Weg zusätzlich freischalten (UNLOCK_BRANCH): Lehrkraft schaltet branchB zusätzlich für Max frei
        postAs(teacher(), "/api/admin/students/max/learning-paths/weichen-kurs/branches/unlock",
                Map.of("nodeId", "ch", "to", "branchB")).andExpect(status().isNoContent());
        maxView = body(getAs(as(max), "/api/learning-paths/weichen-kurs"));
        assertEquals("DONE", state(maxView, "branchA"), "Gewählter Weg A bleibt erhalten");
        assertEquals("AVAILABLE", state(maxView, "branchB"), "Zusätzlich freigeschalteter Weg B ist ebenfalls offen");
        getAs(as(max), "/api/modules/git/g3").andExpect(status().isOk());

        // 4. Kursweite Weichen-Statistik prüfen
        JsonNode stats = body(getAs(teacher(), "/api/admin/learning-paths/" + id + "/choice-stats"));
        assertEquals(1, stats.size());
        assertEquals(2, stats.get(0).get("totalStudents").asInt());
        assertEquals(1, stats.get(0).get("openCount").asInt());
        assertEquals(1, stats.get(0).get("branches").get(0).get("count").asInt());
        assertEquals(1, stats.get(0).get("branches").get(1).get("unlockedCount").asInt());

        // 5. Wahl zurücksetzen MIT Fortschritt-Reset: löscht Wahl, entfernt UNLOCK_BRANCH und bucht Punkte für g5 per Korrektur zurück
        postAs(teacher(), "/api/admin/students/max/learning-paths/weichen-kurs/choices/reset",
                Map.of("nodeId", "ch", "resetProgress", true)).andExpect(status().isNoContent());
        assertEquals(0, points(max), "Punkte aus dem verlassenen Weg wurden über Korrekturbuchung zurückgenommen");
        maxView = body(getAs(as(max), "/api/learning-paths/weichen-kurs"));
        assertEquals("AVAILABLE", state(maxView, "ch"), "Max steht wieder vor der Weiche");
        assertEquals("BLOCKED", state(maxView, "branchA"));
        assertEquals("BLOCKED", state(maxView, "branchB"));

        // 6. Lehrkraft setzt Wahl für Max auf branchB (chosenBy = TEACHER)
        postAs(teacher(), "/api/admin/students/max/learning-paths/weichen-kurs/choices/set",
                Map.of("nodeId", "ch", "to", "branchB")).andExpect(status().isNoContent());
        JsonNode adminStudentView = body(getAs(teacher(), "/api/admin/students/max/learning-path"));
        assertEquals("branchB", adminStudentView.get("choices").get(0).get("chosenTo").asString());
        assertEquals("TEACHER", adminStudentView.get("choices").get(0).get("chosenBy").asString());
        getAs(as(max), "/api/modules/git/g3").andExpect(status().isOk());
        getAs(as(max), "/api/modules/git/g5").andExpect(status().isForbidden());

        // 7. Sammelaktion für den ganzen Kurs: für alle Schüler im Kurs Weg A setzen, danach für alle zurücksetzen
        postAs(teacher(), "/api/admin/learning-paths/" + id + "/course-actions",
                Map.of("action", "UNLOCK_NODE", "nodeId", "intro")).andExpect(status().isOk());
        postAs(teacher(), "/api/admin/learning-paths/" + id + "/course-actions",
                Map.of("action", "SET_CHOICE", "nodeId", "ch", "to", "branchA")).andExpect(status().isOk());
        lenaView = body(getAs(as(lena), "/api/learning-paths/weichen-kurs"));
        assertEquals("AVAILABLE", state(lenaView, "branchA"));
        assertEquals("EXCLUDED", state(lenaView, "branchB"));

        postAs(teacher(), "/api/admin/learning-paths/" + id + "/course-actions",
                Map.of("action", "RESET_CHOICE", "nodeId", "ch", "resetProgress", false)).andExpect(status().isOk());
        lenaView = body(getAs(as(lena), "/api/learning-paths/weichen-kurs"));
        assertEquals("AVAILABLE", state(lenaView, "ch"));

        // Audit-Log-Einträge vorhanden
        Integer auditCount = jdbc.queryForObject(
                "select count(*) from audit_events where action like 'LEARNING_PATH_%'", Integer.class);
        assertTrue(auditCount != null && auditCount >= 5);

        // Kurs beenden räumt auch learning_path_overrides auf
        postAs(teacher(), "/api/admin/courses/" + course.getId() + "/end", Map.of("confirmName", "10a"))
                .andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("select count(*) from learning_path_overrides", Integer.class));
    }

    public static Map<String, Object> pathNode(String id, String pathSlug, int y) {
        return Map.of("id", id, "kind", "PATH", "path", pathSlug, "x", 80, "y", y);
    }

    public static Map<String, Object> bonusNode(String id, String label, int points, int y) {
        return Map.of("id", id, "kind", "BONUS", "label", label, "points", points, "x", 100, "y", y);
    }

    @Test
    void bonusStationAwardsPointsOnceAndHandlesChoiceResetAndTeacherUnlock() throws Exception {
        Course course = course("9b");
        unlock(course, "git");
        User tim = student("tim", course);
        long id = create("Bonus-Pfad", "bonus-pfad");

        // 1. Validierung: Bonus-Station braucht Label und Punkte 1-1000
        var invalidNoLabel = Map.of("graph", Map.of(
                "nodes", List.of(Map.of("id", "b1", "kind", "BONUS", "points", 50, "x", 0, "y", 0)),
                "edges", List.of()));
        putAs(teacher(), "/api/admin/learning-paths/" + id, invalidNoLabel).andExpect(status().isBadRequest());

        var invalidPoints = Map.of("graph", Map.of(
                "nodes", List.of(Map.of("id", "b1", "kind", "BONUS", "label", "Extra", "points", 0, "x", 0, "y", 0)),
                "edges", List.of()));
        putAs(teacher(), "/api/admin/learning-paths/" + id, invalidPoints).andExpect(status().isBadRequest());

        // 2. Gültiger Pfad mit Weiche und Bonus-Stationen:
        // start (git/g1) -> bonus1 (50 P.) -> ch (Weiche) -> branchA (git/g5) -> bonusA (30 P.)
        //                                                 -> branchB (git/g3)
        var graph = Map.of(
                "nodes", List.of(
                        node("start", "TASK", "git", "g1", "Start-Aufgabe", 0),
                        bonusNode("bonus1", "Stern 1", 50, 100),
                        node("ch", "CHOICE", null, null, "Weiche", 200),
                        node("branchA", "TASK", "git", "g5", "Weg A", 300),
                        bonusNode("bonusA", "Stern A", 30, 400),
                        node("branchB", "TASK", "git", "g3", "Weg B", 300)
                ),
                "edges", List.of(
                        requiredEdge("start", "bonus1"),
                        requiredEdge("bonus1", "ch"),
                        Map.of("from", "ch", "to", "branchA", "required", true, "label", "Schwer"),
                        requiredEdge("branchA", "bonusA"),
                        Map.of("from", "ch", "to", "branchB", "required", true, "label", "Leicht")
                )
        );

        putAs(teacher(), "/api/admin/learning-paths/" + id,
                Map.of("graph", graph, "published", true, "enforce", true, "courseId", course.getId()))
                .andExpect(status().isOk());

        // Vor Lösung von g1: bonus1 ist BLOCKED, Punkte = 0
        JsonNode timView = body(getAs(as(tim), "/api/learning-paths/bonus-pfad"));
        assertEquals("BLOCKED", state(timView, "bonus1"));
        assertEquals(0, points(tim));

        // 3. Lösung von g1: ProgressCompletedEvent schaltet bonus1 frei und bucht 50 Punkte
        postAs(as(tim), "/api/modules/git/g1/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        long pointsAfterG1 = points(tim);
        // Punkte = Punkte für g1 + 50 Bonuspunkte
        long forTask = pointsService.forTask(tim.getId(), contentService.publishedTask("git", "g1").taskId());
        assertEquals(forTask + 50, pointsAfterG1);

        timView = body(getAs(as(tim), "/api/learning-paths/bonus-pfad"));
        assertEquals("DONE", state(timView, "bonus1"));
        assertTrue(find(timView, "bonus1").get("reason").asString().contains("+50"));

        // Zweiter Aufruf bleibt idempotent (keine doppelten Bonuspunkte)
        timView = body(getAs(as(tim), "/api/learning-paths/bonus-pfad"));
        assertEquals(pointsAfterG1, points(tim));

        // 4. Weichen-Optionen prüfen: Weg A enthält bonusA (30 Punkte), Weg B hat 0 Bonuspunkte
        JsonNode chNode = find(timView, "ch");
        assertEquals("AVAILABLE", chNode.get("state").asString());
        JsonNode optA = chNode.get("options").get(0);
        JsonNode optB = chNode.get("options").get(1);
        assertEquals("branchA", optA.get("to").asString());
        assertEquals(30, optA.get("bonusPoints").asInt());
        assertEquals("branchB", optB.get("to").asString());
        assertEquals(0, optB.get("bonusPoints").asInt());

        // 5. Tim wählt Weg A und löst g5 -> bonusA wird erreicht und bucht 30 Bonuspunkte
        postAs(as(tim), "/api/learning-paths/bonus-pfad/choices", Map.of("nodeId", "ch", "to", "branchA"))
                .andExpect(status().isOk());
        postAs(as(tim), "/api/modules/git/g5/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        long pointsAfterG5 = points(tim);
        long forG5 = pointsService.forTask(tim.getId(), contentService.publishedTask("git", "g5").taskId());
        assertEquals(pointsAfterG1 + forG5 + 30, pointsAfterG5);

        // 6. Lehrkraft setzt die Weiche zurück (resetProgress = false)
        // Bonuspunkte aus dem verlassenen Zweig (bonusA) müssen dennoch storniert werden (-30 P.)!
        postAs(teacher(), "/api/admin/students/tim/learning-paths/bonus-pfad/choices/reset",
                Map.of("nodeId", "ch", "resetProgress", false)).andExpect(status().isNoContent());
        assertEquals(pointsAfterG5 - 30, points(tim), "Bonus aus verlassenem Zweig wurde storniert");

        // 7. Manuelle Freischaltung durch Lehrkraft (UNLOCK_NODE): Station wird DONE, vergibt aber KEINE Bonuspunkte
        long pointsBeforeUnlock = points(tim);
        postAs(teacher(), "/api/admin/students/tim/learning-paths/bonus-pfad/nodes/unlock",
                Map.of("nodeId", "bonusA")).andExpect(status().isNoContent());
        timView = body(getAs(as(tim), "/api/learning-paths/bonus-pfad"));
        assertEquals("DONE", state(timView, "bonusA"));
        assertTrue(find(timView, "bonusA").get("reason").asString().contains("kein Bonus"));
        assertEquals(pointsBeforeUnlock, points(tim), "UNLOCK_NODE vergibt keine Bonuspunkte");
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

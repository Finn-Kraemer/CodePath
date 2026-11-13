package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.CourseService;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Klassenraum mit Sitzplan und Beobachtungsbogen: Raum, Plätze, Kriterien, Bewertungen, Notizen, Exporte, Datenschutz. */
class ObservationTest extends IntegrationTestBase {

    static final String BASE = "/api/admin/observations";

    @BeforeEach
    void clearTemplates() {
        jdbc.update("delete from observation_boards");
    }

    static Map<String, Object> table(String id, double x, double y, String... seats) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String seat : seats) list.add(Map.of("id", seat, "side", "BOTTOM"));
        return Map.of("id", id, "kind", "TABLE", "x", x, "y", y, "width", 160, "height", 60, "rotation", 0, "seats", list);
    }

    static Map<String, Object> room() {
        return Map.of("items", List.of(
                Map.of("id", "board", "kind", "BOARD", "x", 400, "y", 10, "width", 400, "height", 20, "rotation", 0, "seats", List.of()),
                table("t2", 600, 200, "s3", "s4"),
                table("t1", 100, 200, "s1", "s2")));
    }

    JsonNode board(Course course) throws Exception {
        return body(postAs(teacher(), BASE + "/" + course.getId() + "/open", null).andExpect(status().isOk()));
    }

    static JsonNode person(JsonNode board, String name) {
        for (JsonNode p : board.get("people")) if (p.get("name").asString().equals(name)) return p;
        throw new AssertionError("Person fehlt: " + name);
    }

    static long criterion(JsonNode board, String name) {
        for (JsonNode c : board.get("criteria")) if (c.get("name").asString().equals(name)) return c.get("id").asLong();
        throw new AssertionError("Kriterium fehlt: " + name);
    }

    static Map<String, Object> interest() {
        return Map.of("name", "Interesse", "kind", "CHOICE", "options", List.of(
                Map.of("value", "1", "description", "Neugier durch Fragen zum Beruf"),
                Map.of("value", "2", "description", "Wirkt motiviert und aufgeweckt")));
    }

    /** Lehrkraft richtet einen Bogen ein: Raum, zwei Kriterien, eine Person ohne Konto, Plätze, Werte und Notiz. */
    JsonNode setUp(Course course, User mia) throws Exception {
        String url = BASE + "/" + course.getId();
        putAs(teacher(), url + "/layout", room()).andExpect(status().isOk());
        postAs(teacher(), url + "/criteria", interest()).andExpect(status().isOk());
        postAs(teacher(), url + "/criteria", Map.of("name", "Motivation", "kind", "NUMBER", "min", 1, "max", 5)).andExpect(status().isOk());
        JsonNode b = body(postAs(teacher(), url + "/people", Map.of("name", "Gast Jonas")).andExpect(status().isOk()));
        long miaId = person(b, "mia").get("id").asLong();
        putAs(teacher(), url + "/people/" + miaId + "/seat", Map.of("seatId", "s1")).andExpect(status().isOk());
        putAs(teacher(), url + "/people/" + miaId + "/ratings/" + criterion(b, "Interesse"), Map.of("value", "1")).andExpect(status().isOk());
        putAs(teacher(), url + "/people/" + miaId + "/ratings/" + criterion(b, "Motivation"), Map.of("value", "4,5")).andExpect(status().isOk());
        putAs(teacher(), url + "/people/" + miaId + "/note", Map.of("note", "Fragt viel zu Ausbildungswegen")).andExpect(status().isOk());
        return board(course);
    }

    @Test
    void teacherBuildsRoomAssignsSeatsAndRates() throws Exception {
        Course course = course("8a");
        User mia = student("mia", course);
        student("tom", course);
        student("ida", course("8b"));
        String url = BASE + "/" + course.getId();

        JsonNode b = board(course);
        assertEquals(2, b.get("people").size(), "Jedes Kursmitglied hat eine Zeile, Fremde nicht");
        assertTrue(b.get("editable").asBoolean());

        b = setUp(course, mia);
        assertEquals(3, b.get("layout").get("items").size());
        assertEquals(List.of("Interesse", "Motivation"), b.get("criteria").valueStream().map(c -> c.get("name").asString()).toList());
        assertEquals("Neugier durch Fragen zum Beruf", b.get("criteria").get(0).get("options").get(0).get("description").asString());
        JsonNode miaRow = person(b, "mia");
        assertEquals("s1", miaRow.get("seatId").asString());
        assertEquals("1", miaRow.get("ratings").get(String.valueOf(criterion(b, "Interesse"))).asString());
        assertEquals("4.5", miaRow.get("ratings").get(String.valueOf(criterion(b, "Motivation"))).asString(), "Komma wird zum Punkt");
        assertEquals("Fragt viel zu Ausbildungswegen", miaRow.get("note").asString());
        assertTrue(person(b, "Gast Jonas").get("userId").isNull());

        // Ungültige Werte
        long miaId = miaRow.get("id").asLong();
        putAs(teacher(), url + "/people/" + miaId + "/ratings/" + criterion(b, "Motivation"), Map.of("value", "7")).andExpect(status().isBadRequest());
        putAs(teacher(), url + "/people/" + miaId + "/ratings/" + criterion(b, "Motivation"), Map.of("value", "viel")).andExpect(status().isBadRequest());
        putAs(teacher(), url + "/people/" + miaId + "/ratings/" + criterion(b, "Interesse"), Map.of("value", "3")).andExpect(status().isBadRequest());
        putAs(teacher(), url + "/people/" + miaId + "/seat", Map.of("seatId", "gibts-nicht")).andExpect(status().isNotFound());
        postAs(teacher(), url + "/criteria", Map.of("name", "Leer", "kind", "CHOICE", "options", List.of())).andExpect(status().isBadRequest());
        postAs(teacher(), url + "/criteria", Map.of("name", "Falsch", "kind", "NUMBER", "min", 5, "max", 1)).andExpect(status().isBadRequest());
        putAs(teacher(), url + "/layout", Map.of("items", List.of(table("x", 0, 0, "a"), table("y", 0, 0, "a")))).andExpect(status().isBadRequest());

        // Platz tauschen: Jonas auf Mias Platz, Mia bekommt Jonas' (keinen) Platz – dann Mia auf s2
        long jonasId = person(b, "Gast Jonas").get("id").asLong();
        putAs(teacher(), url + "/people/" + jonasId + "/seat", Map.of("seatId", "s3")).andExpect(status().isOk());
        b = body(putAs(teacher(), url + "/people/" + jonasId + "/seat", Map.of("seatId", "s1")).andExpect(status().isOk()));
        assertEquals("s1", person(b, "Gast Jonas").get("seatId").asString());
        assertEquals("s3", person(b, "mia").get("seatId").asString(), "Belegter Platz: beide tauschen");

        // Leerer Wert löscht, Notiz leeren
        JsonNode cleared = body(putAs(teacher(), url + "/people/" + miaId + "/ratings/" + criterion(b, "Interesse"), Map.of("value", "")).andExpect(status().isOk()));
        assertFalse(cleared.get("ratings").has(String.valueOf(criterion(b, "Interesse"))));

        // Kriterium ändern: Bereich enger – nicht mehr passende Werte fallen weg
        b = body(putAs(teacher(), url + "/criteria/" + criterion(b, "Motivation"),
                Map.of("name", "Motivation", "kind", "NUMBER", "min", 1, "max", 3)).andExpect(status().isOk()));
        assertFalse(person(b, "mia").get("ratings").has(String.valueOf(criterion(b, "Motivation"))));

        // Reihenfolge, Umbenennen, Löschen
        long interesse = criterion(b, "Interesse"), motivation = criterion(b, "Motivation");
        b = body(putAs(teacher(), url + "/criteria-order", Map.of("ids", List.of(motivation, interesse))).andExpect(status().isOk()));
        assertEquals("Motivation", b.get("criteria").get(0).get("name").asString());
        b = body(putAs(teacher(), url + "/people/" + jonasId, Map.of("name", "Jonas (Gast)")).andExpect(status().isOk()));
        assertEquals("s1", person(b, "Jonas (Gast)").get("seatId").asString());
        putAs(teacher(), url + "/people/" + miaId, Map.of("name", "Umbenannt")).andExpect(status().isBadRequest());
        mvc.perform(delete(url + "/people/" + miaId).with(teacher()).with(csrf())).andExpect(status().isBadRequest());
        b = body(mvc.perform(delete(url + "/criteria/" + motivation).with(teacher()).with(csrf())).andExpect(status().isOk()));
        assertEquals(1, b.get("criteria").size());

        // Raum ändern: Platz verschwindet → Person wieder ohne Platz
        b = body(putAs(teacher(), url + "/layout", Map.of("items", List.of(table("t1", 100, 200, "s1")))).andExpect(status().isOk()));
        assertTrue(person(b, "mia").get("seatId").isNull());
        assertEquals("s1", person(b, "Jonas (Gast)").get("seatId").asString());

        // Nach dem Neuladen ist alles da
        JsonNode reloaded = board(course);
        assertEquals("Fragt viel zu Ausbildungswegen", person(reloaded, "mia").get("note").asString());
    }

    @Test
    void readingAndExportingWriteNothing() throws Exception {
        Course course = course("8a");
        student("mia", course);
        String url = BASE + "/" + course.getId();
        JsonNode snapshot = body(getAs(teacher(), BASE + "?courseId=" + course.getId()).andExpect(status().isOk()));
        assertEquals(1, snapshot.get("people").size(), "Mitglieder erscheinen auch ohne Zeile");
        assertTrue(snapshot.get("people").get(0).get("id").isNull());
        getAs(teacher(), url + "/export.json").andExpect(status().isOk());
        getAs(teacher(), url + "/export.csv").andExpect(status().isOk());
        getAs(teacher(), url + "/pdf").andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("select count(*) from observation_boards", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from observation_people", Integer.class));
        // Erst das Öffnen (POST) legt Bogen und Zeilen an
        assertNotNull(board(course).get("people").get(0).get("id").asLong());
        assertEquals(1, jdbc.queryForObject("select count(*) from observation_people", Integer.class));
    }

    @Test
    void studentsSeeNothing() throws Exception {
        Course course = course("8a");
        User mia = student("mia", course);
        setUp(course, mia);
        String url = BASE + "/" + course.getId();
        getAs(as(mia), BASE + "?courseId=" + course.getId()).andExpect(status().isForbidden());
        postAs(as(mia), url + "/open", null).andExpect(status().isForbidden());
        getAs(as(mia), url + "/export.json").andExpect(status().isForbidden());
        getAs(as(mia), url + "/pdf").andExpect(status().isForbidden());
        putAs(as(mia), url + "/people/1/note", Map.of("note", "x")).andExpect(status().isForbidden());
        // Kein Notizinhalt im Audit-Log
        Integer leaks = jdbc.queryForObject("select count(*) from audit_events where details::text like '%Ausbildungswegen%'", Integer.class);
        assertEquals(0, leaks);
    }

    @Test
    void exportImportCsvAndPdf() throws Exception {
        Course course = course("8a");
        User mia = student("mia", course);
        setUp(course, mia);
        String url = BASE + "/" + course.getId();

        JsonNode exported = body(getAs(teacher(), url + "/export.json").andExpect(status().isOk()));
        assertEquals("codepath-observation", exported.get("format").asString());
        assertEquals(2, exported.get("criteria").size());
        JsonNode miaExport = exported.get("people").valueStream().filter(p -> "mia".equals(p.path("username").asString(null))).findFirst().orElseThrow();
        assertEquals("1", miaExport.get("ratings").get("Interesse").asString());

        String csv = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url + "/export.csv").with(teacher()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("Name;Benutzername;Platz;Interesse;Motivation;Notizen"), csv);
        assertTrue(csv.contains("mia;mia;Tisch 1 · 1;1;4.5;Fragt viel zu Ausbildungswegen"), csv);

        byte[] pdf = getAs(teacher(), url + "/pdf?perPerson=true").andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String text = TakeHomeTest.text(pdf);
        assertTrue(text.contains("BEOBACHTUNGSBOGEN"), text);
        assertTrue(text.contains("Neugier durch Fragen zum Beruf"), text);
        assertTrue(text.contains("Fragt viel zu Ausbildungswegen"), text);

        // Import in einen anderen Kurs: mia ist dort kein Mitglied → Name ohne Konto, mit Hinweis
        Course other = course("9b");
        student("tom", other);
        JsonNode result = body(mvc.perform(post(BASE + "/" + other.getId() + "/import").with(teacher()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(exported))).andExpect(status().isOk()));
        assertEquals(2, result.get("criteria").asInt());
        assertTrue(result.get("warnings").toString().contains("@mia gehört nicht zum Kurs"), result.toString());
        JsonNode imported = result.get("board");
        assertEquals(3, imported.get("layout").get("items").size());
        JsonNode miaFree = person(imported, "mia");
        assertTrue(miaFree.get("userId").isNull());
        assertEquals("s1", miaFree.get("seatId").asString());
        assertEquals("4.5", miaFree.get("ratings").get(String.valueOf(criterion(imported, "Motivation"))).asString());

        // Zweiter Import ändert nichts doppelt
        JsonNode again = body(mvc.perform(post(BASE + "/" + other.getId() + "/import").with(teacher()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(exported))).andExpect(status().isOk()));
        assertEquals(imported.get("people").size(), again.get("board").get("people").size());
        assertEquals(2, again.get("board").get("criteria").size());

        postAs(teacher(), BASE + "/" + other.getId() + "/import", Map.of("format", "etwas-anderes")).andExpect(status().isBadRequest());
    }

    @Test
    void templatesCarryRoomAndCriteriaButNoPeople() throws Exception {
        Course course = course("8a");
        User mia = student("mia", course);
        setUp(course, mia);

        JsonNode template = body(postAs(teacher(), BASE + "/" + course.getId() + "/templates", Map.of("name", "Computerraum 1")).andExpect(status().isOk()));
        assertEquals(4, template.get("seats").asInt());
        assertEquals(2, template.get("criteria").asInt());
        long templateId = template.get("id").asLong();

        Course other = course("9b");
        student("tom", other);
        JsonNode sources = body(getAs(teacher(), BASE + "/templates"));
        assertEquals("Computerraum 1", sources.get(0).get("name").asString());
        assertTrue(sources.get(0).get("courseId").isNull());

        JsonNode b = body(postAs(teacher(), BASE + "/" + other.getId() + "/apply",
                Map.of("sourceId", templateId, "room", true, "criteria", true)).andExpect(status().isOk()));
        assertEquals(3, b.get("layout").get("items").size());
        assertEquals(2, b.get("criteria").size());
        assertEquals(1, b.get("people").size(), "Nur die eigenen Mitglieder");
        // erneut übernehmen legt keine doppelten Kriterien an
        b = body(postAs(teacher(), BASE + "/" + other.getId() + "/apply",
                Map.of("sourceId", templateId, "room", false, "criteria", true)).andExpect(status().isOk()));
        assertEquals(2, b.get("criteria").size());

        // Zentraler Export: nur Vorlagen, keine Personen; Import erkennt „unverändert“
        byte[] zip = getAs(teacher(), "/api/admin/transfer/export?sections=observation-templates").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode manifest = json.readTree(TransferTest.unzip(zip).get("codepath-export.json"));
        JsonNode section = manifest.at("/sections/observation-templates");
        assertEquals(1, section.size());
        assertEquals("Computerraum 1", section.get(0).get("name").asString());
        assertFalse(manifest.toString().contains("Ausbildungswegen"));
        assertFalse(manifest.toString().contains("mia"));
        JsonNode report = body(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/admin/transfer/import")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "export.zip", "application/zip", zip))
                .with(teacher()).with(csrf())).andExpect(status().isOk()));
        assertTrue(report.toString().contains("\"action\":\"UNCHANGED\""), report.toString());

        mvc.perform(delete(BASE + "/templates/" + templateId).with(teacher()).with(csrf())).andExpect(status().isOk());
        assertEquals(2, body(getAs(teacher(), BASE + "/templates")).size(), "Nur noch die Bögen der beiden Kurse");
    }

    @Test
    void endingTheCourseDeletesEverything() throws Exception {
        Course course = course("8a");
        User mia = student("mia", course);
        setUp(course, mia);
        courseService.endCourse(course.getId());
        for (String table : List.of("observation_boards", "observation_criteria", "observation_people", "observation_ratings")) {
            assertEquals(0, jdbc.queryForObject("select count(*) from " + table, Integer.class), table);
        }
    }

    @Test
    void promotionMovesRoomButDropsRatingsWithoutProgress() throws Exception {
        Course course = course("8a");
        User mia = student("mia", course);
        setUp(course, mia);
        CourseService.CourseSummary next = courseService.promote(course.getId(),
                new CourseService.PromoteOptions("9a", null, false, false, false, false));
        Course target = courseService.get(next.id());
        JsonNode b = board(target);
        assertEquals(3, b.get("layout").get("items").size());
        assertEquals(2, b.get("criteria").size());
        JsonNode miaRow = person(b, "mia");
        assertEquals("s1", miaRow.get("seatId").asString());
        assertTrue(miaRow.get("note").isNull());
        assertEquals(0, miaRow.get("ratings").size());
        assertEquals(0, jdbc.queryForObject("select count(*) from observation_boards where course_id = ?", Integer.class, course.getId()));
    }

    @Test
    void leavingTheCourseRemovesTheRow() throws Exception {
        Course course = course("8a");
        User mia = student("mia", course);
        setUp(course, mia);
        jdbc.update("delete from memberships where user_id = ?", mia.getId());
        JsonNode b = board(course);
        assertTrue(b.get("people").valueStream().noneMatch(p -> "mia".equals(p.path("username").asString(null))));
        assertEquals(0, jdbc.queryForObject("select count(*) from observation_ratings", Integer.class));
    }
}

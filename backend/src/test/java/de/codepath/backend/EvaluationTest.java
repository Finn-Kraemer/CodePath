package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bewertungsbögen: Selbsteinschätzung, Unterrichtsbewertung, Lehrerbewertung und deren Sichtbarkeit. */
class EvaluationTest extends IntegrationTestBase {

    static final String ADMIN = "/api/admin/evaluations";

    static Map<String, Object> scale(String id, String label) {
        return Map.of("id", id, "type", "SCALE", "label", label, "required", true, "min", 1, "max", 5);
    }

    static Map<String, Object> text(String id, String label) {
        return Map.of("id", id, "type", "TEXT", "label", label);
    }

    Map<String, Object> roundRequest(Course course, String visibility, boolean anonymous, boolean allowEdit) {
        Map<String, Object> request = new HashMap<>();
        request.put("courseId", course.getId());
        request.put("title", "Halbjahr 1");
        request.put("intro", "Bitte ehrlich ausfüllen.");
        request.put("selfCriteria", List.of(
                Map.of("id", "h", "type", "HEADING", "label", "Arbeit"),
                scale("fleiss", "Ich habe fleißig gearbeitet."),
                text("notiz", "Notiz")));
        request.put("lessonCriteria", List.of(
                scale("tempo", "Tempo"),
                Map.of("id", "hilfe", "type", "CHOICE", "label", "Was half?", "multiple", true, "options", List.of("Erklärungen", "Aufgaben", "Quiz")),
                Map.of("id", "gern", "type", "YES_NO", "label", "Gern dabei?"),
                text("wunsch", "Wunsch")));
        request.put("teacherUsesSelfCriteria", true);
        request.put("teacherVisibility", visibility);
        request.put("lessonAnonymous", anonymous);
        request.put("allowEdit", allowEdit);
        return request;
    }

    long createRound(Map<String, Object> request) throws Exception {
        JsonNode round = body(postAs(teacher(), ADMIN + "/rounds", request).andExpect(status().isOk()));
        return round.get("id").asLong();
    }

    void open(long id) throws Exception {
        putAs(teacher(), ADMIN + "/rounds/" + id + "/status?status=OPEN", null).andExpect(status().isOk());
    }

    @Test
    void studentsRateThemselvesAndTheLessonTeacherRatesStudents() throws Exception {
        Course course = course("9a");
        User lena = student("lena", course);
        User tom = student("tom", course);
        long id = createRound(roundRequest(course, "AFTER_SELF", true, true));

        // Entwurf: für Schüler unsichtbar
        getAs(as(lena), "/api/evaluations").andExpect(jsonPath("$.length()").value(0));
        getAs(as(lena), "/api/evaluations/" + id).andExpect(status().isNotFound());
        open(id);

        JsonNode mine = body(getAs(as(lena), "/api/evaluations").andExpect(status().isOk()));
        assertEquals(1, mine.size());
        assertTrue(mine.get(0).get("hasSelf").asBoolean());
        assertFalse(mine.get(0).get("selfDone").asBoolean());

        // Pflichtfelder und gültige Werte
        putAs(as(lena), "/api/evaluations/" + id + "/self", Map.of("answers", Map.of())).andExpect(status().isBadRequest());
        putAs(as(lena), "/api/evaluations/" + id + "/self", Map.of("answers", Map.of("fleiss", 9))).andExpect(status().isBadRequest());
        putAs(as(lena), "/api/evaluations/" + id + "/self", Map.of("answers", Map.of("fleiss", 4, "notiz", "  gut  ", "fremd", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.self.answers.fleiss").value(4))
                .andExpect(jsonPath("$.self.answers.notiz").value("gut"))
                .andExpect(jsonPath("$.self.answers.fremd").doesNotExist());
        putAs(as(lena), "/api/evaluations/" + id + "/lesson",
                Map.of("answers", Map.of("tempo", 3, "hilfe", List.of("Quiz", "Erklärungen"), "gern", true, "wunsch", "Mehr Quiz")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lesson.answers.hilfe[0]").value("Erklärungen"));
        putAs(as(tom), "/api/evaluations/" + id + "/lesson",
                Map.of("answers", Map.of("tempo", 5, "hilfe", List.of("Unbekannt"), "gern", false))).andExpect(status().isBadRequest());
        putAs(as(tom), "/api/evaluations/" + id + "/lesson",
                Map.of("answers", Map.of("tempo", 5, "gern", false, "wunsch", "Langsamer bitte"))).andExpect(status().isOk());

        // Schüler dürfen keine Lehrerbewertung abgeben und nichts aus der Administration lesen
        getAs(as(lena), ADMIN + "/rounds/" + id + "/results").andExpect(status().isForbidden());

        // Lehrkraft bewertet nach den Kriterien der Selbsteinschätzung
        putAs(teacher(), ADMIN + "/rounds/" + id + "/students/" + lena.getId(), Map.of("answers", Map.of("fleiss", 5, "notiz", "Sehr gut")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfAnswers.fleiss").value(4))
                .andExpect(jsonPath("$.teacherAnswers.fleiss").value(5))
                .andExpect(jsonPath("$.lessonSubmitted").value(true))
                .andExpect(jsonPath("$.lessonAnswers").doesNotExist());
        putAs(teacher(), ADMIN + "/rounds/" + id + "/students/" + tom.getId(), Map.of("answers", Map.of("fleiss", 2)))
                .andExpect(status().isOk());

        // AFTER_SELF: Lena sieht die Lehrerbewertung, Tom erst nach seiner Selbsteinschätzung
        getAs(as(lena), "/api/evaluations/" + id).andExpect(jsonPath("$.teacherState").value("VISIBLE"))
                .andExpect(jsonPath("$.teacher.answers.fleiss").value(5));
        getAs(as(tom), "/api/evaluations/" + id).andExpect(jsonPath("$.teacherState").value("AFTER_SELF"))
                .andExpect(jsonPath("$.teacher").doesNotExist());

        // Auswertung: anonyme Unterrichtsbewertung ohne Namen
        JsonNode results = body(getAs(teacher(), ADMIN + "/rounds/" + id + "/results").andExpect(status().isOk()));
        assertEquals(2, results.get("students").size());
        assertEquals(2, results.get("lesson").get("responses").asInt());
        JsonNode tempo = results.get("lesson").get("criteria").get(0);
        assertEquals(4.0, tempo.get("average").asDouble(), 0.001);
        assertEquals(5, tempo.get("distribution").size());
        JsonNode wunsch = results.get("lesson").get("criteria").get(3);
        assertEquals(2, wunsch.get("texts").size());
        wunsch.get("texts").forEach(t -> assertTrue(t.get("author") == null || t.get("author").isNull()));
        JsonNode lenaRow = results.get("students").get(0);
        assertEquals("lena", lenaRow.get("username").asString());
        assertEquals(75, lenaRow.get("selfScore").asInt());
        assertEquals(100, lenaRow.get("teacherScore").asInt());
        assertEquals(1, results.get("teacher").get("criteria").get(1).get("distribution").get(4).get("count").asInt());
        assertEquals(4, lenaRow.get("selfAnswers").get("fleiss").asInt());
        assertTrue(lenaRow.get("lessonAnswers") == null || lenaRow.get("lessonAnswers").isNull(), "anonym");
        assertEquals(2, results.get("lessonAnswers").size());

        // Lehrkraft blendet ihre Bewertung aus
        Map<String, Object> hidden = roundRequest(course, "HIDDEN", true, true);
        putAs(teacher(), ADMIN + "/rounds/" + id, hidden).andExpect(status().isOk());
        getAs(as(lena), "/api/evaluations/" + id).andExpect(jsonPath("$.teacherState").value("HIDDEN"))
                .andExpect(jsonPath("$.teacher").doesNotExist());

        // Geschlossen: nur noch lesen
        putAs(teacher(), ADMIN + "/rounds/" + id + "/status?status=CLOSED", null).andExpect(status().isOk());
        putAs(as(lena), "/api/evaluations/" + id + "/self", Map.of("answers", Map.of("fleiss", 1))).andExpect(status().isConflict());
        getAs(as(lena), "/api/evaluations/" + id).andExpect(jsonPath("$.self.answers.fleiss").value(4))
                .andExpect(jsonPath("$.self.canEdit").value(false));
    }

    @Test
    void namedLessonFeedbackSingleSubmissionAndCourseIsolation() throws Exception {
        Course course = course("9a");
        Course other = course("9b");
        User lena = student("lena", course);
        User fremd = student("fremd", other);
        long id = createRound(roundRequest(course, "VISIBLE", false, false));
        open(id);

        getAs(as(fremd), "/api/evaluations/" + id).andExpect(status().isNotFound());
        putAs(as(fremd), "/api/evaluations/" + id + "/self", Map.of("answers", Map.of("fleiss", 3))).andExpect(status().isNotFound());
        putAs(teacher(), ADMIN + "/rounds/" + id + "/students/" + fremd.getId(), Map.of("answers", Map.of("fleiss", 3)))
                .andExpect(status().isNotFound());

        putAs(as(lena), "/api/evaluations/" + id + "/lesson", Map.of("answers", Map.of("tempo", 2, "gern", true, "wunsch", "Mehr Pausen")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lesson.canEdit").value(false));
        // allowEdit = false: nur einmal
        putAs(as(lena), "/api/evaluations/" + id + "/lesson", Map.of("answers", Map.of("tempo", 4, "gern", true)))
                .andExpect(status().isConflict());

        // Nicht anonym: Lehrkraft sieht Namen und die Antworten der Person
        JsonNode results = body(getAs(teacher(), ADMIN + "/rounds/" + id + "/results"));
        assertEquals("lena", results.get("lesson").get("criteria").get(3).get("texts").get(0).get("author").asString());
        getAs(teacher(), ADMIN + "/rounds/" + id + "/students/" + lena.getId())
                .andExpect(jsonPath("$.lessonAnswers.wunsch").value("Mehr Pausen"));

        // VISIBLE: sofort sichtbar, sobald bewertet
        getAs(as(lena), "/api/evaluations/" + id).andExpect(jsonPath("$.teacherState").value("PENDING"));
        putAs(teacher(), ADMIN + "/rounds/" + id + "/students/" + lena.getId(), Map.of("answers", Map.of("fleiss", 3)))
                .andExpect(status().isOk());
        getAs(as(lena), "/api/evaluations/" + id).andExpect(jsonPath("$.teacherState").value("VISIBLE"));
    }

    @Test
    void deadlineClosesTheRound() throws Exception {
        Course course = course("9a");
        User lena = student("lena", course);
        Map<String, Object> request = roundRequest(course, "HIDDEN", true, true);
        request.put("closesAt", LocalDateTime.now().plusHours(1).withNano(0).toString());
        long id = createRound(request);
        open(id);
        getAs(as(lena), "/api/evaluations/" + id).andExpect(jsonPath("$.acceptingAnswers").value(true));
        jdbc.update("update evaluation_rounds set closes_at = ? where id = ?", LocalDateTime.now().minusMinutes(1), id);
        getAs(as(lena), "/api/evaluations/" + id).andExpect(jsonPath("$.acceptingAnswers").value(false));
        putAs(as(lena), "/api/evaluations/" + id + "/self", Map.of("answers", Map.of("fleiss", 3))).andExpect(status().isConflict());
    }

    @Test
    void templatesAreCustomizableAndValidated() throws Exception {
        JsonNode list = body(getAs(teacher(), ADMIN + "/templates").andExpect(status().isOk()));
        Set<String> slugs = new HashSet<>();
        list.forEach(t -> slugs.add(t.get("slug").asString()));
        assertTrue(slugs.containsAll(Set.of("selbsteinschaetzung", "unterrichtsbewertung", "lehrerbewertung")));

        Map<String, Object> template = Map.of("name", "Projekt-Feedback", "kind", "LESSON", "criteria", List.of(
                Map.of("type", "SCALE", "label", "Projekt", "min", 0, "max", 10, "style", "stars"),
                Map.of("type", "TEXT", "label", "Warum?", "maxLength", 300)));
        JsonNode created = body(postAs(teacher(), ADMIN + "/templates", template).andExpect(status().isOk()));
        assertEquals("projekt-feedback", created.get("slug").asString());
        assertEquals("k1", created.get("criteria").get(0).get("id").asString());
        assertEquals("STARS", created.get("criteria").get(0).get("style").asString());
        assertEquals("k2", created.get("criteria").get(1).get("id").asString());

        // Ungültiges wird abgelehnt
        postAs(teacher(), ADMIN + "/templates", Map.of("name", "X", "kind", "SELF", "criteria",
                List.of(Map.of("type", "CHOICE", "label", "Nur eine", "options", List.of("a"))))).andExpect(status().isBadRequest());
        postAs(teacher(), ADMIN + "/templates", Map.of("name", "X", "kind", "SELF", "criteria",
                List.of(Map.of("type", "SCALE", "label", "Zu groß", "min", 1, "max", 20)))).andExpect(status().isBadRequest());
        postAs(teacher(), ADMIN + "/templates", Map.of("name", "X", "kind", "SELF", "criteria",
                List.of(Map.of("type", "HEADING", "label", "Nur Überschrift")))).andExpect(status().isBadRequest());
        postAs(teacher(), ADMIN + "/templates", Map.of("name", "X", "kind", "SELF", "criteria",
                List.of(Map.of("id", "a", "type", "YES_NO", "label", "1"), Map.of("id", "a", "type", "YES_NO", "label", "2"))))
                .andExpect(status().isBadRequest());

        // Bearbeiten und Löschen
        long id = created.get("id").asLong();
        putAs(teacher(), ADMIN + "/templates/" + id, Map.of("name", "Projekt", "kind", "LESSON", "criteria",
                List.of(Map.of("id", "k1", "type", "YES_NO", "label", "Gut?")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("projekt-feedback"))
                .andExpect(jsonPath("$.criteria.length()").value(1));
        mvc.perform(delete(ADMIN + "/templates/" + id).with(teacher()).with(csrf())).andExpect(status().isOk());

        // Schüler kommen nicht an die Vorlagen
        User lena = student("lena", course("9a"));
        getAs(as(lena), ADMIN + "/templates").andExpect(status().isForbidden());
    }

    byte[] bytes(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    @Test
    void sheetsAsPdfAndWholeClassAsZip() throws Exception {
        Course course = course("9a");
        User lena = student("lena", course);
        User tom = student("tom", course);
        long id = createRound(roundRequest(course, "AFTER_SELF", true, true));
        open(id);
        putAs(as(lena), "/api/evaluations/" + id + "/self", Map.of("answers", Map.of("fleiss", 4, "notiz", "Viel gelernt"))).andExpect(status().isOk());
        putAs(as(lena), "/api/evaluations/" + id + "/lesson", Map.of("answers", Map.of("tempo", 2, "gern", true, "wunsch", "Geheimer Wunsch")))
                .andExpect(status().isOk());
        putAs(teacher(), ADMIN + "/rounds/" + id + "/students/" + lena.getId(), Map.of("answers", Map.of("fleiss", 5, "notiz", "Toll gemacht")))
                .andExpect(status().isOk());
        putAs(teacher(), ADMIN + "/rounds/" + id + "/students/" + tom.getId(), Map.of("answers", Map.of("fleiss", 2, "notiz", "Nur für Tom")))
                .andExpect(status().isOk());

        // Bogen für die Lehrkraft: Selbst- und Lehrerbewertung, anonyme Unterrichtsbewertung nicht
        String sheet = TakeHomeTest.text(bytes(getAs(teacher(), ADMIN + "/rounds/" + id + "/students/" + lena.getId() + "/pdf")));
        assertTrue(sheet.contains("Viel gelernt"));
        assertTrue(sheet.contains("Toll gemacht"));
        assertTrue(sheet.contains("5 von 5"));
        assertFalse(sheet.contains("Geheimer Wunsch"), "anonym");

        // Eigener Bogen: Tom hat sich nicht selbst eingeschätzt → Lehrerbewertung (AFTER_SELF) fehlt
        String toms = TakeHomeTest.text(bytes(getAs(as(tom), "/api/evaluations/" + id + "/pdf")));
        assertFalse(toms.contains("Nur für Tom"));
        assertTrue(toms.contains("Selbsteinschätzung abgeschickt"));
        String lenas = TakeHomeTest.text(bytes(getAs(as(lena), "/api/evaluations/" + id + "/pdf")));
        assertTrue(lenas.contains("Toll gemacht"));
        assertTrue(lenas.contains("Geheimer Wunsch"), "die eigene Unterrichtsbewertung");
        getAs(as(lena), ADMIN + "/rounds/" + id + "/export.zip").andExpect(status().isForbidden());

        // Ganze Klasse als ZIP: Klassenauswertung + ein Bogen je Person
        Map<String, byte[]> files = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes(getAs(teacher(), ADMIN + "/rounds/" + id + "/export.zip"))))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) files.put(e.getName(), zip.readAllBytes());
        }
        assertEquals(Set.of("00_Klassenauswertung.pdf", "Bewertung_lena.pdf", "Bewertung_tom.pdf"), files.keySet());
        String summary = TakeHomeTest.text(files.get("00_Klassenauswertung.pdf"));
        assertTrue(summary.contains("Geheimer Wunsch"), "Freitexte der Unterrichtsbewertung in der Auswertung");
        int wish = summary.indexOf("Geheimer Wunsch");
        assertFalse(summary.substring(wish, Math.min(summary.length(), wish + 40)).contains("lena"), "ohne Namen");
        assertTrue(summary.contains("Viel gelernt"), "Selbsteinschätzung mit Namen");
        assertTrue(TakeHomeTest.text(files.get("Bewertung_tom.pdf")).contains("Nur für Tom"));
        String single = TakeHomeTest.text(bytes(getAs(teacher(), ADMIN + "/rounds/" + id + "/summary.pdf")));
        assertTrue(single.contains("KLASSE"));

        // Eine Sammel-PDF zum Drucken: Auswertung + alle Bögen, direkt im Browser geöffnet
        var print = getAs(teacher(), ADMIN + "/rounds/" + id + "/export?format=pdf&duplex=true&inline=true")
                .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals("application/pdf", print.getContentType());
        assertTrue(print.getHeader("Content-Disposition").startsWith("inline"));
        String all = TakeHomeTest.text(print.getContentAsByteArray());
        assertTrue(all.contains("Toll gemacht") && all.contains("Nur für Tom") && all.contains("KLASSE"));
        getAs(teacher(), ADMIN + "/rounds/" + id + "/export?format=docx").andExpect(status().isBadRequest());
    }
}

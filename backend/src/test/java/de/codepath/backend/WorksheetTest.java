package de.codepath.backend;

import de.codepath.backend.identity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Aufgabenblätter: anlegen, bearbeiten, PDF als Schülerfassung und Lösung, Bilder, Export/Import. */
class WorksheetTest extends IntegrationTestBase {

    @BeforeEach
    void clearWorksheets() {
        jdbc.update("delete from worksheets");
    }

    long create(String title) throws Exception {
        return body(postAs(teacher(), "/api/admin/worksheets", Map.of("title", title)).andExpect(status().isOk())).get("id").asLong();
    }

    static Map<String, Object> content(Map<String, Object> settings, Object... blocks) {
        return Map.of("settings", settings, "blocks", List.of(blocks));
    }

    static final Map<String, Object> FULL = content(
            Map.of("kind", "Hausaufgabe", "subject", "Informatik", "school", "Gesamtschule Musterstadt",
                    "className", "8b", "instructions", "Bearbeite **alle** Aufgaben.", "footer", "Viel Erfolg!",
                    "accent", "#1d4ed8", "gradeField", true),
            Map.of("id", "h", "type", "heading", "text", "Teil A: Grundlagen"),
            Map.of("id", "t", "type", "task", "prompt", "Was ist eine Variable?", "points", 2, "answer", "lines", "size", 3,
                    "solution", "Ein benannter Speicherplatz"),
            Map.of("id", "p", "type", "task", "prompt", "Rechne um:", "points", 2, "answer", "grid", "size", 2,
                    "parts", List.of(Map.of("text", "5 in Binär", "solution", "101"), Map.of("text", "12 in Binär", "solution", "1100"))),
            Map.of("id", "c", "type", "choice", "prompt", "Welche Sprache ist eine Programmiersprache?", "points", 1, "columns", 2,
                    "options", List.of(Map.of("text", "Python", "correct", true), Map.of("text", "HTML", "correct", false))),
            Map.of("id", "g", "type", "gap", "prompt", "Fülle die Lücken.", "points", 2, "wordBank", true,
                    "text", "Eine [[Schleife]] wiederholt Befehle, eine [[Bedingung|Verzweigung]] entscheidet."),
            Map.of("id", "tf", "type", "truefalse", "prompt", "Richtig oder falsch?", "points", 1.5,
                    "statements", List.of(Map.of("text", "1 Byte hat 8 Bit", "correct", true))),
            Map.of("id", "m", "type", "matching", "prompt", "Ordne zu.", "points", 2,
                    "pairs", List.of(Map.of("left", "CPU", "right", "Rechenwerk"), Map.of("left", "RAM", "right", "Arbeitsspeicher"))),
            Map.of("id", "o", "type", "order", "prompt", "Bringe in die richtige Reihenfolge.", "points", 1,
                    "items", List.of("Eingabe", "Verarbeitung", "Ausgabe")),
            Map.of("id", "tb", "type", "table", "prompt", "Ergänze die Tabelle.", "points", 1,
                    "rows", List.of(List.of("Dezimal", "Binär"), List.of("3", "[[11]]"))),
            Map.of("id", "k", "type", "code", "code", "for i in range(3):\n    print([[i]])", "gaps", true, "lineNumbers", true),
            Map.of("id", "b", "type", "box", "title", "Merke", "text", "- Variablen speichern Werte", "style", "tip"),
            Map.of("id", "s", "type", "text", "text", "Nur in der Lösung sichtbar", "show", "solution"),
            Map.of("id", "x", "type", "pagebreak"),
            Map.of("id", "d", "type", "divider"),
            Map.of("id", "sp", "type", "space", "size", 2));

    @Test
    void createEditAndRenderStudentAndSolutionVersions() throws Exception {
        long id = create("Binärzahlen üben");
        JsonNode starter = body(getAs(teacher(), "/api/admin/worksheets/" + id));
        assertEquals("task", starter.at("/content/blocks/1/type").asString(), "Neues Blatt startet mit einer Beispielaufgabe");

        JsonNode saved = body(putAs(teacher(), "/api/admin/worksheets/" + id, Map.of("title", "Binärzahlen", "content", FULL))
                .andExpect(status().isOk()));
        assertEquals("Binärzahlen", saved.get("title").asString());

        JsonNode summary = body(getAs(teacher(), "/api/admin/worksheets")).get(0);
        assertEquals("Hausaufgabe", summary.get("kind").asString());
        assertEquals(8, summary.get("tasks").asInt(), "Tabelle mit Arbeitsauftrag zählt als Aufgabe");
        assertEquals(12.5, summary.get("points").asDouble());

        MockHttpServletResponse response = getAs(teacher(), "/api/admin/worksheets/" + id + "/pdf?download=true")
                .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals("application/pdf", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").startsWith("attachment"));
        assertTrue(response.getHeader("Content-Disposition").contains("Binaerzahlen.pdf"));
        String student = TakeHomeTest.text(response.getContentAsByteArray());
        assertTrue(student.contains("HAUSAUFGABE"));
        assertTrue(student.contains("Gesamtschule Musterstadt"));
        assertTrue(student.contains("Aufgabe 1"));
        assertTrue(student.contains("Aufgabe 8"));
        assertTrue(student.contains("/ 12,5"), "Gesamtpunkte im Kopf");
        assertTrue(student.contains("Seite 1 von"));
        assertEquals(2, de.codepath.backend.worksheet.WorksheetRendererTest.pages(response.getContentAsByteArray()),
                "Kein leeres Blatt durch Umbruch, Trennlinie und Freiraum am Ende");
        assertTrue(student.contains("Viel Erfolg!"));
        assertTrue(student.contains("Schleife"), "Wortspeicher zeigt die Lückenwörter");
        assertFalse(student.contains("Ein benannter Speicherplatz"), "Keine Lösung in der Schülerfassung");
        assertFalse(student.contains("1100"));
        assertFalse(student.contains("Nur in der Lösung sichtbar"));
        assertFalse(student.contains("LÖSUNG"));

        response = getAs(teacher(), "/api/admin/worksheets/" + id + "/pdf?variant=solution").andExpect(status().isOk())
                .andReturn().getResponse();
        assertTrue(response.getHeader("Content-Disposition").startsWith("inline"));
        assertTrue(response.getHeader("Content-Disposition").contains("Binaerzahlen_Loesung.pdf"));
        String solution = TakeHomeTest.text(response.getContentAsByteArray());
        assertTrue(solution.contains("LÖSUNG"));
        assertTrue(solution.contains("Ein benannter Speicherplatz"));
        assertTrue(solution.contains("1100"));
        assertTrue(solution.contains("Nur in der Lösung sichtbar"));
    }

    @Test
    void previewRendersUnsavedContentAndInvalidContentIsRejected() throws Exception {
        byte[] pdf = mvc.perform(post("/api/admin/worksheets/preview?variant=solution").with(teacher()).with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("title", "Entwurf", "content", FULL))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(TakeHomeTest.text(pdf).contains("Entwurf"));

        long id = create("Leer");
        putAs(teacher(), "/api/admin/worksheets/" + id, Map.of("content", content(Map.of(), Map.of("type", "unbekannt"))))
                .andExpect(status().isBadRequest());
        putAs(teacher(), "/api/admin/worksheets/" + id, Map.of("content", content(Map.of("accent", "red"))))
                .andExpect(status().isBadRequest());
        putAs(teacher(), "/api/admin/worksheets/" + id, Map.of("content", content(Map.of(), Map.of("type", "image", "key", "../etc/passwd"))))
                .andExpect(status().isBadRequest());
        putAs(teacher(), "/api/admin/worksheets/" + id, Map.of("title", " ")).andExpect(status().isBadRequest());

        // Ein leeres Blatt ergibt trotzdem ein gültiges PDF
        putAs(teacher(), "/api/admin/worksheets/" + id, Map.of("content", content(Map.of()))).andExpect(status().isOk());
        getAs(teacher(), "/api/admin/worksheets/" + id + "/pdf").andExpect(status().isOk());
    }

    @Test
    void onlyTheTeacherManagesWorksheets() throws Exception {
        long id = create("Geheim");
        User student = student("tom", course("8b"));
        getAs(as(student), "/api/admin/worksheets").andExpect(status().isForbidden());
        getAs(as(student), "/api/admin/worksheets/" + id + "/pdf?variant=solution").andExpect(status().isForbidden());
        postAs(as(student), "/api/admin/worksheets", Map.of("title", "X")).andExpect(status().isForbidden());
    }

    @Test
    void copyDeleteImagesAndTransfer() throws Exception {
        byte[] png = MediaTest.png(30, 20, 0x336699);
        String key = body(mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "logo.png", "image/png", png))
                .with(teacher()).with(csrf()))).get("key").asString();
        long id = create("Mit Bild");
        putAs(teacher(), "/api/admin/worksheets/" + id, Map.of("content", content(Map.of("logo", key),
                Map.of("type", "image", "key", key, "width", 40, "caption", "Ein Bild")))).andExpect(status().isOk());
        assertTrue(TakeHomeTest.text(getAs(teacher(), "/api/admin/worksheets/" + id + "/pdf").andReturn().getResponse()
                .getContentAsByteArray()).contains("Ein Bild"));

        // Bild ist als verwendet bekannt und lässt sich nicht löschen
        JsonNode library = body(getAs(teacher(), "/api/admin/media/library"));
        boolean referenced = false;
        for (JsonNode item : library) {
            if (item.get("key").asString().equals(key)) {
                for (JsonNode usage : item.get("usages")) referenced |= usage.get("kind").asString().equals("WORKSHEET");
            }
        }
        assertTrue(referenced);
        mvc.perform(delete("/api/admin/media/" + key).with(teacher()).with(csrf())).andExpect(status().isConflict());

        long copy = body(postAs(teacher(), "/api/admin/worksheets/" + id + "/copy", Map.of()).andExpect(status().isOk())).get("id").asLong();
        assertEquals("Mit Bild (Kopie)", body(getAs(teacher(), "/api/admin/worksheets/" + copy)).get("title").asString());

        // Export enthält die Blätter und das Bild; Import legt gelöschte Blätter wieder an
        byte[] zip = getAs(teacher(), "/api/admin/transfer/export?sections=worksheets").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        boolean hasImage = false;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) hasImage |= entry.getName().equals("media/" + key);
        }
        assertTrue(hasImage);

        mvc.perform(delete("/api/admin/worksheets/" + copy).with(teacher()).with(csrf())).andExpect(status().isNoContent());
        getAs(teacher(), "/api/admin/worksheets/" + copy).andExpect(status().isNotFound());
        JsonNode report = body(mvc.perform(multipart("/api/admin/transfer/import")
                .file(new MockMultipartFile("file", "export.zip", "application/zip", zip)).with(teacher()).with(csrf())));
        assertTrue(report.toString().contains("CREATED"), report.toString());
        assertTrue(report.toString().contains("UNCHANGED"), report.toString());
        assertEquals(2, body(getAs(teacher(), "/api/admin/worksheets")).size());
    }
}

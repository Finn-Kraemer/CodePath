package de.codepath.backend;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mitnehm-Paket und Berufe-Kompass. */
class TakeHomeTest extends IntegrationTestBase {

    @Test
    void compassPointsTowardsTheStrongestCareerField() throws Exception {
        Course course = course("9a");
        unlock(course, "git");
        unlock(course, "datenbanken");
        User lena = student("lena", course);

        var empty = body(getAs(as(lena), "/api/compass").andExpect(status().isOk()));
        assertEquals(2, empty.get("entries").size(), "Beide freigegebenen Module haben ein Berufsfeld");
        assertEquals(0, empty.get("top").size(), "Ohne Punkte zeigt der Kompass noch keine Richtung");

        postAs(as(lena), "/api/modules/git/g1/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        var compass = body(getAs(as(lena), "/api/compass"));
        assertEquals("git", compass.at("/top/0/moduleSlug").asString());
        assertTrue(compass.at("/top/0/career").asString().contains("DevOps"));
        assertTrue(compass.at("/top/0/percent").asInt() > 0);
        assertEquals(1, compass.get("top").size());
    }

    @Test
    void takeHomePdfContainsResultsCodeCompassAndLinksAndStoresNothing() throws Exception {
        Course course = course("9b");
        unlock(course, "git");
        unlock(course, "datenbanken");
        User tom = student("tom", course);
        postAs(as(tom), "/api/modules/git/g1/submit", Map.of("selected", List.of("b"))).andExpect(status().isOk());
        postAs(as(tom), "/api/modules/datenbanken/sql-query-pro/submit", Map.of("query", "SELECT name FROM kunden_mitnehmtest"))
                .andExpect(status().isOk());

        Integer auditBefore = jdbc.queryForObject("select count(*) from audit_events", Integer.class);
        MockHttpServletResponse response = getAs(as(tom), "/api/reports/take-home").andExpect(status().isOk()).andReturn().getResponse();
        assertEquals("application/pdf", response.getContentType());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrue(response.getHeader("Content-Disposition").contains("Mitnehm-Paket_tom.pdf"));
        assertEquals(auditBefore, jdbc.queryForObject("select count(*) from audit_events", Integer.class),
                "Das Mitnehm-Paket hinterlässt keine Spuren auf dem Server");

        String text = text(response.getContentAsByteArray());
        assertTrue(text.contains("MITNEHM-PAKET"));
        assertTrue(text.contains("BERUFE-KOMPASS"));
        assertTrue(text.contains("DevOps-Engineer"), "Kompass-Ergebnis");
        assertTrue(text.contains("Gelöst"), "Ergebnisse je Aufgabe");
        assertTrue(text.contains("kunden_mitnehmtest"), "Eigener Code aus der letzten Abgabe");
        assertTrue(text.contains("Learn Git Branching"), "Links zum Weiterlernen aus dem Modul");
        assertTrue(text.contains("planet-beruf.de"), "Allgemeine Links");
        assertFalse(text.contains("Programmierung"), "Nicht freigegebene Module erscheinen nicht");
    }

    @Test
    void takeHomeWorksWithoutAnyProgress() throws Exception {
        User mia = student("mia", course("9c"));
        String text = text(getAs(as(mia), "/api/reports/take-home").andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertTrue(text.contains("Noch keine Ergebnisse"));
    }

    static String text(byte[] pdf) throws Exception {
        try (PdfReader reader = new PdfReader(pdf)) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) sb.append(extractor.getTextFromPage(page)).append('\n');
            return sb.toString();
        }
    }
}

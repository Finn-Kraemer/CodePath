package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spielt jedes Szenario des Browser-Labors so durch, wie es Schüler mit den Entwicklerwerkzeugen tun –
 * nur über die Labor-Adressen – und reicht dann den gefundenen Code ein.
 */
class BrowserLabTest extends IntegrationTestBase {

    static final Pattern PART = Pattern.compile("Teil \\d: ([A-Z2-9]{4})");

    long taskId(String slug) {
        return jdbc.queryForObject("select id from tasks where slug = ?", Long.class, slug);
    }

    MockHttpServletResponse call(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(as(user))).andReturn().getResponse();
    }

    static String part(String text) {
        Matcher m = PART.matcher(text);
        assertTrue(m.find(), "Kein Teil gefunden in: " + text);
        return m.group(1);
    }

    String lab(String slug) {
        return "/api/lab/" + taskId(slug);
    }

    @Test
    void weatherStation() throws Exception {
        Course course = course("8a");
        User lena = student("lena", course);
        unlock(course, "web-entwicklung");
        String base = lab("lab-wetter");

        MockHttpServletResponse page = call(lena, get(base + "/"));
        assertEquals(200, page.getStatus());
        assertTrue(page.getContentType().startsWith("text/html"));
        assertTrue(page.getHeader("Content-Security-Policy").contains("frame-ancestors 'none'"));
        assertFalse(PART.matcher(page.getContentAsString()).find(), "Die Startseite verrät nichts");

        // 1. Netzwerk: v1 ist abgeschaltet, v2 liefert Teil 1 und im Header den Schlüssel
        assertTrue(call(lena, get(base + "/daten").param("v", "1")).getContentAsString().contains("v=2"));
        MockHttpServletResponse v2 = call(lena, get(base + "/daten").param("v", "2"));
        String teil1 = part(v2.getContentAsString(StandardCharsets.UTF_8));
        String key = v2.getHeader("X-Api-Schluessel");
        assertNotNull(key);

        // 2. Quelltext + Web-Speicher: Diagnosemodus schickt den Header X-Diagnose
        assertTrue(call(lena, get(base + "/app.js")).getContentAsString().contains("'wetter', 'diagnose'"));
        assertEquals(403, call(lena, get(base + "/diagnose")).getStatus());
        String teil2 = part(call(lena, get(base + "/diagnose").header("X-Diagnose", "an")).getContentAsString(StandardCharsets.UTF_8));

        // 3. Konsole: Verlauf nur mit dem Schlüssel
        assertEquals(401, call(lena, get(base + "/verlauf").header("X-Api-Schluessel", "falsch")).getStatus());
        String teil3 = part(call(lena, get(base + "/verlauf").header("X-Api-Schluessel", key)).getContentAsString(StandardCharsets.UTF_8));

        postAs(as(lena), "/api/modules/web-entwicklung/lab-wetter/submit", Map.of("code", teil1 + "-" + teil2 + "-" + teil3))
                .andExpect(jsonPath("$.isCorrect").value(true));
        // Kleinschreibung und ohne Bindestriche geht auch – aber Lenas Code hilft Tom nicht
        User tom = student("tom", course);
        postAs(as(tom), "/api/modules/web-entwicklung/lab-wetter/submit", Map.of("code", (teil1 + teil2 + teil3).toLowerCase()))
                .andExpect(jsonPath("$.isCorrect").value(false));
    }

    @Test
    void schoolShop() throws Exception {
        Course course = course("8a");
        User lena = student("lena", course);
        unlock(course, "web-entwicklung");
        String base = lab("lab-shop");

        // 1. Inspektor: ausgeblendeter Artikel mit data-lagercode
        String html = call(lena, get(base + "/")).getContentAsString(StandardCharsets.UTF_8);
        Matcher hidden = Pattern.compile("data-lagercode=\"(Teil 1: [A-Z2-9]{4})\" hidden").matcher(html);
        assertTrue(hidden.find());
        String teil1 = part(hidden.group(1));
        assertTrue(html.contains("id=\"bestellen\" disabled"));

        // 2. Netzwerk zeigt den Fehler, Konsole sendet korrigiert (ohne CSRF-Token – Labor ist ausgenommen)
        MockHttpServletResponse broken = call(lena, post(base + "/bestellung").contentType("application/json")
                .content("{\"artikel\":\"hoodie\",\"menge\":\"2\"}"));
        assertEquals(400, broken.getStatus());
        assertTrue(broken.getContentAsString(StandardCharsets.UTF_8).contains("ganze Zahl"));
        MockHttpServletResponse fixed = call(lena, post(base + "/bestellung").contentType("application/json")
                .content("{\"artikel\":\"hoodie\",\"menge\":2}"));
        assertEquals(201, fixed.getStatus());
        String teil2 = part(fixed.getContentAsString(StandardCharsets.UTF_8));

        // 3. Web-Speicher + atob
        Matcher stored = Pattern.compile("setItem\\('warenkorb', '([A-Za-z0-9+/=]+)'\\)")
                .matcher(call(lena, get(base + "/shop.js")).getContentAsString());
        assertTrue(stored.find());
        String teil3 = part(new String(Base64.getDecoder().decode(stored.group(1)), StandardCharsets.UTF_8));

        postAs(as(lena), "/api/modules/web-entwicklung/lab-shop/submit", Map.of("code", teil1 + "-" + teil2 + "-" + teil3))
                .andExpect(jsonPath("$.isCorrect").value(true));
    }

    @Test
    void escapeRoom() throws Exception {
        Course course = course("8a");
        User lena = student("lena", course);
        unlock(course, "web-entwicklung");
        String base = lab("lab-escape");

        // 1. CSS-Regel
        Matcher css = Pattern.compile("content: \"Teil 1: ([A-Z2-9]{2})\" \"([A-Z2-9]{2})\"")
                .matcher(call(lena, get(base + "/zimmer.css")).getContentAsString());
        assertTrue(css.find());
        String teil1 = css.group(1) + css.group(2);
        // 2. Inhalt einer Fehlerantwort
        MockHttpServletResponse missing = call(lena, get(base + "/schluessel"));
        assertEquals(404, missing.getStatus());
        String teil2 = part(missing.getContentAsString(StandardCharsets.UTF_8));
        // 3. Rückwärts gespeichert
        JsonNode chest = json.readTree(call(lena, get(base + "/truhe")).getContentAsString(StandardCharsets.UTF_8));
        String teil3 = part(new StringBuilder(chest.get("inhalt").asText()).reverse().toString());
        // 4. Tür nur mit den drei richtigen Teilen
        MockHttpServletResponse wrong = call(lena, post(base + "/tuer").contentType("application/json")
                .content("{\"teile\":[\"" + teil1 + "\",\"" + teil2 + "\",\"AAAA\"]}"));
        assertEquals(403, wrong.getStatus());
        assertEquals(2, json.readTree(wrong.getContentAsString(StandardCharsets.UTF_8)).get("richtig").asInt());
        MockHttpServletResponse open = call(lena, post(base + "/tuer").contentType("application/json")
                .content("{\"teile\":[\"" + teil1 + "\",\"" + teil2 + "\",\"" + teil3.toLowerCase() + "\"]}"));
        assertEquals(200, open.getStatus());
        String teil4 = json.readTree(open.getContentAsString(StandardCharsets.UTF_8)).get("teil4").asText();

        // Drei von vier Teilen: Teilpunkte, nicht gelöst
        postAs(as(lena), "/api/modules/web-entwicklung/lab-escape/submit", Map.of("code", teil1 + "-" + teil2 + "-" + teil3 + "-AAAA"))
                .andExpect(jsonPath("$.outcome").value("PARTIAL"));
        postAs(as(lena), "/api/modules/web-entwicklung/lab-escape/submit", Map.of("code", teil1 + "-" + teil2 + "-" + teil3 + "-" + teil4))
                .andExpect(jsonPath("$.isCorrect").value(true));
    }

    @Test
    void accessRules() throws Exception {
        Course course = course("8a");
        User lena = student("lena", course);
        String base = lab("lab-wetter");

        // Ohne Anmeldung, bei gesperrtem Modul, für andere Aufgabentypen: kein Labor
        assertEquals(401, mvc.perform(get(base + "/")).andReturn().getResponse().getStatus());
        assertEquals(403, call(lena, get(base + "/")).getStatus());
        unlock(course, "web-entwicklung");
        assertEquals(200, call(lena, get(base + "/")).getStatus());
        assertEquals(404, call(lena, get("/api/lab/" + taskId("w12") + "/")).getStatus());
        assertEquals(404, call(lena, get(base + "/gibt-es-nicht")).getStatus());
        int traversal = call(lena, get(base + "/../../admin/media")).getStatus();
        assertTrue(traversal >= 400 && traversal < 500, "Pfad mit .. wird abgelehnt: " + traversal);

        // Die Lehrkraft sieht im Labor die Codes der Vorschau – Probebewertung passt dazu
        String teacherPart = part(mvc.perform(get(base + "/daten").param("v", "2").with(teacher()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String preview = body(getAs(teacher(), "/api/admin/content/tasks/" + taskId("lab-wetter") + "/preview/solution"))
                .get("solution").get("code").asText();
        assertTrue(preview.startsWith(teacherPart + "-"));
        // Der Code steht nie in der Aufgabe selbst
        String detail = getAs(as(lena), "/api/modules/web-entwicklung/lab-wetter").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(detail.contains(preview.substring(0, 4)), detail);
    }
}

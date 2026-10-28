package de.codepath.backend;

import de.codepath.backend.identity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Eigene Seiten: anlegen, veröffentlichen, Sichtbarkeit, Menü, Bilder. */
class PagesTest extends IntegrationTestBase {

    @BeforeEach
    void clearPages() {
        jdbc.update("delete from pages");
    }

    static Map<String, Object> blocks(Object... blocks) {
        return Map.of("blocks", List.of(blocks));
    }

    long create(String title, String slug) throws Exception {
        return body(postAs(teacher(), "/api/admin/pages", Map.of("title", title, "slug", slug)).andExpect(status().isOk()))
                .get("id").asLong();
    }

    @Test
    void pageIsVisibleOnlyAfterPublishingAndRespectsVisibility() throws Exception {
        long id = create("Anfahrt", "anfahrt");
        User student = student("lena", course("8a"));
        assertEquals("Anfahrt", body(getAs(teacher(), "/api/admin/pages/" + id)).at("/draft/blocks/0/title").asString(),
                "Neue Seite startet mit einem Textabschnitt");
        getAs(as(student), "/api/public/pages/anfahrt").andExpect(status().isNotFound());

        putAs(teacher(), "/api/admin/pages/" + id + "/published", blocks(Map.of("id", "a", "type", "text", "title", "Bus 42")))
                .andExpect(status().isNoContent());
        assertEquals("Bus 42", body(getAs(as(student), "/api/public/pages/anfahrt")).at("/content/blocks/0/title").asString());
        mvc.perform(get("/api/public/pages/anfahrt")).andExpect(status().isUnauthorized());

        // Veröffentlichte Seiten erscheinen im Seiten-Menü – nicht angemeldet nur die öffentlichen
        assertEquals("anfahrt", body(getAs(as(student), "/api/public/pages")).at("/0/slug").asString());
        assertEquals(0, body(mvc.perform(get("/api/public/pages"))).size());

        putAs(teacher(), "/api/admin/pages/" + id, Map.of("visibility", "PUBLIC", "showInNav", true, "description", " Mit Bus und Bahn "))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/pages/anfahrt")).andExpect(status().isOk());
        var nav = body(mvc.perform(get("/api/public/pages")));
        assertEquals("anfahrt", nav.at("/0/slug").asString());
        assertEquals("Mit Bus und Bahn", nav.at("/0/description").asString());
        assertTrue(nav.at("/0/showInNav").asBoolean());
        putAs(teacher(), "/api/admin/pages/" + id, Map.of("description", "x".repeat(301))).andExpect(status().isBadRequest());

        // Entwurf ändert die Live-Fassung nicht
        putAs(teacher(), "/api/admin/pages/" + id + "/draft", blocks(Map.of("id", "a", "type", "text", "title", "Neu")))
                .andExpect(status().isNoContent());
        assertEquals("Bus 42", body(mvc.perform(get("/api/public/pages/anfahrt"))).at("/content/blocks/0/title").asString());
        assertTrue(body(getAs(teacher(), "/api/admin/pages")).at("/0/changed").asBoolean());

        mvc.perform(delete("/api/admin/pages/" + id + "/published").with(teacher()).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/public/pages/anfahrt")).andExpect(status().isNotFound());
        assertEquals(0, body(mvc.perform(get("/api/public/pages"))).size());

        mvc.perform(delete("/api/admin/pages/" + id).with(teacher()).with(csrf())).andExpect(status().isNoContent());
        getAs(teacher(), "/api/admin/pages/" + id).andExpect(status().isNotFound());
    }

    @Test
    void slugsAreCheckedAndOnlyTheTeacherManagesPages() throws Exception {
        create("Erste", "erste");
        postAs(teacher(), "/api/admin/pages", Map.of("title", "Doppelt", "slug", "erste")).andExpect(status().isConflict());
        postAs(teacher(), "/api/admin/pages", Map.of("title", "Böse", "slug", "../admin")).andExpect(status().isBadRequest());
        postAs(teacher(), "/api/admin/pages", Map.of("title", " ", "slug", "leer")).andExpect(status().isBadRequest());
        User student = student("tom", course("8b"));
        postAs(as(student), "/api/admin/pages", Map.of("title", "X", "slug", "x")).andExpect(status().isForbidden());
    }

    @Test
    void imagesOfPublicPagesAreVisibleWithoutLoginAndProtectedFromDeletion() throws Exception {
        String key = body(mvc.perform(multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "bild.png", "image/png", MediaTest.png(12, 12, 0x333333)))
                .with(teacher()).with(csrf())).andExpect(status().isOk())).get("key").asString();
        long id = create("Galerie", "galerie");
        putAs(teacher(), "/api/admin/pages/" + id + "/published", blocks(Map.of("id", "i", "type", "image", "media", key)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/media/" + key)).andExpect(status().isUnauthorized());

        putAs(teacher(), "/api/admin/pages/" + id, Map.of("visibility", "PUBLIC")).andExpect(status().isOk());
        mvc.perform(get("/api/media/" + key)).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/media/" + key).with(teacher()).with(csrf())).andExpect(status().isConflict());
    }
}

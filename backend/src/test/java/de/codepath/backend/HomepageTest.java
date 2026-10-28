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

/** Startseite: Entwurf, Veröffentlichen, öffentliche Auslieferung und Bilder für Besucher. */
class HomepageTest extends IntegrationTestBase {

    @BeforeEach
    void clearHomepage() {
        jdbc.update("delete from system_settings where key in ('homepage_draft', 'homepage_published')");
    }

    static Map<String, Object> page(Object... blocks) {
        return Map.of("blocks", List.of(blocks));
    }

    @Test
    void draftStaysPrivateUntilPublished() throws Exception {
        assertTrue(body(mvc.perform(get("/api/public/homepage")).andExpect(status().isOk())).get("content").isNull(),
                "Ohne Veröffentlichung gilt die Standardseite");

        var draft = page(Map.of("id", "a", "type", "hero", "title", "Entwurf"));
        putAs(teacher(), "/api/admin/homepage/draft", draft).andExpect(status().isNoContent());
        assertTrue(body(mvc.perform(get("/api/public/homepage"))).get("content").isNull());
        assertEquals("Entwurf", body(getAs(teacher(), "/api/admin/homepage")).at("/draft/blocks/0/title").asString());

        putAs(teacher(), "/api/admin/homepage/published", page(Map.of("id", "a", "type", "hero", "title", "Live")))
                .andExpect(status().isNoContent());
        assertEquals("Live", body(mvc.perform(get("/api/public/homepage"))).at("/content/blocks/0/title").asString());
        assertEquals("Live", body(getAs(teacher(), "/api/admin/homepage")).at("/draft/blocks/0/title").asString());

        mvc.perform(delete("/api/admin/homepage").with(teacher()).with(csrf())).andExpect(status().isNoContent());
        assertTrue(body(mvc.perform(get("/api/public/homepage"))).get("content").isNull());
    }

    @Test
    void onlyTheTeacherEditsAndInvalidContentIsRejected() throws Exception {
        User student = student("lena", course("8a"));
        var valid = page(Map.of("id", "a", "type", "text", "title", "Hallo"));
        putAs(as(student), "/api/admin/homepage/published", valid).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/homepage/published").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(valid))).andExpect(status().isUnauthorized());

        putAs(teacher(), "/api/admin/homepage/published", Map.of("blocks", "kein Array")).andExpect(status().isBadRequest());
        putAs(teacher(), "/api/admin/homepage/published", page(Map.of("type", "script"))).andExpect(status().isBadRequest());
        putAs(teacher(), "/api/admin/homepage/published", page(Map.of("type", "text", "body", "x".repeat(250_000))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void imagesOfThePublishedHomepageAreVisibleWithoutLogin() throws Exception {
        String shown = upload(MediaTest.png(10, 10, 0x111111));
        String other = upload(MediaTest.png(10, 10, 0x222222));
        putAs(teacher(), "/api/admin/homepage/published", page(Map.of("id", "i", "type", "image", "media", shown)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/media/" + shown)).andExpect(status().isOk());
        mvc.perform(get("/api/media/" + other)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/media/" + shown).with(teacher()).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/media/" + other).with(teacher()).with(csrf())).andExpect(status().isNoContent());
    }

    private String upload(byte[] data) throws Exception {
        var result = mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "bild.png", "image/png", data))
                .with(teacher()).with(csrf())).andExpect(status().isOk());
        return body(result).get("key").asString();
    }
}

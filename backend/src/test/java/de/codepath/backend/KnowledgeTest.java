package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Wissensdatenbank: mitgelieferte Artikel, Sichtbarkeit nach Modul, Bearbeiten durch die Lehrkraft. */
class KnowledgeTest extends IntegrationTestBase {

    List<String> slugs(User user) throws Exception {
        List<String> slugs = new ArrayList<>();
        body(getAs(as(user), "/api/knowledge").andExpect(status().isOk())).forEach(a -> slugs.add(a.get("slug").asString()));
        return slugs;
    }

    @Test
    void bundledArticlesFollowModuleReleases() throws Exception {
        Course course = course("8a");
        User student = student("lena", course);
        List<String> before = slugs(student);
        assertTrue(before.containsAll(List.of("linux", "git", "algorithms")), "Grundlagen sind immer sichtbar: " + before);
        assertFalse(before.contains("datenbanken"));
        getAs(as(student), "/api/knowledge/datenbanken").andExpect(status().isNotFound());

        unlock(course, "datenbanken");
        assertTrue(slugs(student).contains("datenbanken"));
        assertTrue(body(getAs(as(student), "/api/knowledge/datenbanken")).get("body").asString().contains("SELECT"));
    }

    @Test
    void teacherCreatesEditsAndDeletesArticles() throws Exception {
        User student = student("tom", course("8b"));
        long id = body(postAs(teacher(), "/api/admin/knowledge", Map.of("title", "Netiquette", "slug", "netiquette"))
                .andExpect(status().isOk())).get("id").asLong();
        assertFalse(slugs(student).contains("netiquette"), "Neue Artikel sind zuerst Entwurf");

        putAs(teacher(), "/api/admin/knowledge/" + id, Map.of("body", "# Netiquette\n\nFreundlich bleiben.", "published", true, "icon", "💬"))
                .andExpect(status().isOk());
        assertTrue(slugs(student).contains("netiquette"));
        assertEquals("💬", body(getAs(as(student), "/api/knowledge/netiquette")).get("icon").asString());

        putAs(teacher(), "/api/admin/knowledge/" + id, Map.of("moduleSlug", "gibt-es-nicht")).andExpect(status().isNotFound());
        putAs(teacher(), "/api/admin/knowledge/" + id, Map.of("moduleSlug", "datenbanken")).andExpect(status().isOk());
        assertFalse(slugs(student).contains("netiquette"), "An gesperrtes Modul gekoppelt");

        putAs(as(student), "/api/admin/knowledge/" + id, Map.of("published", false)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/knowledge/" + id).with(teacher()).with(csrf())).andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("select count(*) from deleted_knowledge_articles where slug = 'netiquette'", Integer.class));
        jdbc.update("delete from deleted_knowledge_articles");
    }
}

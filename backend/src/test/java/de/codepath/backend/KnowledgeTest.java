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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    List<String> search(User user, String query) throws Exception {
        List<String> slugs = new ArrayList<>();
        body(mvc.perform(get("/api/search/knowledge").param("q", query).with(as(user))).andExpect(status().isOk()))
                .forEach(a -> slugs.add(a.get("slug").asString()));
        return slugs;
    }

    @Test
    void searchFindsOnlyVisibleArticlesWithAllTerms() throws Exception {
        Course course = course("8c");
        User student = student("mia", course);
        assertEquals("git", search(student, "COMMIT").get(0), "Groß-/Kleinschreibung egal");
        assertFalse(search(student, "SELECT").contains("datenbanken"), "Gesperrtes Modul: Artikel nicht durchsuchbar");
        unlock(course, "datenbanken");
        assertTrue(search(student, "select where").contains("datenbanken"));
        assertTrue(search(student, "select gibtesnichtxyz").isEmpty(), "Alle Wörter müssen vorkommen");
        assertTrue(search(student, "a").isEmpty(), "Zu kurze Suchwörter werden ignoriert");

        var hit = body(getAs(as(student), "/api/search/knowledge?q=commit")).get(0);
        String snippet = hit.get("snippet").asString();
        assertTrue(snippet.toLowerCase().contains("commit"), snippet);
        assertFalse(snippet.contains("**"), "Ohne Markdown-Zeichen: " + snippet);
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

package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lerntagebuch: privat, automatisch gespeichert, im eigenen Mitnehm-Paket, mit dem Kurs gelöscht. */
class JournalTest extends IntegrationTestBase {

    static Map<String, Object> note(String task, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("taskSlug", task);
        body.put("text", text);
        return body;
    }

    @Test
    void notesArePrivateAndEndUpInTheOwnTakeHomePackage() throws Exception {
        Course course = course("8a");
        User lena = student("lena", course);
        User tom = student("tom", course);
        unlock(course, "git");

        // Anlegen, bearbeiten (gleiche Notiz), Notiz zu einer Aufgabe
        JsonNode saved = body(putAs(as(lena), "/api/journal/modules/git",
                note(null, "# Merksatz\n- **commit** speichert einen Stand\n- Frage: was macht `rebase`?")).andExpect(status().isOk()));
        long id = saved.get("id").asLong();
        JsonNode edited = body(putAs(as(lena), "/api/journal/modules/git", note(null, "# Merksatz\n- **commit** speichert einen Stand → Łódź 😀"))
                .andExpect(status().isOk()));
        assertEquals(id, edited.get("id").asLong());
        putAs(as(lena), "/api/journal/modules/git", note("g1", "Bei g1 war ich unsicher.")).andExpect(status().isOk());
        JsonNode module = body(getAs(as(lena), "/api/journal/modules/git"));
        assertEquals(2, module.get("entries").size());
        assertTrue(module.get("entries").get(0).get("taskSlug").isNull(), "Modulnotiz zuerst");
        assertEquals(0, body(getAs(as(tom), "/api/journal/modules/git")).get("entries").size(), "Tom sieht Lenas Notizen nicht");
        assertEquals(0, body(getAs(as(tom), "/api/journal")).size());

        // Andere und die Lehrkraft kommen nicht an die Notizen
        mvc.perform(delete("/api/journal/" + id).with(as(tom)).with(csrf())).andExpect(status().isNotFound());
        assertEquals(0, body(getAs(teacher(), "/api/journal")).size());
        assertEquals(0, body(getAs(teacher(), "/api/admin/journal/shared?courseId=" + course.getId())).size());
        getAs(as(tom), "/api/admin/journal/shared").andExpect(status().isForbidden());

        // Gesperrtes Modul: kein Tagebuch
        getAs(as(lena), "/api/journal/modules/programmierung").andExpect(status().isForbidden());

        // Eigenes Mitnehm-Paket enthält die Notizen, Markdown lesbar
        String pdf = TakeHomeTest.text(getAs(as(lena), "/api/reports/take-home").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertTrue(pdf.contains("MEIN LERNTAGEBUCH"));
        assertTrue(pdf.contains("commit speichert einen Stand"), pdf);
        assertFalse(pdf.contains("**commit**"));
        assertTrue(pdf.contains("Łódź"));
        assertTrue(pdf.contains("Bei g1 war ich unsicher."));
        // … das Sammel-Paket der Lehrkraft nicht
        byte[] bundle = getAs(teacher(), "/api/admin/courses/" + course.getId() + "/take-home?format=pdf")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertFalse(TakeHomeTest.text(bundle).contains("LERNTAGEBUCH"));

        // Teilen: dann sieht die Lehrkraft genau diesen Eintrag
        Map<String, Object> share = note("g1", "Bei g1 war ich unsicher.");
        share.put("shared", true);
        putAs(as(lena), "/api/journal/modules/git", share).andExpect(status().isOk());
        JsonNode shared = body(getAs(teacher(), "/api/admin/journal/shared?courseId=" + course.getId()));
        assertEquals(1, shared.size());
        assertEquals("lena", shared.get(0).get("username").asString());
        assertEquals("g1", shared.get(0).get("entry").get("taskSlug").asString());

        // Leerer Text löscht, Löschen per ID
        putAs(as(lena), "/api/journal/modules/git", note("g1", "  ")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/journal/" + id).with(as(lena)).with(csrf())).andExpect(status().isOk());
        assertEquals(0, body(getAs(as(lena), "/api/journal")).size());
    }

    @Test
    void lengthIsLimitedPromptsAndCourseEnd() throws Exception {
        Course course = course("8a");
        User lena = student("lena", course);
        unlock(course, "git");
        long gitId = contentService.module("git").getId();

        putAs(as(lena), "/api/journal/modules/git", note(null, "a".repeat(9_000))).andExpect(status().isOk());
        putAs(as(lena), "/api/journal/modules/git", note("g1", "b".repeat(1_500))).andExpect(status().isBadRequest());
        putAs(as(lena), "/api/journal/modules/git", note("g1", "b".repeat(1_000))).andExpect(status().isOk());

        // Leitfragen der Lehrkraft
        putAs(teacher(), "/api/admin/modules/" + gitId + "/journal-prompts",
                Map.of("prompts", "Was hast du heute gelernt?\n\nWas ist noch unklar?")).andExpect(status().isOk());
        JsonNode module = body(getAs(as(lena), "/api/journal/modules/git"));
        assertEquals(2, module.get("prompts").size());
        putAs(as(lena), "/api/admin/modules/" + gitId + "/journal-prompts", Map.of("prompts", "x")).andExpect(status().isForbidden());

        courseService.endCourse(course.getId());
        assertEquals(0, jdbc.queryForObject("select count(*) from journal_entries", Integer.class));
        putAs(teacher(), "/api/admin/modules/" + gitId + "/journal-prompts", Map.of("prompts", "")).andExpect(status().isOk());
    }
}

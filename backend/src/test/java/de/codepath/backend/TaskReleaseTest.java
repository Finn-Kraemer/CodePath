package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import de.codepath.backend.report.ReportRenderer;
import de.codepath.backend.report.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Einzelne Aufgaben je Kurs freigeben oder sperren – und überall richtig zählen. */
class TaskReleaseTest extends IntegrationTestBase {

    @Autowired
    ReportService reportService;

    long moduleId(String slug) {
        return contentService.module(slug).getId();
    }

    long taskId(String slug) {
        return jdbc.queryForObject("select id from tasks where slug = ?", Long.class, slug);
    }

    void setMode(Course course, String module, String task, String mode) throws Exception {
        putAs(teacher(), "/api/admin/modules/" + moduleId(module) + "/tasks/" + taskId(task) + "/release?courseId="
                + course.getId() + "&mode=" + mode, null).andExpect(status().isOk());
    }

    @Test
    void singleTaskInLockedModule() throws Exception {
        Course course = course("8a");
        Course other = course("8b");
        User lena = student("lena", course);
        User tom = student("tom", other);

        // Modul gesperrt, eine Aufgabe einzeln freigegeben
        getAs(as(lena), "/api/modules/git").andExpect(status().isForbidden());
        setMode(course, "git", "g1", "SHOWN");

        JsonNode modules = body(getAs(as(lena), "/api/modules"));
        assertEquals(1, modules.size());
        assertEquals("git", modules.get(0).get("slug").asText());
        JsonNode git = body(getAs(as(lena), "/api/modules/git").andExpect(status().isOk()));
        assertEquals(1, git.get("tasks").size());
        assertEquals("g1", git.get("tasks").get(0).get("slug").asText());
        getAs(as(lena), "/api/modules/git/g3").andExpect(status().isForbidden());
        postAs(as(lena), "/api/modules/git/g3/submit", Map.of("selected", List.of("a"))).andExpect(status().isForbidden());
        getAs(as(lena), "/api/modules/git/g1").andExpect(status().isOk());

        // Der andere Kurs sieht davon nichts
        getAs(as(tom), "/api/modules/git").andExpect(status().isForbidden());

        // Modul-Steuerung und Bestätigung zählen nur die eine Aufgabe
        int g1Points = jdbc.queryForObject("select v.points from tasks t join task_versions v on v.id = t.current_version_id where t.slug = 'g1'", Integer.class);
        JsonNode view = body(getAs(teacher(), "/api/admin/modules?courseId=" + course.getId()));
        JsonNode gitView = find(view, "git");
        assertEquals(1, gitView.get("taskCount").asInt());
        assertEquals(g1Points, gitView.get("maxPoints").asInt());
        assertEquals(1, gitView.get("adjustedTasks").asInt());
        List<ReportRenderer.ModuleResult> results = reportService.results(lena);
        assertEquals(1, results.size());
        assertEquals(g1Points, results.getFirst().max());
    }

    @Test
    void hideTaskInUnlockedModule() throws Exception {
        Course course = course("8a");
        User lena = student("lena", course);
        unlock(course, "git");
        int all = body(getAs(as(lena), "/api/modules/git")).get("tasks").size();
        int fullMax = find(body(getAs(teacher(), "/api/admin/modules?courseId=" + course.getId())), "git").get("maxPoints").asInt();
        int releasePoints = jdbc.queryForObject("select v.points from tasks t join task_versions v on v.id = t.current_version_id where t.slug = 'git-release'", Integer.class);

        setMode(course, "git", "git-release", "HIDDEN");
        JsonNode git = body(getAs(as(lena), "/api/modules/git"));
        assertEquals(all - 1, git.get("tasks").size());
        assertEquals(all - 1, git.get("module").get("totalTasks").asInt());
        getAs(as(lena), "/api/modules/git/git-release").andExpect(status().isForbidden());

        assertEquals(fullMax - releasePoints, find(body(getAs(teacher(), "/api/admin/modules?courseId=" + course.getId())), "git").get("maxPoints").asInt());
        assertEquals(fullMax - releasePoints, reportService.results(lena).stream()
                .filter(r -> r.title().startsWith("Git")).findFirst().orElseThrow().max());

        JsonNode tasks = body(getAs(teacher(), "/api/admin/modules/" + moduleId("git") + "/tasks?courseId=" + course.getId()));
        JsonNode release = findTask(tasks, "git-release");
        assertEquals("HIDDEN", release.get("mode").asText());
        assertFalse(release.get("visible").asBoolean());

        // Zurück auf „wie das Modul“
        setMode(course, "git", "git-release", "MODULE");
        getAs(as(lena), "/api/modules/git/git-release").andExpect(status().isOk());
        assertTrue(findTask(body(getAs(teacher(), "/api/admin/modules/" + moduleId("git") + "/tasks?courseId=" + course.getId())), "git-release").get("mode").isNull());

        // Nur die Lehrkraft, nur bekannte Modi, nur Aufgaben des Moduls
        putAs(as(lena), "/api/admin/modules/" + moduleId("git") + "/tasks/" + taskId("g1") + "/release?courseId=" + course.getId() + "&mode=SHOWN", null)
                .andExpect(status().isForbidden());
        putAs(teacher(), "/api/admin/modules/" + moduleId("git") + "/tasks/" + taskId("g1") + "/release?courseId=" + course.getId() + "&mode=EGAL", null)
                .andExpect(status().isBadRequest());
        putAs(teacher(), "/api/admin/modules/" + moduleId("git") + "/tasks/" + taskId("d1") + "/release?courseId=" + course.getId() + "&mode=SHOWN", null)
                .andExpect(status().isNotFound());
    }

    static JsonNode find(JsonNode modules, String slug) {
        for (JsonNode m : modules) if (m.get("slug").asText().equals(slug)) return m;
        throw new AssertionError("Modul fehlt: " + slug);
    }

    static JsonNode findTask(JsonNode tasks, String slug) {
        for (JsonNode t : tasks) if (t.get("slug").asText().equals(slug)) return t;
        throw new AssertionError("Aufgabe fehlt: " + slug);
    }
}

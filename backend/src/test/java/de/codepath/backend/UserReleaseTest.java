package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Freigaben für einzelne Schülerinnen und Schüler: Rangfolge Person → Aufgabe im Kurs → Modul im Kurs. */
class UserReleaseTest extends IntegrationTestBase {

    long moduleId(String slug) {
        return contentService.module(slug).getId();
    }

    long taskId(String slug) {
        return jdbc.queryForObject("select id from tasks where slug = ?", Long.class, slug);
    }

    JsonNode release(Map<String, Object> body) throws Exception {
        return body(postAs(teacher(), "/api/admin/user-releases", body).andExpect(status().isOk()));
    }

    static Map<String, Object> req(List<String> users, String kind, long id, String mode) {
        Map<String, Object> body = new HashMap<>();
        body.put("usernames", users);
        body.put(kind, id);
        body.put("mode", mode);
        return body;
    }

    void setCourseTask(Course course, String module, String task, String mode) throws Exception {
        putAs(teacher(), "/api/admin/modules/" + moduleId(module) + "/tasks/" + taskId(task) + "/release?courseId="
                + course.getId() + "&mode=" + mode, null).andExpect(status().isOk());
    }

    @Test
    void personalReleasesTakePrecedenceOverTheCourse() throws Exception {
        Course course = course("8a");
        User lena = student("lena", course);
        User tom = student("tom", course);

        // 1. Aufgabe für eine Person in einem gesperrten Modul: nur sie (und die Lehrkraft) sieht sie
        release(req(List.of("lena"), "taskId", taskId("g1"), "SHOWN"));
        JsonNode git = body(getAs(as(lena), "/api/modules/git").andExpect(status().isOk()));
        assertEquals(1, git.get("tasks").size());
        assertTrue(git.get("tasks").get(0).get("personal").asBoolean());
        getAs(as(lena), "/api/modules/git/g1").andExpect(status().isOk());
        getAs(as(tom), "/api/modules/git").andExpect(status().isForbidden());
        getAs(as(tom), "/api/modules/git/g1").andExpect(status().isForbidden());
        assertEquals(0, body(getAs(as(tom), "/api/modules")).size());

        // 2. Modul im Kurs frei, für Lena gesperrt – auch serverseitig
        unlock(course, "git");
        release(req(List.of("lena"), "moduleId", moduleId("git"), "HIDDEN"));
        getAs(as(tom), "/api/modules/git/g3").andExpect(status().isOk());
        getAs(as(lena), "/api/modules/git/g3").andExpect(status().isForbidden());
        postAs(as(lena), "/api/modules/git/g3/submit", Map.of("selected", List.of("a"))).andExpect(status().isForbidden());
        //    … aber die eigene Aufgabenfreigabe geht dem eigenen Modul vor
        assertEquals(1, body(getAs(as(lena), "/api/modules/git")).get("tasks").size());

        // 3. Person vor Aufgabe im Kurs: im Kurs gesperrte Aufgabe für Lena frei
        setCourseTask(course, "git", "git-release", "HIDDEN");
        release(req(List.of("lena"), "moduleId", moduleId("git"), "SHOWN"));
        getAs(as(lena), "/api/modules/git/git-release").andExpect(status().isOk());
        getAs(as(tom), "/api/modules/git/git-release").andExpect(status().isForbidden());

        // 4. Aufgabe im Kurs vor Modul im Kurs (bestehendes Verhalten), Person sperrt sie trotzdem
        setCourseTask(course, "git", "git-release", "MODULE");
        lockGit(course);
        setCourseTask(course, "git", "g3", "SHOWN");
        getAs(as(tom), "/api/modules/git/g3").andExpect(status().isOk());
        release(req(List.of("tom"), "taskId", taskId("g3"), "HIDDEN"));
        getAs(as(tom), "/api/modules/git/g3").andExpect(status().isForbidden());

        // Aufheben: Tom folgt wieder dem Kurs
        release(req(List.of("tom"), "taskId", taskId("g3"), "NONE"));
        getAs(as(tom), "/api/modules/git/g3").andExpect(status().isOk());
    }

    private void lockGit(Course course) {
        releaseService.toggle(course.getId(), moduleId("git"));
    }

    @Test
    void personalDeadlineAndBonusWithoutLeaderboardEffect() throws Exception {
        Course course = course("8a");
        User lena = student("lena", course);
        User tom = student("tom", course);

        // Abgelaufene eigene Frist: sichtbar, aber keine Abgabe mehr
        Map<String, Object> body = req(List.of("lena", "tom"), "moduleId", moduleId("git"), "SHOWN");
        body.put("availableUntil", LocalDateTime.now().minusHours(1).withNano(0).toString());
        release(body);
        getAs(as(lena), "/api/modules/git/g1").andExpect(status().isOk());
        postAs(as(lena), "/api/modules/git/g1/submit", Map.of("selected", List.of("a"))).andExpect(status().isForbidden());

        // Bonus ohne Ranglistenwirkung: Punkte zählen im Profil, nicht in der Rangliste
        Map<String, Object> bonus = req(List.of("lena"), "taskId", taskId("g1"), "SHOWN");
        bonus.put("bonus", true);
        JsonNode set = release(bonus);
        assertTrue(set.get(0).get("bonus").asBoolean());
        jdbc.update("insert into points_entries (user_id, task_id, amount, reason) values (?, ?, 10, 'SOLVED')", lena.getId(), taskId("g1"));
        jdbc.update("insert into points_entries (user_id, task_id, amount, reason) values (?, ?, 4, 'SOLVED')", lena.getId(), taskId("g3"));
        jdbc.update("insert into points_entries (user_id, task_id, amount, reason) values (?, ?, 6, 'SOLVED')", tom.getId(), taskId("g1"));
        assertEquals(14, points(lena));
        JsonNode board = body(getAs(as(lena), "/api/leaderboard"));
        assertEquals(4, board.get("ownEntry").get("points").asInt());
        assertEquals("tom", board.get("entries").get(0).get("username").asString());
        JsonNode git = body(getAs(as(lena), "/api/modules/git"));
        for (JsonNode t : git.get("tasks")) {
            assertEquals(t.get("slug").asString().equals("g1"), t.get("bonus").asBoolean());
        }
    }

    @Test
    void studentRecordListsAndRemovesPersonalReleases() throws Exception {
        Course course = course("8a");
        User lena = student("lena", course);
        student("tom", course);
        release(req(List.of("lena", "tom"), "taskId", taskId("g1"), "SHOWN"));
        release(req(List.of("lena"), "moduleId", moduleId("git"), "HIDDEN"));

        JsonNode list = body(getAs(teacher(), "/api/admin/students/lena/releases").andExpect(status().isOk()));
        assertEquals(2, list.size());
        JsonNode moduleView = body(getAs(teacher(), "/api/admin/modules/" + moduleId("git") + "/user-releases?courseId=" + course.getId()));
        assertEquals(3, moduleView.size());

        long id = list.get(0).get("id").asLong();
        mvc.perform(delete("/api/admin/students/lena/releases/" + id).with(teacher()).with(csrf())).andExpect(status().isOk());
        assertEquals(1, body(getAs(teacher(), "/api/admin/students/lena/releases")).size());
        mvc.perform(delete("/api/admin/students/tom/releases/" + list.get(1).get("id").asLong()).with(teacher()).with(csrf()))
                .andExpect(status().isNotFound());

        // Nur die Lehrkraft, nur gültige Angaben
        getAs(as(lena), "/api/admin/students/lena/releases").andExpect(status().isForbidden());
        postAs(as(lena), "/api/admin/user-releases", req(List.of("lena"), "taskId", taskId("g1"), "SHOWN")).andExpect(status().isForbidden());
        Map<String, Object> both = req(List.of("lena"), "taskId", taskId("g1"), "SHOWN");
        both.put("moduleId", moduleId("git"));
        postAs(teacher(), "/api/admin/user-releases", both).andExpect(status().isBadRequest());
        postAs(teacher(), "/api/admin/user-releases", req(List.of("lena"), "taskId", taskId("g1"), "EGAL")).andExpect(status().isBadRequest());
        postAs(teacher(), "/api/admin/user-releases", req(List.of("admin"), "taskId", taskId("g1"), "SHOWN")).andExpect(status().isBadRequest());
        assertTrue(jdbc.queryForObject("select count(*) from audit_events where action = 'USER_RELEASE_SET'", Integer.class) >= 3);

        // Kurs beenden löscht die Freigaben mit den Konten
        courseService.endCourse(course.getId());
        assertEquals(0, jdbc.queryForObject("select count(*) from user_releases", Integer.class));
    }
}

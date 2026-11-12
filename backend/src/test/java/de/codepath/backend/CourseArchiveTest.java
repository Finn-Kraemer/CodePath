package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.CourseStatus;
import de.codepath.backend.identity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kurse archivieren, wiederherstellen, nach Ablauf automatisch löschen und ins nächste Schuljahr übernehmen. */
class CourseArchiveTest extends IntegrationTestBase {

    private Cookie login(String username) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("CODEPATH_SESSION");
    }

    private void addPoints(User user, int amount, LocalDateTime at) {
        jdbc.update("insert into points_entries (user_id, amount, reason, created_at) values (?, ?, 'CORRECTION', ?)", user.getId(), amount, at);
    }

    private Course reload(Course course) {
        return courseService.get(course.getId());
    }

    @Test
    void archivedCourseBlocksStudentsButStaysReadableForTheTeacher() throws Exception {
        Course course = course("9a");
        User lena = student("lena", course);
        unlock(course, "git");
        addPoints(lena, 7, LocalDateTime.now());
        Cookie session = login("lena");

        JsonNode archived = body(postAs(teacher(), "/api/admin/courses/" + course.getId() + "/archive", Map.of("months", 3))
                .andExpect(status().isOk()));
        assertEquals("ARCHIVED", archived.get("status").asString());
        LocalDateTime purge = LocalDateTime.parse(archived.get("purgeAfter").asString());
        assertTrue(purge.isAfter(LocalDateTime.now().plusMonths(3).minusDays(1)) && purge.isBefore(LocalDateTime.now().plusMonths(3).plusDays(1)));

        // Laufende Sitzung beendet, neue Anmeldung und Beitritt abgelehnt
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "lena", "password", PASSWORD))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "neu", "password", PASSWORD, "joinCode", course.getJoinCode()))))
                .andExpect(status().isBadRequest());
        postAs(teacher(), "/api/admin/courses/" + course.getId() + "/accounts", Map.of("count", 1)).andExpect(status().isBadRequest());

        // Die Lehrkraft sieht weiter alles und kann exportieren
        JsonNode record = body(getAs(teacher(), "/api/admin/students/lena/tasks").andExpect(status().isOk()));
        assertFalse(record.isEmpty(), "Aufgaben des freigegebenen Moduls bleiben sichtbar");
        assertNotNull(record);
        getAs(teacher(), "/api/admin/courses/" + course.getId() + "/certificates?format=pdf").andExpect(status().isOk());
        getAs(teacher(), "/api/admin/courses/" + course.getId() + "/take-home").andExpect(status().isOk());
        getAs(teacher(), "/api/leaderboard?courseId=" + course.getId()).andExpect(jsonPath("$.entries[0].points").value(7));
        assertEquals(1, userRepository.findByUsername("lena").stream().count());

        // Nur aktive Kurse lassen sich archivieren, die Frist ist begrenzt
        postAs(teacher(), "/api/admin/courses/" + course.getId() + "/archive", null).andExpect(status().isBadRequest());
        Course other = course("9b");
        postAs(teacher(), "/api/admin/courses/" + other.getId() + "/archive", Map.of("months", 99)).andExpect(status().isBadRequest());
        postAs(as(lena), "/api/admin/courses/" + other.getId() + "/archive", null).andExpect(status().isForbidden());

        // Wiederherstellen: Anmeldung geht wieder
        JsonNode restored = body(postAs(teacher(), "/api/admin/courses/" + course.getId() + "/restore", null).andExpect(status().isOk()));
        assertEquals("ACTIVE", restored.get("status").asString());
        assertTrue(restored.get("purgeAfter").isNull());
        login("lena");
        postAs(teacher(), "/api/admin/courses/" + course.getId() + "/restore", null).andExpect(status().isBadRequest());

        List<String> actions = jdbc.queryForList("select action from audit_events where target = ? order by id", String.class, "course:" + course.getId());
        assertTrue(actions.containsAll(List.of("COURSE_ARCHIVED", "COURSE_RESTORED")));
    }

    @Test
    void archivedCourseIsDeletedAfterTheDeadline() throws Exception {
        Course course = course("9a");
        User lena = student("lena", course);
        Course keep = course("9b");
        User tom = student("tom", keep);
        courseService.archive(course.getId(), 6);
        courseService.archive(keep.getId(), 6);
        jdbc.update("update courses set purge_after = ? where id = ?", LocalDateTime.now().minusMinutes(1), course.getId());
        jdbc.update("update courses set purge_after = ? where id = ?", LocalDateTime.now().plusDays(5), keep.getId());

        // Hinweis vorher: beide werden in den nächsten 14 Tagen gelöscht
        assertEquals(2, body(getAs(teacher(), "/api/admin/courses/purging-soon")).size());

        assertEquals(1, courseService.purgeExpired(LocalDateTime.now()));
        assertEquals(CourseStatus.ENDED, reload(course).getStatus());
        assertTrue(userRepository.findById(lena.getId()).isEmpty());
        assertEquals(CourseStatus.ARCHIVED, reload(keep).getStatus());
        assertTrue(userRepository.findById(tom.getId()).isPresent());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_events where action = 'COURSE_ARCHIVE_PURGED'", Integer.class));
        assertEquals(0, courseService.purgeExpired(LocalDateTime.now()), "nichts doppelt");
    }

    @Test
    void promoteKeepsAccountsAndAppliesTheOptions() throws Exception {
        Course course = course("9a");
        User lena = student("lena", course);
        User tom = student("tom", course);
        unlock(course, "git");
        releaseService.setDeadline(course.getId(), contentService.module("git").getId(), LocalDateTime.now().plusDays(1));
        addPoints(lena, 10, LocalDateTime.now().minusDays(30));
        addPoints(tom, 5, LocalDateTime.now().minusDays(30));
        jdbc.update("insert into codepaths (slug, title, description, graph, published, enforce, course_id, lock_others, template_only, hide_overview, position, updated_at) "
                + "values ('pfad-9a', 'Pfad 9a', '', '{\"nodes\":[],\"edges\":[]}', true, false, ?, false, false, false, 0, now())", course.getId());

        // Mit Fortschritt, Freigaben, CodePaths und neuer Rangliste
        JsonNode next = body(postAs(teacher(), "/api/admin/courses/" + course.getId() + "/promote", Map.of(
                "name", "10a", "endsOn", "2027-07-15", "keepProgress", true, "keepReleases", true,
                "copyCodePaths", true, "restartLeaderboard", true)).andExpect(status().isOk()));
        long nextId = next.get("id").asLong();
        assertEquals("10a", next.get("name").asString());
        assertEquals("ACTIVE", next.get("status").asString());
        assertEquals(2, next.get("members").asInt());
        assertNotEquals(course.getJoinCode(), next.get("joinCode").asString());

        Course old = reload(course);
        assertEquals(CourseStatus.ENDED, old.getStatus());
        assertEquals(nextId, old.getSuccessorId());
        JsonNode oldView = body(getAs(teacher(), "/api/admin/courses"));
        for (JsonNode c : oldView) if (c.get("id").asLong() == course.getId()) assertEquals("10a", c.get("successorName").asString());

        // Konten und Punkte bleiben, die Rangliste startet bei null
        assertEquals("10a", body(getAs(as(lena), "/api/auth/me")).get("courseName").asString());
        assertEquals(10, points(lena));
        JsonNode board = body(getAs(as(lena), "/api/leaderboard"));
        assertEquals(0, board.get("ownEntry").get("points").asInt());
        addPoints(tom, 3, LocalDateTime.now());
        board = body(getAs(teacher(), "/api/leaderboard?courseId=" + nextId));
        assertEquals("tom", board.get("entries").get(0).get("username").asString());
        assertEquals(3, board.get("entries").get(0).get("points").asInt());

        // Freigaben ohne Fristen, CodePath kopiert
        getAs(as(lena), "/api/modules/git").andExpect(status().isOk());
        assertNull(jdbc.queryForObject("select available_until from module_releases where course_id = ? and module_id = ?",
                LocalDateTime.class, nextId, contentService.module("git").getId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from codepaths where course_id = ?", Integer.class, nextId));
        assertEquals(1, jdbc.queryForObject("select count(*) from codepaths where course_id = ?", Integer.class, course.getId()));

        // Ein beendeter Kurs lässt sich nicht noch einmal übernehmen
        postAs(teacher(), "/api/admin/courses/" + course.getId() + "/promote", Map.of("name", "11a")).andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_events where action = 'COURSE_PROMOTED'", Integer.class));
    }

    @Test
    void promoteWithoutProgressStartsFresh() throws Exception {
        Course course = course("9a");
        User lena = student("lena", course);
        unlock(course, "git");
        addPoints(lena, 10, LocalDateTime.now());
        courseService.archive(course.getId(), 6);

        // Auch ein archivierter Kurs lässt sich übernehmen
        JsonNode next = body(postAs(teacher(), "/api/admin/courses/" + course.getId() + "/promote",
                Map.of("name", "10a")).andExpect(status().isOk()));
        assertEquals(0, points(lena));
        assertTrue(userRepository.findById(lena.getId()).isPresent());
        getAs(as(lena), "/api/modules/git").andExpect(status().isForbidden());
        assertTrue(next.get("leaderboardSince").isNull());
        assertTrue(reload(course).getPurgeAfter() == null, "keine Löschfrist mehr für den alten Kurs");
        login("lena");
    }
}

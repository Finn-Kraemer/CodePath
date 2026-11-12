package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Anwesenheit: Termine, Abweichungen anklicken, Quote, Teilnahmebestätigung, Exporte, Datenschutz. */
class AttendanceTest extends IntegrationTestBase {

    long createSession(Course course, String date, String title) throws Exception {
        Map<String, Object> request = new java.util.HashMap<>(Map.of("courseId", course.getId(), "date", date));
        if (title != null) request.put("title", title);
        JsonNode detail = body(postAs(teacher(), "/api/admin/attendance/sessions", request).andExpect(status().isOk()));
        return detail.get("session").get("id").asLong();
    }

    void mark(long session, User user, String status) throws Exception {
        putAs(teacher(), "/api/admin/attendance/sessions/" + session + "/entries/" + user.getId(),
                Map.of("status", status, "note", "Arzttermin")).andExpect(status().isOk());
    }

    @Test
    void sessionsStartAllPresentAndCountTheRate() throws Exception {
        Course course = course("8a");
        User lena = student("lena", course);
        User tom = student("tom", course);
        User other = student("ida", course("8b"));

        long first = createSession(course, "2026-11-02", "Projekttag 1");
        JsonNode detail = body(getAs(teacher(), "/api/admin/attendance/sessions/" + first));
        assertEquals(2, detail.get("entries").size());
        for (JsonNode e : detail.get("entries")) assertEquals("PRESENT", e.get("status").asString());
        assertEquals(2, detail.get("session").get("present").asInt());

        long second = createSession(course, "2026-11-09", null);
        long third = createSession(course, "2026-11-10", null);
        mark(second, tom, "ABSENT");
        mark(third, tom, "EXCUSED");
        mark(third, lena, "LATE");
        putAs(teacher(), "/api/admin/attendance/sessions/" + first + "/entries/" + other.getId(), Map.of("status", "ABSENT"))
                .andExpect(status().isNotFound());
        putAs(teacher(), "/api/admin/attendance/sessions/" + first + "/entries/" + tom.getId(), Map.of("status", "WEG"))
                .andExpect(status().isBadRequest());

        JsonNode overview = body(getAs(teacher(), "/api/admin/attendance?courseId=" + course.getId()));
        assertEquals(3, overview.get("sessions").size());
        for (JsonNode row : overview.get("students")) {
            JsonNode rate = row.get("rate");
            if (row.get("username").asString().equals("tom")) {
                assertEquals(1, rate.get("attended").asInt());
                assertEquals(1, rate.get("excused").asInt());
            } else {
                assertEquals(3, rate.get("attended").asInt(), "verspätet zählt als teilgenommen");
            }
            assertEquals(3, rate.get("recorded").asInt());
        }
        assertEquals("ABSENT", overview.get("cells").get(String.valueOf(tom.getId())).get(String.valueOf(second)).asString());

        JsonNode person = body(getAs(teacher(), "/api/admin/students/tom/attendance"));
        assertEquals("an 1 von 3 Terminen teilgenommen, 1 entschuldigt", person.get("rate").get("text").asString());

        // Exporte
        String csv = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/attendance/export.csv?courseId=" + course.getId()).with(teacher()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("02.11.2026 Projekttag 1"));
        assertTrue(csv.contains("tom;tom;anwesend;fehlt;entschuldigt;1;3;1"));
        getAs(teacher(), "/api/admin/attendance/export?courseId=" + course.getId() + "&format=pdf").andExpect(status().isOk());
        byte[] zip = getAs(teacher(), "/api/admin/attendance/export?courseId=" + course.getId())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(zip.length > 0);

        // Termin löschen
        mvc.perform(delete("/api/admin/attendance/sessions/" + third).with(teacher()).with(csrf())).andExpect(status().isOk());
        assertEquals(2, body(getAs(teacher(), "/api/admin/attendance?courseId=" + course.getId())).get("sessions").size());
    }

    @Test
    void onlyTeachersSeeOthersAndCertificateLineIsOptional() throws Exception {
        Course course = course("8a");
        User lena = student("lena", course);
        User tom = student("tom", course);
        long session = createSession(course, "2026-11-02", null);
        mark(session, lena, "ABSENT");

        getAs(as(lena), "/api/admin/attendance?courseId=" + course.getId()).andExpect(status().isForbidden());
        getAs(as(lena), "/api/admin/students/tom/attendance").andExpect(status().isForbidden());
        getAs(as(lena), "/api/attendance/me").andExpect(status().isForbidden());
        postAs(as(lena), "/api/admin/attendance/sessions", Map.of("courseId", course.getId())).andExpect(status().isForbidden());

        // Ohne Schalter: keine Zeile auf der Bestätigung
        jdbc.update("insert into system_settings (key, value) values ('certificates_enabled', 'true') on conflict (key) do update set value = 'true'");
        String without = TakeHomeTest.text(getAs(as(tom), "/api/reports/my-report").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertFalse(without.contains("Termin"));

        JsonNode settings = body(putAs(teacher(), "/api/admin/settings/attendance",
                Map.of("onCertificate", true, "visibleToStudents", true)).andExpect(status().isOk()));
        assertTrue(settings.get("onCertificate").asBoolean());
        String with = TakeHomeTest.text(getAs(as(tom), "/api/reports/my-report").andReturn().getResponse().getContentAsByteArray());
        assertTrue(with.contains("an 1 von 1 Termin teilgenommen"), with);

        // Eigene Anwesenheit, ohne Notizen der Lehrkraft
        JsonNode own = body(getAs(as(lena), "/api/attendance/me").andExpect(status().isOk()));
        assertEquals("lena", own.get("username").asString());
        assertEquals("ABSENT", own.get("entries").get(0).get("status").asString());
        assertTrue(own.get("entries").get(0).get("note").isNull());

        putAs(teacher(), "/api/admin/settings/attendance", Map.of("onCertificate", false, "visibleToStudents", false)).andExpect(status().isOk());
    }

    @Test
    void endingTheCourseDeletesAttendance() throws Exception {
        Course course = course("8a");
        student("lena", course);
        createSession(course, "2026-11-02", "Tag 1");
        courseService.endCourse(course.getId());
        assertEquals(0, jdbc.queryForObject("select count(*) from attendance_sessions", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from attendance_entries", Integer.class));
    }
}

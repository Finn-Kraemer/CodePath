package de.codepath.backend;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    private Cookie register(String username, String joinCode) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD, "joinCode", joinCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseName").value("8a"))
                .andReturn().getResponse().getCookie("CODEPATH_SESSION");
    }

    @Test
    void registrationNeedsAValidJoinCode() throws Exception {
        Course course = course("8a");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "ohnecode", "password", PASSWORD))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "falsch", "password", PASSWORD, "joinCode", "XXXXXX"))))
                .andExpect(status().isBadRequest());

        Cookie session = register("lena", course.getJoinCode().toLowerCase());
        assertNotNull(session);
        assertTrue(session.isHttpOnly());

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("lena"));
        mvc.perform(post("/api/auth/logout").cookie(session).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void endedCourseCannotBeJoined() throws Exception {
        Course course = course("8a");
        courseService.endCourse(course.getId());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "zuspaet", "password", PASSWORD, "joinCode", course.getJoinCode()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginIsRateLimited() throws Exception {
        User user = student("tim", course("8a"));
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("username", user.getUsername(), "password", "falsch-" + i))))
                    .andExpect(status().isUnauthorized());
        }
        // Auch das richtige Passwort wird für den Rest der Minute abgelehnt
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", user.getUsername(), "password", PASSWORD))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void teacherCanEndAllStudentSessions() throws Exception {
        Course course = course("8a");
        Cookie session = register("schueler", course.getJoinCode());
        postAs(teacher(), "/api/admin/sessions/logout-students", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionsEnded").value(1));
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsNeedLoginAndTeacherRole() throws Exception {
        mvc.perform(get("/api/modules")).andExpect(status().isUnauthorized());
        User user = student("schueler", course("8a"));
        getAs(as(user), "/api/admin/students").andExpect(status().isForbidden());
        getAs(as(user), "/api/admin/submissions").andExpect(status().isForbidden());
        postAs(as(user), "/api/admin/courses", Map.of("name", "x")).andExpect(status().isForbidden());
    }

    @Test
    void bulkAccountsAndEndingACourseDeletesStudentData() throws Exception {
        Course course = course("9b");
        var accounts = body(postAs(teacher(), "/api/admin/courses/" + course.getId() + "/accounts", Map.of("prefix", "9b", "count", 3))
                .andExpect(status().isOk()));
        assertEquals(3, accounts.size());
        String username = accounts.get(0).get("username").asString();
        String password = accounts.get(0).get("password").asString();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk());

        unlock(course, "git");
        User member = userRepository.findByUsername(username).orElseThrow();
        postAs(as(member), "/api/modules/git/g1/submit", Map.of("selected", java.util.List.of("a"))).andExpect(status().isOk());

        // Zeugnisse vor dem Beenden exportieren
        mvc.perform(get("/api/admin/courses/" + course.getId() + "/certificates").with(teacher()))
                .andExpect(status().isOk());
        postAs(teacher(), "/api/admin/courses/" + course.getId() + "/end", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.deletedAccounts").value(3));
        assertTrue(userRepository.findByUsername(username).isEmpty());
        assertEquals(0, jdbc.queryForObject("select count(*) from attempts", Integer.class));
    }
}

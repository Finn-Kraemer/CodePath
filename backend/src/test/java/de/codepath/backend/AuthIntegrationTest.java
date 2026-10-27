package de.codepath.backend;

import tools.jackson.databind.ObjectMapper;
import de.codepath.backend.auth.AuthRequest;
import de.codepath.backend.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.context.annotation.Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterAndLogin() throws Exception {
        AuthRequest registerRequest = AuthRequest.builder()
                .username("testuser")
                .password("password123")
                .build();

        // 1. Register
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("STUDENT"));

        // 2. Login legt eine Session (in PostgreSQL) an; der Browser bekommt nur das Cookie
        Cookie sessionCookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("CODEPATH_SESSION");
        assertNotNull(sessionCookie);
        assertTrue(sessionCookie.isHttpOnly());

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));

        // 3. Logout beendet die Session serverseitig
        mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherCanEndAllStudentSessions() throws Exception {
        AuthRequest student = AuthRequest.builder().username("schueler").password("password123").build();
        Cookie sessionCookie = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(student)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("CODEPATH_SESSION");
        assertNotNull(sessionCookie);
        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie)).andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/sessions/logout-students")
                        .with(user("lehrkraft").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionsEnded").value(1));

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectWrongPassword() throws Exception {
        AuthRequest wrong = AuthRequest.builder().username("nobody").password("wrongpass").build();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrong)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotAccessProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/modules"))
                .andExpect(status().isUnauthorized());
    }
}

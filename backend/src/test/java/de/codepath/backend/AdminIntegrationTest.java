package de.codepath.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.modules.ModuleRepository;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRepository;
import de.codepath.backend.users.UserRole;
import de.codepath.backend.auth.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ModuleRepository moduleRepository;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String studentToken;
    private Module testModule;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        moduleRepository.deleteAll();

        // Create Admin
        User admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash("hash");
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
        adminToken = generateToken(admin);

        // Create Student
        User student = new User();
        student.setUsername("student");
        student.setPasswordHash("hash");
        student.setRole(UserRole.STUDENT);
        userRepository.save(student);
        studentToken = generateToken(student);

        // Create Module
        testModule = new Module();
        testModule.setSlug("test-mod");
        testModule.setTitle("Test");
        testModule.setIsUnlocked(false);
        testModule = moduleRepository.save(testModule);
    }

    private String generateToken(User user) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().name())
                .build();
        return jwtService.generateToken(userDetails);
    }

    @Test
    void shouldToggleModuleAsAdmin() throws Exception {
        mockMvc.perform(put("/api/admin/modules/" + testModule.getId() + "/toggle")
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Module updated = moduleRepository.findById(testModule.getId()).orElseThrow();
        assertTrue(updated.getIsUnlocked());
    }

    @Test
    void shouldRejectStudentAccessToAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/modules")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldFetchStudentsForAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/students")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].username").value("student"));
    }
}

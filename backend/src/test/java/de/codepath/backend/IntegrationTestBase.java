package de.codepath.backend;

import de.codepath.backend.classroom.ReleaseService;
import de.codepath.backend.content.ContentService;
import de.codepath.backend.identity.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Integrationstests gegen PostgreSQL (Testcontainers) mit den echten Content-Paketen als Startinhalt.
 * Vor jedem Test werden alle Schüler- und Kursdaten gelöscht.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected CourseService courseService;
    @Autowired
    protected ReleaseService releaseService;
    @Autowired
    protected ContentService contentService;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected static final String PASSWORD = "geheim-123";

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
                delete from practice_reviews; delete from attempts; delete from points_entries; delete from progress;
                delete from learning_path_choices; delete from module_releases; delete from memberships; delete from general_feedback; delete from audit_events;
                delete from users where role = 'STUDENT'; delete from courses; delete from spring_session; delete from learning_paths;""");
    }

    protected Course course(String name) {
        return courseService.get(courseService.create(name, null).id());
    }

    protected User student(String username, Course course) {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.STUDENT);
        userRepository.save(user);
        if (course != null) courseService.join(user, course);
        return user;
    }

    protected void unlock(Course course, String moduleSlug) {
        releaseService.toggle(course.getId(), contentService.module(moduleSlug).getId());
    }

    protected static RequestPostProcessor as(User user) {
        return user(user.getUsername()).roles(user.getRole().name());
    }

    protected static RequestPostProcessor teacher() {
        return user("admin").roles("ADMIN");
    }

    protected ResultActions getAs(RequestPostProcessor who, String url) throws Exception {
        return mvc.perform(get(url).with(who));
    }

    protected ResultActions postAs(RequestPostProcessor who, String url, Object body) throws Exception {
        return mvc.perform(post(url).with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : json.writeValueAsString(body)));
    }

    protected ResultActions putAs(RequestPostProcessor who, String url, Object body) throws Exception {
        return mvc.perform(put(url).with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : json.writeValueAsString(body)));
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected long points(User user) {
        Long total = jdbc.queryForObject("select coalesce(sum(amount), 0) from points_entries where user_id = ?", Long.class, user.getId());
        return total == null ? 0 : total;
    }

    protected static Map<String, Object> answer(String key, Object value) {
        return Map.of(key, value);
    }
}

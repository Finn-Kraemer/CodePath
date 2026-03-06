package de.codepath.backend;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.modules.ModuleRepository;
import de.codepath.backend.features.tasks.*;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRepository;
import de.codepath.backend.users.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TaskSubmissionTest {

    @Autowired
    private TaskSubmissionService submissionService;

    @Autowired
    private ModuleRepository moduleRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Module testModule;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUsername("student");
        testUser.setPasswordHash("hash");
        testUser.setRole(UserRole.STUDENT);
        testUser.setTotalPoints(0);
        testUser = userRepository.save(testUser);

        testModule = new Module();
        testModule.setSlug("test-module");
        testModule.setTitle("Test Module");
        testModule.setIsUnlocked(true);
        moduleRepository.save(testModule);
    }

    @Test
    void shouldValidateMultipleChoiceCorrectly() {
        Task task = new Task();
        task.setModule(testModule);
        task.setSlug("mc-task");
        task.setTitle("MC");
        task.setType(TaskType.MULTIPLE_CHOICE);
        task.setDifficulty(Difficulty.EASY);
        task.setPoints(10);
        task.setConfig(Map.of("correct", List.of(1)));
        taskRepository.save(task);

        SubmitRequest request = SubmitRequest.builder()
                .payload(Map.of("selected", List.of(1)))
                .build();
        SubmitResponse response = submissionService.submitTask("test-module", "mc-task", testUser, request);

        assertTrue(response.isCorrect());
        assertEquals(10, response.getPointsAwarded());
    }

    @Test
    void shouldRejectWrongMultipleChoice() {
        Task task = new Task();
        task.setModule(testModule);
        task.setSlug("mc-task-wrong");
        task.setTitle("MC Wrong");
        task.setType(TaskType.MULTIPLE_CHOICE);
        task.setDifficulty(Difficulty.EASY);
        task.setPoints(10);
        task.setConfig(Map.of("correct", List.of(1)));
        taskRepository.save(task);

        SubmitRequest request = SubmitRequest.builder()
                .payload(Map.of("selected", List.of(0)))
                .build();
        SubmitResponse response = submissionService.submitTask("test-module", "mc-task-wrong", testUser, request);

        assertFalse(response.isCorrect());
        assertEquals(0, response.getPointsAwarded());
    }
}

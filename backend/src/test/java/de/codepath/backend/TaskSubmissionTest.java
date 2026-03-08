package de.codepath.backend;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.modules.ModuleRepository;
import de.codepath.backend.features.tasks.*;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRepository;
import de.codepath.backend.users.UserRole;
import de.codepath.backend.features.messaging.RealtimeUpdateService;
import de.codepath.backend.features.tasks.messaging.SubmissionProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TaskSubmissionTest {

    @MockBean
    private RealtimeUpdateService realtimeUpdateService;

    @MockBean
    private SubmissionProducer submissionProducer;

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
                .timeSpentSeconds(100L) // Avoid speed bonus
                .build();
        SubmitResponse response = submissionService.submitTask("test-module", "mc-task", testUser, request);

        assertTrue(response.isCorrect());
        assertEquals(10, response.getPointsAwarded());
    }

    @Test
    void shouldAwardHalfPointsWhenSupportUsed() {
        Task task = new Task();
        task.setModule(testModule);
        task.setSlug("support-task");
        task.setTitle("Support Task");
        task.setType(TaskType.MULTIPLE_CHOICE);
        task.setDifficulty(Difficulty.EASY);
        task.setPoints(20);
        task.setConfig(Map.of("correct", List.of(1)));
        taskRepository.save(task);

        SubmitRequest request = SubmitRequest.builder()
                .payload(Map.of("selected", List.of(1)))
                .timeSpentSeconds(100L) // Avoid speed bonus
                .supportUsed(true)
                .build();
        SubmitResponse response = submissionService.submitTask("test-module", "support-task", testUser, request);

        assertTrue(response.isCorrect());
        assertEquals(10, response.getPointsAwarded()); // 20 / 2 = 10
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

    @Test
    void shouldLockMCTaskAfterThreeFailures() {
        Task task = new Task();
        task.setModule(testModule);
        task.setSlug("mc-task-lock");
        task.setTitle("MC Lock");
        task.setType(TaskType.MULTIPLE_CHOICE);
        task.setDifficulty(Difficulty.EASY);
        task.setPoints(10);
        task.setConfig(Map.of("correct", List.of(1)));
        taskRepository.save(task);

        SubmitRequest wrongRequest = SubmitRequest.builder()
                .payload(Map.of("selected", List.of(0)))
                .build();

        // 1st failure
        submissionService.submitTask("test-module", "mc-task-lock", testUser, wrongRequest);
        // 2nd failure
        submissionService.submitTask("test-module", "mc-task-lock", testUser, wrongRequest);
        // 3rd failure
        SubmitResponse response = submissionService.submitTask("test-module", "mc-task-lock", testUser, wrongRequest);

        assertTrue(response.isLocked());
        
        // Try to submit correct answer now
        SubmitRequest correctRequest = SubmitRequest.builder()
                .payload(Map.of("selected", List.of(1)))
                .build();
        SubmitResponse finalResponse = submissionService.submitTask("test-module", "mc-task-lock", testUser, correctRequest);
        
        assertTrue(finalResponse.isLocked());
        assertFalse(finalResponse.isCorrect());
    }
}

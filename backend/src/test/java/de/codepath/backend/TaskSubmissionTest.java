package de.codepath.backend;

import de.codepath.backend.common.GlobalExceptionHandler;
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
@org.springframework.context.annotation.Import(TestcontainersConfiguration.class)
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
                .timeSpentSeconds(100L)
                .build();
        SubmitResponse response = submissionService.submitTask("test-module", "mc-task", testUser, request);

        assertTrue(response.isCorrect());
        assertEquals(15, response.getPointsAwarded()); // 10 + Bonus für den ersten Versuch
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

        submissionService.requestSupport("test-module", "support-task", testUser);

        // Der Client behauptet, keine Hilfe genutzt zu haben – zählt nicht, der Server weiß es besser
        SubmitRequest request = SubmitRequest.builder()
                .payload(Map.of("selected", List.of(1)))
                .supportUsed(false)
                .build();
        SubmitResponse response = submissionService.submitTask("test-module", "support-task", testUser, request);

        assertTrue(response.isCorrect());
        assertEquals(10, response.getPointsAwarded()); // 20 / 2 = 10, kein Bonus
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

    @Test
    void shouldIgnoreClientSupportFlagWithoutServerRequest() {
        saveTask("no-support", TaskType.MULTIPLE_CHOICE, 20, Map.of("correct", List.of(1)));

        SubmitRequest request = SubmitRequest.builder()
                .payload(Map.of("selected", List.of(1)))
                .supportUsed(true)
                .build();
        SubmitResponse response = submissionService.submitTask("test-module", "no-support", testUser, request);

        assertEquals(25, response.getPointsAwarded());
    }

    @Test
    void shouldNotTrustClientTimeForPoints() {
        saveTask("time-task", TaskType.MULTIPLE_CHOICE, 10, Map.of("correct", List.of(1)));

        SubmitRequest request = SubmitRequest.builder()
                .payload(Map.of("selected", List.of(1)))
                .timeSpentSeconds(-100000L)
                .build();
        SubmitResponse response = submissionService.submitTask("test-module", "time-task", testUser, request);

        assertEquals(15, response.getPointsAwarded());
    }

    @Test
    void shouldRejectSubmissionForLockedModule() {
        testModule.setIsUnlocked(false);
        moduleRepository.save(testModule);
        saveTask("locked-module-task", TaskType.MULTIPLE_CHOICE, 10, Map.of("correct", List.of(1)));

        SubmitRequest request = SubmitRequest.builder()
                .payload(Map.of("selected", List.of(1)))
                .build();

        assertThrows(GlobalExceptionHandler.AccessDeniedException.class,
                () -> submissionService.submitTask("test-module", "locked-module-task", testUser, request));
        assertThrows(GlobalExceptionHandler.AccessDeniedException.class,
                () -> submissionService.requestSupport("test-module", "locked-module-task", testUser));
    }

    @Test
    void shouldAwardNoPointsAfterSolutionWasRevealed() {
        saveTask("reveal", TaskType.FILL_BLANK, 10, Map.of("template", "___", "blanks", List.of("html")));
        SubmitRequest wrong = SubmitRequest.builder().payload(Map.of("answers", List.of("body"))).build();

        submissionService.submitTask("test-module", "reveal", testUser, wrong);
        submissionService.submitTask("test-module", "reveal", testUser, wrong);
        SubmitResponse third = submissionService.submitTask("test-module", "reveal", testUser, wrong);
        assertNotNull(third.getCorrectSolution());

        SubmitRequest right = SubmitRequest.builder().payload(Map.of("answers", List.of("html"))).build();
        SubmitResponse response = submissionService.submitTask("test-module", "reveal", testUser, right);

        assertTrue(response.isCorrect());
        assertEquals(0, response.getPointsAwarded());
    }

    @Test
    void shouldNotRevealSqlSolutionAndKeepPointsAfterFailedAttempts() {
        saveSqlTask("sql-retry");
        SubmitRequest wrong = SubmitRequest.builder().payload(Map.of("result", "SELECT name FROM mitarbeiter")).build();
        for (int i = 0; i < 3; i++) {
            SubmitResponse response = submissionService.submitTask("test-module", "sql-retry", testUser, wrong);
            assertNull(response.getCorrectSolution());
            assertFalse(response.isLocked());
        }

        SubmitRequest right = SubmitRequest.builder()
                .payload(Map.of("result", "SELECT name FROM mitarbeiter WHERE created_at > 2020")).build();
        assertEquals(10, submissionService.submitTask("test-module", "sql-retry", testUser, right).getPointsAwarded());
    }

    @Test
    void shouldAcceptFillBlankWithStringAndObjectBlanks() {
        saveTask("fill-mixed", TaskType.FILL_BLANK, 10, Map.of(
                "template", "___ ___",
                "blanks", List.of("html", Map.of("answer", "Body", "caseSensitive", true))));

        SubmitRequest wrongCase = SubmitRequest.builder().payload(Map.of("answers", List.of("HTML", "body"))).build();
        assertFalse(submissionService.submitTask("test-module", "fill-mixed", testUser, wrongCase).isCorrect());

        SubmitRequest right = SubmitRequest.builder().payload(Map.of("answers", List.of(" HTML ", "Body"))).build();
        assertTrue(submissionService.submitTask("test-module", "fill-mixed", testUser, right).isCorrect());
    }

    @Test
    void shouldNotIncludeHintInWrongAnswerFeedbackWithoutSupport() {
        saveTask("hint-task", TaskType.MULTIPLE_CHOICE, 10, Map.of("correct", List.of(1), "hint", "GEHEIMER TIPP"));

        SubmitRequest wrong = SubmitRequest.builder().payload(Map.of("selected", List.of(0))).build();
        SubmitResponse response = submissionService.submitTask("test-module", "hint-task", testUser, wrong);

        assertFalse(response.getFeedback().contains("GEHEIMER TIPP"));

        SupportResponse support = submissionService.requestSupport("test-module", "hint-task", testUser);
        assertEquals("GEHEIMER TIPP", support.hint());
        assertTrue(support.supportUsed());
    }

    private static final String SQL_SCHEMA = "CREATE TABLE mitarbeiter (id INT PRIMARY KEY, name VARCHAR(50), created_at INT); "
            + "INSERT INTO mitarbeiter VALUES (1, 'Max', 2020), (2, 'Lisa', 2021);";

    private void saveSqlTask(String slug) {
        saveTask(slug, TaskType.SQL, 10, Map.of(
                "schemaSql", SQL_SCHEMA,
                "solutionSql", "SELECT name FROM mitarbeiter WHERE created_at > 2020;"));
    }

    private boolean submitSql(String slug, String query) {
        SubmitRequest request = SubmitRequest.builder().payload(Map.of("result", query)).build();
        return submissionService.submitTask("test-module", slug, testUser, request).isCorrect();
    }

    @Test
    void shouldAcceptCorrectSqlQuery() {
        saveSqlTask("sql-ok");
        // Spaltenname enthält "CREATE" – früher fälschlich blockiert
        assertTrue(submitSql("sql-ok", "select name from mitarbeiter where created_at = 2021;"));
    }

    @Test
    void shouldBlockDangerousSql() {
        saveSqlTask("sql-evil");
        assertFalse(submitSql("sql-evil", "SELECT FILE_READ('/etc/passwd') AS name"));
        assertFalse(submitSql("sql-evil", "SELECT * FROM LINK_SCHEMA('x', '', 'jdbc:h2:mem:other', 'sa', '', 'PUBLIC')"));
        assertFalse(submitSql("sql-evil", "SELECT name FROM mitarbeiter; DROP TABLE mitarbeiter"));
        assertFalse(submitSql("sql-evil", "DELETE FROM mitarbeiter"));
    }

    @Test
    void shouldRejectOversizedSqlResults() {
        saveSqlTask("sql-big");
        assertFalse(submitSql("sql-big", "SELECT X AS name FROM SYSTEM_RANGE(1, 100000)"));
    }

    @Test
    void sqlStudentUserCannotUseAdminFunctions() throws Exception {
        // Prüft die zweite Verteidigungslinie direkt: auch ohne Schlüsselwortfilter darf der Schülernutzer
        // keine Dateien lesen
        String url = "jdbc:h2:mem:perm_test_" + System.nanoTime();
        try (var owner = java.sql.DriverManager.getConnection(url, "owner", "pw");
             var stmt = owner.createStatement()) {
            stmt.execute("CREATE USER student PASSWORD 'pw2'");
            stmt.execute("GRANT SELECT ON SCHEMA PUBLIC TO student");
            try (var student = java.sql.DriverManager.getConnection(url, "student", "pw2");
                 var studentStmt = student.createStatement()) {
                assertThrows(java.sql.SQLException.class,
                        () -> studentStmt.executeQuery("SELECT FILE_READ('/etc/hostname')"));
                assertThrows(java.sql.SQLException.class,
                        () -> studentStmt.executeQuery("SELECT * FROM CSVREAD('/etc/hostname')"));
            }
        }
    }

    private Task saveTask(String slug, TaskType type, int points, Map<String, Object> config) {
        Task task = new Task();
        task.setModule(testModule);
        task.setSlug(slug);
        task.setTitle(slug);
        task.setType(type);
        task.setDifficulty(Difficulty.EASY);
        task.setPoints(points);
        task.setConfig(config);
        return taskRepository.save(task);
    }
}

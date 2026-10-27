package de.codepath.backend.features.tasks;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.modules.ModuleRepository;
import de.codepath.backend.features.messaging.RealtimeUpdateService;
import de.codepath.backend.common.GlobalExceptionHandler.AccessDeniedException;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRepository;
import de.codepath.backend.users.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskSubmissionService {

    private final TaskRepository taskRepository;
    private final UserTaskCompletionRepository completionRepository;
    private final PracticeSubmissionRepository practiceSubmissionRepository;
    private final UserRepository userRepository;
    private final ModuleRepository moduleRepository;
    private final RealtimeUpdateService realtimeUpdateService;

    // Ab so vielen Fehlversuchen wird die Lösung angezeigt; danach gibt es keine Punkte mehr.
    // Gilt nur für Auswahl- und Lückenaufgaben – bei Code, SQL und Terminal gehört Ausprobieren dazu.
    private static final int SOLUTION_REVEAL_ATTEMPTS = 3;
    private static final Set<TaskType> REVEALS_SOLUTION = EnumSet.of(
            TaskType.MULTIPLE_CHOICE, TaskType.FILL_BLANK, TaskType.SORTING);
    private static final int FIRST_TRY_BONUS = 5;
    // Obergrenze pro Abgabe, damit unplausible Client-Werte die Statistik nicht verzerren
    private static final long MAX_TIME_PER_SUBMISSION_SECONDS = 3600;

    private static final int SQL_MAX_ROWS = 1000;
    private static final int SQL_TIMEOUT_SECONDS = 2;
    private static final Pattern SQL_FORBIDDEN = Pattern.compile(
            "\\b(CREATE|DROP|ALTER|CALL|EXECUTE|GRANT|REVOKE|TRUNCATE|DELETE|UPDATE|INSERT|MERGE|RUNSCRIPT|SCRIPT|SET|SHUTDOWN|BACKUP|LINK_SCHEMA|FILE_READ|FILE_WRITE|CSVREAD|CSVWRITE)\\b",
            Pattern.CASE_INSENSITIVE);

    @Transactional
    public SubmitResponse submitTask(String moduleSlug, String taskSlug, User user, SubmitRequest request) {
        Module module = findAccessibleModule(moduleSlug, user);

        // Check Deadline
        if (module.getAvailableUntil() != null && module.getAvailableUntil().isBefore(LocalDateTime.now())) {
            return SubmitResponse.builder()
                    .isCorrect(false)
                    .feedback("Die Bearbeitungszeit für dieses Modul ist abgelaufen.")
                    .build();
        }

        Task task = findTask(module, taskSlug);
        UserTaskCompletion progress = findOrCreateProgress(user, task);

        if (progress.isLocked()) {
            return SubmitResponse.builder()
                    .isCorrect(false)
                    .isLocked(true)
                    .feedback("Diese Aufgabe ist gesperrt.")
                    .build();
        }

        // Bearbeitungszeit dient nur noch der Statistik und beeinflusst keine Punkte mehr
        if (request.getTimeSpentSeconds() != null) {
            long seconds = Math.clamp(request.getTimeSpentSeconds(), 0L, MAX_TIME_PER_SUBMISSION_SECONDS);
            progress.setTimeSpentSeconds(progress.getTimeSpentSeconds() + seconds);
        }

        // Ob Hilfe genutzt wurde, weiß nur der Server (siehe requestSupport) – der Client-Wert wird ignoriert
        boolean supportUsed = progress.isSupportUsed();
        boolean isCorrect = validatePayload(task, request.getPayload());

        if (isCorrect) {
            if (task.getType() == TaskType.PRACTICE) {
                return handlePracticeSubmission(task, user, request.getPayload(), supportUsed);
            } else {
                if (!progress.isCompleted()) {
                    // Synchron in derselben Transaktion (ADR-1); die Zeilensperre auf dem Nutzer verhindert,
                    // dass zwei gleichzeitige Abgaben die Punkte doppelt vergeben
                    User lockedUser = userRepository.findByIdForUpdate(user.getId()).orElseThrow();
                    // Skalare Abfrage geht an die Datenbank, nicht an den Cache der Persistenz
                    if (completionRepository.isCompleted(user.getId(), task.getId())) {
                        return SubmitResponse.builder()
                                .isCorrect(true)
                                .pointsAwarded(0)
                                .failedAttempts(progress.getFailedAttempts())
                                .feedback("Korrekt! Du hast diese Aufgabe bereits gelöst.")
                                .build();
                    }

                    int awardedPoints = calculatePoints(task, progress, supportUsed);
                    progress.setCompleted(true);
                    progress.setCompletedAt(LocalDateTime.now());
                    progress.setPointsAwarded(awardedPoints);
                    completionRepository.save(progress);
                    lockedUser.setTotalPoints(lockedUser.getTotalPoints() + awardedPoints);
                    userRepository.save(lockedUser);
                    realtimeUpdateService.publishLeaderboardUpdate();

                    String feedback = awardedPoints > 0
                            ? "Super! Das war richtig."
                            : "Richtig! Da die Lösung bereits angezeigt wurde, gibt es für diese Aufgabe keine Punkte.";
                    return SubmitResponse.builder()
                            .isCorrect(true)
                            .pointsAwarded(awardedPoints)
                            .failedAttempts(progress.getFailedAttempts())
                            .feedback(feedback)
                            .build();
                } else {
                    return SubmitResponse.builder()
                            .isCorrect(true)
                            .pointsAwarded(0)
                            .failedAttempts(progress.getFailedAttempts())
                            .feedback("Korrekt! Du hast diese Aufgabe bereits gelöst.")
                            .build();
                }
            }
        }

        // Handle wrong answer
        if (!progress.isCompleted()) {
            progress.setFailedAttempts(progress.getFailedAttempts() + 1);
            
            // Lock Multiple Choice after 3 attempts
            if (task.getType() == TaskType.MULTIPLE_CHOICE && progress.getFailedAttempts() >= 3) {
                progress.setLocked(true);
            }
            
            completionRepository.save(progress);
        }

        // Der Hinweis kostet Punkte und wird deshalb nur nach einer angeforderten Hilfestellung mitgeschickt
        String feedback = supportUsed
                ? "Leider falsch. Tipp: " + TaskConfigFilter.hint(task).orElse("Probiere es noch einmal!")
                : "Leider falsch. Probiere es noch einmal!";
        SubmitResponse.SubmitResponseBuilder responseBuilder = SubmitResponse.builder()
                .isCorrect(false)
                .isLocked(progress.isLocked())
                .pointsAwarded(0)
                .failedAttempts(progress.getFailedAttempts())
                .feedback(feedback);

        if (isSolutionRevealed(task, progress)) {
            responseBuilder.correctSolution(getSolution(task));
            if (progress.isLocked()) {
                responseBuilder.feedback("Drei Fehlversuche. Diese Aufgabe wurde gesperrt.");
            }
        }

        return responseBuilder.build();
    }

    /**
     * Fordert die Hilfestellung an. Der Server merkt sich das und zieht beim Lösen die Hälfte der Punkte ab.
     */
    @Transactional
    public SupportResponse requestSupport(String moduleSlug, String taskSlug, User user) {
        Module module = findAccessibleModule(moduleSlug, user);
        Task task = findTask(module, taskSlug);
        UserTaskCompletion progress = findOrCreateProgress(user, task);

        if (!progress.isCompleted() && !progress.isLocked() && !progress.isSupportUsed()) {
            progress.setSupportUsed(true);
            completionRepository.save(progress);
        }

        return new SupportResponse(TaskConfigFilter.hint(task).orElse(null), progress.isSupportUsed());
    }

    private Module findAccessibleModule(String moduleSlug, User user) {
        Module module = moduleRepository.findBySlug(moduleSlug)
                .orElseThrow(() -> new RuntimeException("Module not found"));
        if (user.getRole() != UserRole.ADMIN && !Boolean.TRUE.equals(module.getIsUnlocked())) {
            throw new AccessDeniedException("Dieses Modul ist aktuell gesperrt.");
        }
        return module;
    }

    private Task findTask(Module module, String taskSlug) {
        return taskRepository.findByModuleIdAndSlug(module.getId(), taskSlug)
                .orElseThrow(() -> new RuntimeException("Task not found"));
    }

    private UserTaskCompletion findOrCreateProgress(User user, Task task) {
        return completionRepository.findByUser_IdAndTask_Id(user.getId(), task.getId())
                .orElseGet(() -> {
                    UserTaskCompletion c = new UserTaskCompletion();
                    c.setUser(user);
                    c.setTask(task);
                    return c;
                });
    }

    private static boolean isSolutionRevealed(Task task, UserTaskCompletion progress) {
        return REVEALS_SOLUTION.contains(task.getType()) && progress.getFailedAttempts() >= SOLUTION_REVEAL_ATTEMPTS;
    }

    private int calculatePoints(Task task, UserTaskCompletion progress, boolean supportUsed) {
        // Wer die Lösung schon gesehen hat, soll sie nicht für Punkte abschreiben können
        if (isSolutionRevealed(task, progress)) {
            return 0;
        }

        int points = task.getPoints();

        // Bonus nur für Lösen ohne Fehlversuch und ohne Hilfe (früher: Zeitbonus, der sich fälschen ließ)
        if (progress.getFailedAttempts() == 0 && !supportUsed) {
            points += FIRST_TRY_BONUS;
        }

        if (supportUsed) {
            points = Math.max(1, points / 2);
        }
        
        return points;
    }

    private String getSolution(Task task) {
        return switch (task.getType()) {
            case MULTIPLE_CHOICE -> "Korrekte Antworten: " + task.getConfig().get("correct");
            case FILL_BLANK -> "Lösung: " + ((List<?>) task.getConfig().get("blanks")).stream()
                    .map(TaskSubmissionService::blankAnswer)
                    .reduce((a, b) -> a + ", " + b).orElse("");
            case FILL_CODE, CODE -> "Erwartete Ausgabe: " + task.getConfig().get("expectedOutput");
            case SORTING -> "Richtige Reihenfolge: " + task.getConfig().get("correctOrder");
            case SQL -> "Musterlösung: " + task.getConfig().get("solutionSql");
            case TERMINAL -> "Erwarteter Endzustand: " + task.getConfig().get("expectedState");
            case PRACTICE -> "Manuelle Prüfung erforderlich.";
        };
    }

    private boolean validatePayload(Task task, Map<String, Object> payload) {
        if (payload == null) return false;
        
        try {
            return switch (task.getType()) {
                case MULTIPLE_CHOICE -> validateMultipleChoice(task, payload);
                case FILL_BLANK -> validateFillBlank(task, payload);
                case FILL_CODE, CODE -> validateCode(task, payload);
                case SORTING -> validateSorting(task, payload);
                case SQL -> validateSql(task, payload);
                case TERMINAL -> validateTerminal(task, payload);
                case PRACTICE -> true;
            };
        } catch (Exception e) {
            log.error("Validation error for task type {}: {}", task.getType(), e.getMessage());
            return false;
        }
    }

    private boolean validateMultipleChoice(Task task, Map<String, Object> payload) {
        List<?> selected = (List<?>) payload.get("selected");
        List<?> correct = (List<?>) task.getConfig().get("correct");
        
        if (selected == null || correct == null) return false;
        return selected.size() == correct.size() && selected.containsAll(correct);
    }

    private boolean validateFillBlank(Task task, Map<String, Object> payload) {
        List<?> answers = (List<?>) payload.get("answers");
        List<?> blanks = (List<?>) task.getConfig().get("blanks");
        
        if (answers == null || blanks == null || answers.size() != blanks.size()) return false;

        for (int i = 0; i < blanks.size(); i++) {
            // Lücken sind entweder ein String (die Antwort) oder ein Objekt mit answer und caseSensitive
            Object blank = blanks.get(i);
            String expected = blankAnswer(blank);
            boolean caseSensitive = blank instanceof Map<?, ?> blankConfig
                    && Boolean.TRUE.equals(blankConfig.get("caseSensitive"));
            
            String actual = answers.get(i) != null ? answers.get(i).toString().trim() : "";
            
            if (expected == null) return false;
            expected = expected.trim();
            
            if (caseSensitive) {
                if (!expected.equals(actual)) return false;
            } else {
                if (!expected.equalsIgnoreCase(actual)) return false;
            }
        }
        return true;
    }

    private static String blankAnswer(Object blank) {
        if (blank instanceof Map<?, ?> blankConfig) {
            return blankConfig.get("answer") != null ? blankConfig.get("answer").toString() : null;
        }
        return blank != null ? blank.toString() : null;
    }

    private boolean validateCode(Task task, Map<String, Object> payload) {
        String output = payload.get("output") != null ? payload.get("output").toString() : null;
        String expected = task.getConfig().get("expectedOutput") != null ? task.getConfig().get("expectedOutput").toString() : null;
        
        if (expected == null) return false;
        return output != null && output.trim().equals(expected.trim());
    }

    private boolean validateSorting(Task task, Map<String, Object> payload) {
        List<?> currentOrder = (List<?>) payload.get("order");
        List<?> correctOrder = (List<?>) task.getConfig().get("correctOrder");
        if (currentOrder == null || correctOrder == null) return false;
        return Objects.equals(currentOrder, correctOrder);
    }

    private boolean validateSql(Task task, Map<String, Object> payload) {
        String studentQuery = (String) payload.get("result");
        String schemaSql = (String) task.getConfig().get("schemaSql");
        String solutionSql = (String) task.getConfig().get("solutionSql");

        if (studentQuery == null || solutionSql == null) return false;

        // Vorprüfung: nur eine einzelne lesende Abfrage. Die eigentliche Absicherung ist der
        // eingeschränkte Datenbanknutzer weiter unten; die Prüfung hier liefert nur früh ein klares Nein.
        String sanitizedQuery = stripTrailingSemicolon(studentQuery.trim());
        String upperQuery = sanitizedQuery.toUpperCase(Locale.ROOT);
        if (!upperQuery.startsWith("SELECT") && !upperQuery.startsWith("WITH") && !upperQuery.startsWith("VALUES")) {
            log.warn("Blocked non-query SQL: {}", studentQuery);
            return false;
        }
        if (sanitizedQuery.contains(";") || SQL_FORBIDDEN.matcher(withoutStringLiterals(sanitizedQuery)).find()) {
            log.warn("Blocked SQL query with forbidden statement or function: {}", studentQuery);
            return false;
        }

        // Eigene In-Memory-Datenbank je Abgabe. Schema und Musterlösung laufen als Owner, die Schülerabfrage
        // als Nutzer, der nur lesen darf – FILE_READ, LINK_SCHEMA, CSVREAD usw. brauchen Admin-Rechte.
        String dbName = "sql_val_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        String ownerPassword = UUID.randomUUID().toString();
        String studentPassword = UUID.randomUUID().toString();

        try (Connection owner = DriverManager.getConnection(url, "owner", ownerPassword)) {
            List<Map<String, Object>> expectedResults;
            try (Statement stmt = owner.createStatement()) {
                if (schemaSql != null && !schemaSql.isBlank()) {
                    stmt.execute(schemaSql);
                }
                expectedResults = executeAndMap(stmt, solutionSql);
                stmt.execute("CREATE USER student PASSWORD '" + studentPassword + "'");
                stmt.execute("GRANT SELECT ON SCHEMA PUBLIC TO student");
            }

            try (Connection student = DriverManager.getConnection(url, "student", studentPassword);
                 Statement stmt = student.createStatement()) {
                student.setReadOnly(true);
                stmt.setQueryTimeout(SQL_TIMEOUT_SECONDS);
                // Eine Zeile mehr als erlaubt holen, um übergroße Ergebnisse zu erkennen
                stmt.setMaxRows(SQL_MAX_ROWS + 1);
                List<Map<String, Object>> studentResults = executeAndMap(stmt, sanitizedQuery);
                if (studentResults.size() > SQL_MAX_ROWS) {
                    log.warn("SQL result exceeds {} rows", SQL_MAX_ROWS);
                    return false;
                }
                return Objects.equals(expectedResults, studentResults);
            }
            // Die Datenbank verschwindet, sobald die letzte Verbindung geschlossen ist
        } catch (SQLException e) {
            log.warn("SQL Validation failed or blocked by security: {}", e.getMessage());
            return false;
        }
    }

    private static String stripTrailingSemicolon(String sql) {
        String result = sql;
        while (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private static String withoutStringLiterals(String sql) {
        return sql.replaceAll("'([^']|'')*'", "''");
    }

    private List<Map<String, Object>> executeAndMap(Statement stmt, String sql) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnName(i).toUpperCase(), rs.getObject(i));
                }
                results.add(row);
            }
        }
        return results;
    }

    private boolean validateTerminal(Task task, Map<String, Object> payload) {
        // Validate final state of virtual FS
        String actualState = (String) payload.get("finalState");
        String expectedState = (String) task.getConfig().get("expectedState");
        if (actualState == null || expectedState == null) return false;
        return actualState.equals(expectedState);
    }

    private SubmitResponse handlePracticeSubmission(Task task, User user, Map<String, Object> payload, boolean supportUsed) {
        // Prevent spam: check if a PENDING submission already exists
        boolean hasPending = practiceSubmissionRepository.existsByUser_IdAndTask_IdAndStatus(user.getId(), task.getId(), SubmissionStatus.PENDING);
        
        if (hasPending) {
            return SubmitResponse.builder()
                    .isCorrect(true)
                    .pointsAwarded(0)
                    .status("PENDING")
                    .feedback("Du hast bereits eine Abgabe eingereicht, die noch geprüft wird.")
                    .build();
        }

        String content = "";
        if (payload != null && payload.containsKey("practiceContent")) {
            content = (String) payload.get("practiceContent");
        }

        PracticeSubmission submission = new PracticeSubmission();
        submission.setTask(task);
        submission.setUser(user);
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setContent(content);
        submission.setSupportUsed(supportUsed);
        submission.setSubmittedAt(LocalDateTime.now());
        practiceSubmissionRepository.save(submission);

        return SubmitResponse.builder()
                .isCorrect(true)
                .pointsAwarded(0)
                .status("PENDING")
                .feedback("Abgabe eingereicht! Die Lehrkraft wird sie prüfen.")
                .build();
    }

}

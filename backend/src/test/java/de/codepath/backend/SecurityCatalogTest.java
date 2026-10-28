package de.codepath.backend;

import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.engine.CodeRunner;
import de.codepath.backend.engine.TaskContext;
import de.codepath.backend.engine.TaskHandlerRegistry;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sicherheits-Testkatalog aus dem Überarbeitungsplan: Kein Punkt lässt sich durch einen manipulierten
 * Request erreichen, keine Lösung erreicht den Browser vor dem Lösen oder Aufdecken.
 */
class SecurityCatalogTest extends IntegrationTestBase {

    @Autowired
    TaskHandlerRegistry registry;

    private static final List<String> SOLUTION_FIELDS = List.of("\"correct\"", "\"correctOrder\"", "\"solutionSql\"",
            "\"schemaSql\"", "\"expectedDirectories\"", "\"expectedFiles\"", "\"expectedContents\"", "\"solution\":{",
            "\"explanation\":\"", "\"answers\"", "\"rubricScores\"", "\"pairs\"", "\"targets\"", "\"indent\"", "\"gates\"");

    private Map<String, Object> sample(PublishedTask task, User user) {
        return registry.get(task.type()).sampleAnswer(task.spec(), TaskContext.of(user.getId(), task.taskId()));
    }

    private Map<String, Object> wrong(PublishedTask task, User user) {
        return registry.get(task.type()).wrongAnswer(task.spec(), TaskContext.of(user.getId(), task.taskId()));
    }

    private String url(PublishedTask task) {
        return "/api/modules/" + task.module().getSlug() + "/" + task.task().getSlug();
    }

    @Test
    void noStudentResponseContainsASolution() throws Exception {
        Course course = course("8a");
        User user = student("lena", course);
        contentService.modules().forEach(m -> unlock(course, m.getSlug()));
        List<PublishedTask> tasks = contentService.allPublishedTasks();
        assertTrue(tasks.size() >= 120, "Startinhalt fehlt: " + tasks.size());

        for (PublishedTask task : tasks) {
            String response = getAs(as(user), url(task)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            for (String field : SOLUTION_FIELDS) {
                assertFalse(response.contains(field), task.module().getSlug() + "/" + task.task().getSlug() + " verrät " + field);
            }
            assertTrue(response.contains("\"hints\":[]"), "Hinweise vor dem Anfordern sichtbar: " + task.task().getSlug());
        }
        for (var module : contentService.modules()) {
            String response = getAs(as(user), "/api/modules/" + module.getSlug()).andReturn().getResponse().getContentAsString();
            SOLUTION_FIELDS.forEach(f -> assertFalse(response.contains(f), module.getSlug() + " verrät " + f));
        }
    }

    @Test
    void lockedModulesAndOtherCoursesStayClosed() throws Exception {
        Course a = course("8a");
        Course b = course("8b");
        User inA = student("anna", a);
        User inB = student("ben", b);
        unlock(a, "git");
        PublishedTask task = contentService.publishedTask("git", "g1");

        getAs(as(inA), url(task)).andExpect(status().isOk());
        getAs(as(inB), url(task)).andExpect(status().isForbidden());
        postAs(as(inB), url(task) + "/submit", sample(task, inB)).andExpect(status().isForbidden());
        postAs(as(inB), url(task) + "/hint", null).andExpect(status().isForbidden());
        postAs(as(inB), url(task) + "/reveal", null).andExpect(status().isForbidden());
        assertEquals(0, body(getAs(as(inB), "/api/modules")).size());
        assertEquals(0, points(inB));

        User noCourse = student("ohne", null);
        getAs(as(noCourse), url(task)).andExpect(status().isForbidden());
    }

    @Test
    void deadlineClosesSubmissionsButKeepsTheModuleVisible() throws Exception {
        Course course = course("8a");
        User user = student("lena", course);
        unlock(course, "git");
        releaseService.setDeadline(course.getId(), contentService.module("git").getId(), LocalDateTime.now().minusMinutes(1));
        PublishedTask task = contentService.publishedTask("git", "g1");
        getAs(as(user), url(task)).andExpect(status().isOk()).andExpect(jsonPath("$.isOpen").value(false));
        postAs(as(user), url(task) + "/submit", sample(task, user)).andExpect(status().isForbidden());
    }

    @Test
    void pointsAreAwardedExactlyOnceEvenUnderConcurrentSubmissions() throws Exception {
        Course course = course("8a");
        User user = student("lena", course);
        unlock(course, "git");
        PublishedTask task = contentService.publishedTask("git", "g1");
        Map<String, Object> right = sample(task, user);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            results.add(pool.submit(() -> postAs(as(user), url(task) + "/submit", right).andReturn().getResponse().getStatus()));
        }
        for (Future<Integer> f : results) assertEquals(200, f.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        int expected = task.version().getPoints() + task.version().getPoints() * task.rules().firstTryBonusPercent() / 100;
        assertEquals(expected, points(user));
        assertEquals(1, jdbc.queryForObject("select count(*) from points_entries where user_id = ?", Integer.class, user.getId()));
    }

    @Test
    void clientCannotClaimHelpTimeOrResults() throws Exception {
        Course course = course("8a");
        User user = student("lena", course);
        unlock(course, "git");
        PublishedTask task = contentService.publishedTask("git", "g1");

        // Frühere Manipulationen: Ergebnis, Hilfe und Zeit selbst melden
        Map<String, Object> forged = new HashMap<>(wrong(task, user));
        forged.put("isCorrect", true);
        forged.put("supportUsed", false);
        forged.put("timeSpentSeconds", 1);
        postAs(as(user), url(task) + "/submit", forged).andExpect(status().isOk()).andExpect(jsonPath("$.isCorrect").value(false));
        assertEquals(0, points(user));
    }

    @Test
    void hintsAndRevealedSolutionsCostPoints() throws Exception {
        Course course = course("8a");
        User user = student("lena", course);
        unlock(course, "git");
        PublishedTask withHint = contentService.allPublishedTasks().stream()
                .filter(t -> t.module().getSlug().equals("git") && !t.version().getHints().isEmpty() && t.type().name().equals("MULTIPLE_CHOICE"))
                .findFirst().orElseThrow();
        JsonNode hint = body(postAs(as(user), url(withHint) + "/hint", null).andExpect(status().isOk()));
        assertEquals(1, hint.get("hints").size());
        postAs(as(user), url(withHint) + "/submit", sample(withHint, user)).andExpect(status().isOk());
        int base = withHint.version().getPoints();
        assertEquals(Math.round(base * 0.75), points(user), "Hinweis kostet 25 %, kein Bonus");

        PublishedTask other = contentService.allPublishedTasks().stream()
                .filter(t -> t.module().getSlug().equals("git") && t.type().name().equals("MULTIPLE_CHOICE") && !t.taskId().equals(withHint.taskId()))
                .findFirst().orElseThrow();
        JsonNode revealed = body(postAs(as(user), url(other) + "/reveal", null).andExpect(status().isOk()));
        assertTrue(revealed.get("solution").has("correct"));
        long before = points(user);
        postAs(as(user), url(other) + "/submit", sample(other, user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pointsAwarded").value(0));
        assertEquals(before, points(user));
    }

    @Test
    void attemptsRunOutAndPartialCreditIsKept() throws Exception {
        Course course = course("8a");
        User user = student("lena", course);
        unlock(course, "web-entwicklung");
        PublishedTask fill = contentService.allPublishedTasks().stream()
                .filter(t -> t.type().name().equals("FILL_BLANK") && t.version().getConfig().get("blanks") instanceof List<?> l && l.size() >= 2)
                .filter(t -> t.module().getSlug().equals("web-entwicklung"))
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<Object> right = new ArrayList<>((List<Object>) sample(fill, user).get("answers"));
        right.set(right.size() - 1, "absichtlich-falsch");
        for (int i = 0; i < 3; i++) {
            postAs(as(user), url(fill) + "/submit", Map.of("answers", right)).andExpect(status().isOk());
        }
        getAs(as(user), url(fill)).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.solution.answers").isArray());
        postAs(as(user), url(fill) + "/submit", sample(fill, user)).andExpect(status().isConflict());
        assertTrue(points(user) > 0 && points(user) < fill.version().getPoints(), "Teilpunkte erwartet, war " + points(user));
    }

    @Test
    void teacherLockBlocksSubmissions() throws Exception {
        Course course = course("8a");
        User user = student("lena", course);
        unlock(course, "git");
        PublishedTask task = contentService.publishedTask("git", "g1");
        putAs(teacher(), "/api/admin/students/lena/tasks/" + task.taskId() + "/toggle-lock", null).andExpect(status().isOk());
        postAs(as(user), url(task) + "/submit", sample(task, user)).andExpect(status().isConflict());
    }

    @Test
    void terminalAndSqlAreGradedOnTheServer() throws Exception {
        Course course = course("8a");
        User user = student("lena", course);
        unlock(course, "git");
        unlock(course, "datenbanken");
        PublishedTask terminal = contentService.publishedTask("git", "terminal-git-init");
        postAs(as(user), url(terminal) + "/submit", Map.of("finalState", "website,website/index.html,website/styles.css"))
                .andExpect(jsonPath("$.isCorrect").value(false));
        postAs(as(user), url(terminal) + "/submit", Map.of("commands", List.of("mkdir website", "cd website", "touch index.html", "touch styles.css")))
                .andExpect(jsonPath("$.isCorrect").value(true));

        PublishedTask sql = contentService.publishedTask("datenbanken", "sql-query-pro");
        postAs(as(user), url(sql) + "/submit", Map.of("query", "SELECT FILE_READ('/etc/passwd')"))
                .andExpect(jsonPath("$.outcome").value("INVALID"));
        // Ohne Abteilungsfilter darf es nicht reichen (Testdaten enthalten Vertrieb mit hohem Gehalt)
        postAs(as(user), url(sql) + "/submit", Map.of("query", "SELECT * FROM mitarbeiter WHERE gehalt > 5000"))
                .andExpect(jsonPath("$.isCorrect").value(false));
        postAs(as(user), url(sql) + "/submit", Map.of("query", "SELECT * FROM mitarbeiter WHERE abteilung = 'IT' AND gehalt > 5000"))
                .andExpect(jsonPath("$.isCorrect").value(true))
                .andExpect(jsonPath("$.details.result.rowCount").value(2));
    }

    @Test
    void codeIsGradedByTheRunnerAndHardcodedOutputFails(@Autowired CodeRunner runner) throws Exception {
        assumeTrue(runner.available(), "Nur mit Code-Runner (CODEPATH_RUNNER_URL)");
        Course course = course("8a");
        User user = student("lena", course);
        unlock(course, "algorithmen");
        PublishedTask search = contentService.publishedTask("algorithmen", "a5");
        // Nur die sichtbare Ausgabe nachdrucken reicht nicht: versteckte Tests rufen die Funktion anders auf
        postAs(as(user), url(search) + "/submit", Map.of("code", "def binaere_suche(liste, ziel):\n    return 5\nprint(5)\nprint(-1)\n"))
                .andExpect(jsonPath("$.isCorrect").value(false))
                .andExpect(jsonPath("$.outcome").value("PARTIAL"));
        postAs(as(user), url(search) + "/submit", Map.of("code", "import socket\nsocket.create_connection(('1.1.1.1', 80), timeout=1)\n"))
                .andExpect(jsonPath("$.isCorrect").value(false));
        postAs(as(user), url(search) + "/submit", sample(search, user))
                .andExpect(jsonPath("$.isCorrect").value(true));
        assertTrue(points(user) > 0);
    }

    @Test
    void onlyTeachersMayRunCodeFromTheEditor(@Autowired CodeRunner runner) throws Exception {
        User user = student("lena", course("8a"));
        Map<String, Object> request = Map.of("code", "print(input() * 2)", "tests", List.of(Map.of("stdin", "ab")));
        postAs(as(user), "/api/admin/content/run-solution", request).andExpect(status().isForbidden());
        var result = postAs(teacher(), "/api/admin/content/run-solution", request).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(runner.available()));
        if (runner.available()) result.andExpect(jsonPath("$.outputs[0].stdout").value("abab\n"));
    }
}

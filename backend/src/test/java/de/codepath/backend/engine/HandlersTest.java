package de.codepath.backend.engine;

import de.codepath.backend.engine.handlers.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static de.codepath.backend.engine.HandlerTestSupport.CONTEXT;
import static de.codepath.backend.engine.HandlerTestSupport.spec;
import static org.junit.jupiter.api.Assertions.*;

/** Je Aufgabentyp: richtige, falsche und manipulierte Antworten. */
class HandlersTest {

    static final Set<String> SOLUTION_KEYS = Set.of("correct", "correctOrder", "answers", "solutionSql", "schemaSql",
            "expectedDirectories", "expectedFiles", "expectedContents", "tests", "solution", "hint", "hints");

    static void assertNoSolution(Map<String, Object> view) {
        String json = view.toString();
        for (String key : SOLUTION_KEYS) {
            assertFalse(json.contains(key + "="), "Öffentliche Sicht enthält " + key + ": " + json);
        }
    }

    @Nested
    class MultipleChoice {
        final MultipleChoiceHandler handler = new MultipleChoiceHandler();
        final TaskSpec spec = spec(TaskType.MULTIPLE_CHOICE, 10, Map.of(
                "options", List.of(Map.of("id", "a", "text", "A"), Map.of("id", "b", "text", "B", "explanation", "weil"), Map.of("id", "c", "text", "C")),
                "correct", List.of("b")));

        @Test
        void correctWrongAndManipulated() {
            assertTrue(handler.evaluate(spec, Map.of("selected", List.of("b")), CONTEXT).isCorrect());
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, Map.of("selected", List.of("a")), CONTEXT).outcome());
            // Alles ankreuzen darf bei Einfachauswahl nicht helfen
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of("selected", List.of("a", "b", "c")), CONTEXT).outcome());
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, Map.of("selected", List.of("x")), CONTEXT).outcome());
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of("selected", "b"), CONTEXT).outcome());
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of(), CONTEXT).outcome());
        }

        @Test
        void publicViewHidesSolutionAndIsStablePerStudent() {
            Map<String, Object> view = handler.publicView(spec, CONTEXT);
            assertNoSolution(view);
            assertFalse(view.toString().contains("weil"));
            assertEquals(view, handler.publicView(spec, CONTEXT));
            assertTrue(handler.validate(spec.config(), 10).isEmpty());
            assertFalse(handler.validate(Map.of("options", List.of(), "correct", List.of("z")), 10).isEmpty());
        }
    }

    @Nested
    class FillBlank {
        final FillBlankHandler handler = new FillBlankHandler();
        final TaskSpec spec = spec(TaskType.FILL_BLANK, 10, Map.of(
                "template", "<___> und <___>",
                "blanks", List.of(Map.of("answers", List.of("html")), Map.of("answers", List.of("Body"), "caseSensitive", true))));

        @Test
        void partialCreditPerBlank() {
            assertTrue(handler.evaluate(spec, Map.of("answers", List.of(" HTML ", "Body")), CONTEXT).isCorrect());
            Evaluation half = handler.evaluate(spec, Map.of("answers", List.of("html", "body")), CONTEXT);
            assertEquals(Evaluation.Outcome.PARTIAL, half.outcome());
            assertEquals(0.5, half.score(), 1e-9);
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, Map.of("answers", List.of(Map.of("x", 1), 5)), CONTEXT).outcome());
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, Map.of("answers", "html"), CONTEXT).outcome());
        }

        @Test
        void validatesGapCount() {
            assertTrue(handler.validate(spec.config(), 10).isEmpty());
            assertFalse(handler.validate(Map.of("template", "___", "blanks", List.of()), 10).isEmpty());
            assertNoSolution(handler.publicView(spec, CONTEXT));
        }
    }

    @Nested
    class Sorting {
        final SortingHandler handler = new SortingHandler();
        final TaskSpec spec = spec(TaskType.SORTING, 10, Map.of(
                "items", List.of(Map.of("id", "s1", "text", "1"), Map.of("id", "s2", "text", "2"), Map.of("id", "s3", "text", "3"), Map.of("id", "s4", "text", "4")),
                "correctOrder", List.of("s1", "s2", "s3", "s4")));

        @Test
        void scoresLongestCorrectSubsequence() {
            assertTrue(handler.evaluate(spec, Map.of("order", List.of("s1", "s2", "s3", "s4")), CONTEXT).isCorrect());
            Evaluation partial = handler.evaluate(spec, Map.of("order", List.of("s1", "s2", "s4", "s3")), CONTEXT);
            assertEquals(2.0 / 3, partial.score(), 1e-9);
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, Map.of("order", List.of("s4", "s3", "s2", "s1")), CONTEXT).outcome());
            // Doppelte oder fremde IDs gelten nicht
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of("order", List.of("s1", "s1", "s2", "s3")), CONTEXT).outcome());
        }

        @Test
        void neverShipsTheSolutionOrder() {
            for (long seed = 0; seed < 200; seed++) {
                List<?> items = (List<?>) handler.publicView(spec, new TaskContext(seed)).get("items");
                assertNotEquals(List.of("s1", "s2", "s3", "s4"), items.stream().map(i -> ((Map<?, ?>) i).get("id")).toList());
            }
        }
    }

    @Nested
    class Sql {
        final SqlHandler handler = new SqlHandler(new SqlSandbox());
        final TaskSpec spec = spec(TaskType.SQL, 10, Map.of(
                "schemaSql", "CREATE TABLE t (id INT, name VARCHAR(20), created_at INT); INSERT INTO t VALUES (1,'a',2020),(2,'b',2021);",
                "solutionSql", "SELECT name FROM t WHERE created_at > 2020"));

        @Test
        void comparesResults() {
            Evaluation ok = handler.evaluate(spec, Map.of("query", "select name from t where id = 2;"), CONTEXT);
            assertTrue(ok.isCorrect());
            assertTrue(ok.details().containsKey("result"));
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, Map.of("query", "SELECT name FROM t"), CONTEXT).outcome());
        }

        @Test
        void blocksEverythingButOneSelect() {
            for (String evil : List.of(
                    "SELECT FILE_READ('/etc/passwd')",
                    "SELECT * FROM CSVREAD('/etc/hostname')",
                    "SELECT * FROM LINK_SCHEMA('x', '', 'jdbc:h2:mem:o', 'sa', '', 'PUBLIC')",
                    "SELECT name FROM t; DROP TABLE t",
                    "DROP TABLE t",
                    "CALL 1",
                    "CREATE ALIAS X AS 'String x() { return \"\"; }'")) {
                assertNotEquals(Evaluation.Outcome.CORRECT, handler.evaluate(spec, Map.of("query", evil), CONTEXT).outcome(), evil);
            }
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, Map.of("query", "SELECT X FROM SYSTEM_RANGE(1, 100000)"), CONTEXT).outcome());
        }

        @Test
        void studentUserCannotCallAdminFunctionsEvenPastTheParser() throws Exception {
            // Zweite Verteidigungslinie direkt geprüft: Funktionsaufruf in Anführungszeichen umgeht den Wortfilter
            var sandbox = new SqlSandbox();
            assertThrows(java.sql.SQLException.class, () -> sandbox.compare("CREATE TABLE t (x INT)", "SELECT 1", "SELECT \"FILE_READ\"('/etc/hostname')"));
        }
    }

    @Nested
    class Terminal {
        final TerminalHandler handler = new TerminalHandler();
        final TaskSpec spec = spec(TaskType.TERMINAL, 20, Map.of(
                "expectedDirectories", List.of("website"),
                "expectedFiles", List.of("website/index.html", "website/styles.css")));

        @Test
        void replaysCommandsOnTheServer() {
            assertTrue(handler.evaluate(spec, Map.of("commands", List.of("mkdir website", "cd website", "touch index.html styles.css")), CONTEXT).isCorrect());
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, Map.of("commands", List.of("mkdir website", "touch website/index.html")), CONTEXT).outcome());
            // Zusätzliche Dateien sind nicht verlangt
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, Map.of("commands",
                    List.of("mkdir website", "touch website/index.html website/styles.css website/extra.txt")), CONTEXT).outcome());
            // Der alte Weg – Endzustand behaupten – zählt nicht mehr
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of("finalState", "website,website/index.html,website/styles.css"), CONTEXT).outcome());
        }

        @Test
        void simulatorSupportsCommonCommands() {
            TerminalSimulator sim = new TerminalSimulator(List.of(), Map.of());
            sim.execute("mkdir -p a/b");
            sim.execute("echo \"Hallo Welt\" > a/b/gruss.txt");
            sim.execute("cp a/b/gruss.txt a/kopie.txt");
            sim.execute("mv a/kopie.txt a/b");
            assertEquals("Hallo Welt", sim.execute("cat a/b/kopie.txt"));
            sim.execute("cd a/b");
            assertEquals("~/a/b", sim.execute("pwd"));
            assertEquals("gruss.txt  kopie.txt", sim.execute("ls"));
            sim.execute("cd ../..");
            sim.execute("rm -r a");
            assertTrue(sim.directories().isEmpty());
            assertTrue(sim.files().isEmpty());
            assertTrue(sim.execute("rm nichtda").startsWith("rm:"));
        }
    }

    @Nested
    class Code {
        final CodeRunner fakeRunner = new CodeRunner() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public RunResult run(String code, String stdin) {
                // Simuliert ein Programm, das die Eingabe verdoppelt ausgibt
                if (code.contains("while True")) return new RunResult(RunResult.Status.TIMEOUT, "", "");
                if (code.contains("verdoppeln")) return new RunResult(RunResult.Status.OK, String.valueOf(Integer.parseInt(stdin.strip()) * 2) + "\n", "");
                return new RunResult(RunResult.Status.OK, "42\n", "");
            }
        };
        final TaskSpec spec = spec(TaskType.CODE, 10, Map.of("starterCode", "", "tests", List.of(
                Map.of("stdin", "21", "expectedOutput", "42", "hidden", false),
                Map.of("stdin", "5", "expectedOutput", "10"))));

        @Test
        void hiddenTestsCatchHardcodedOutput() {
            CodeHandler handler = new CodeHandler(fakeRunner);
            assertTrue(handler.evaluate(spec, Map.of("code", "verdoppeln"), CONTEXT).isCorrect());
            Evaluation hardcoded = handler.evaluate(spec, Map.of("code", "print(42)"), CONTEXT);
            assertEquals(Evaluation.Outcome.PARTIAL, hardcoded.outcome());
            assertFalse(hardcoded.details().toString().contains("10"), "Versteckter Test verrät seine Erwartung");
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, Map.of("code", "while True: pass"), CONTEXT).outcome());
            // Ausgabe melden wie früher bringt nichts
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of("output", "42"), CONTEXT).outcome());
        }

        @Test
        void withoutRunnerCodeIsUngradedPractice() {
            CodeHandler handler = new CodeHandler(new DisabledCodeRunner());
            assertEquals(Evaluation.Outcome.UNGRADED, handler.evaluate(spec, Map.of("code", "verdoppeln"), CONTEXT).outcome());
            Map<String, Object> view = handler.publicView(spec, CONTEXT);
            assertEquals(false, view.get("graded"));
            assertFalse(view.toString().contains("expectedOutput=10"), "Nur nicht versteckte Tests sind Beispiele");
        }

        @Test
        void fillCodeGradedByBlanksAndAlternativesByTests() {
            TaskSpec fill = spec(TaskType.FILL_CODE, 10, Map.of(
                    "template", "print(___)", "blanks", List.of(Map.of("answers", List.of("42"))),
                    "tests", List.of(Map.of("expectedOutput", "42"))));
            assertTrue(new FillCodeHandler(new DisabledCodeRunner()).evaluate(fill, Map.of("answers", List.of("42")), CONTEXT).isCorrect());
            assertFalse(new FillCodeHandler(new DisabledCodeRunner()).evaluate(fill, Map.of("answers", List.of("6*7")), CONTEXT).isCorrect());
            assertTrue(new FillCodeHandler(fakeRunner).evaluate(fill, Map.of("answers", List.of("6*7")), CONTEXT).isCorrect());
        }
    }

    @Test
    void attemptRulesOverrideDefaults() {
        AttemptRules rules = AttemptRules.from(TaskType.MULTIPLE_CHOICE, Map.of("maxAttempts", 5, "firstTryBonusPercent", 0));
        assertEquals(5, rules.maxAttempts());
        assertEquals(0, rules.firstTryBonusPercent());
        assertEquals(25, rules.hintPenaltyPercent());
        assertNull(AttemptRules.from(TaskType.MULTIPLE_CHOICE, Map.of("maxAttempts", 0)).maxAttempts());
    }
}

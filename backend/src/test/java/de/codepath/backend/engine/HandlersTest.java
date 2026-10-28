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
            "expectedDirectories", "expectedFiles", "expectedContents", "tests", "solution", "hint", "hints",
            "pairs", "targets", "indent", "gates");

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

        final Map<String, Object> logTask = Map.of(
                "initialFiles", Map.of("server.log", "INFO ok\nERROR {{dienst}} weg\nINFO ok\n", "notiz.txt", "Code: {{code}}"),
                "variants", List.of(Map.of("dienst", "Datenbank"), Map.of("dienst", "Drucker"), Map.of("dienst", "Mailserver")),
                "answer", "{{dienst}}",
                "answerLabel", "Welcher Dienst ist ausgefallen?",
                "solution", List.of("grep ERROR server.log"));

        @Test
        void answerIsCheckedAndDiffersPerPerson() {
            TaskSpec s = spec(TaskType.TERMINAL, 10, logTask);
            Map<String, Object> view = handler.publicView(s, CONTEXT);
            assertEquals("Welcher Dienst ist ausgefallen?", view.get("answerLabel"));
            assertFalse(view.toString().contains("answer="), "Antwort darf nicht an den Browser: " + view);
            @SuppressWarnings("unchecked")
            Map<String, String> files = (Map<String, String>) view.get("initialFiles");
            String service = files.get("server.log").split("\n")[1].substring("ERROR ".length()).replace(" weg", "");
            assertFalse(files.get("notiz.txt").contains("{{"), "Platzhalter ersetzt");

            assertTrue(handler.evaluate(s, Map.of("commands", List.of("grep ERROR server.log"), "answer", " " + service.toLowerCase() + "."), CONTEXT).isCorrect());
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(s, Map.of("commands", List.of("cat server.log"), "answer", "Toaster"), CONTEXT).outcome());
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(s, Map.of("commands", List.of("cat server.log")), CONTEXT).outcome());
            assertTrue(handler.evaluate(s, handler.sampleAnswer(s, CONTEXT), CONTEXT).isCorrect());
            assertFalse(handler.evaluate(s, handler.wrongAnswer(s, CONTEXT), CONTEXT).isCorrect());

            // Mit vielen Personen kommen verschiedene Varianten und Codes vor
            Set<Object> seen = new HashSet<>();
            for (long user = 1; user <= 20; user++) seen.add(handler.publicView(s, TaskContext.of(user, 42)).get("initialFiles"));
            assertTrue(seen.size() > 3, "Varianten je Person: " + seen.size());
        }

        @Test
        void gitChecksReplayTheRepository() {
            TaskSpec s = spec(TaskType.TERMINAL, 20, Map.of(
                    "initialDirectories", List.of("website"),
                    "initialFiles", Map.of("website/index.html", "<h1>Hallo</h1>", "website/passwort.txt", "geheim"),
                    "gitChecks", List.of(
                            Map.of("check", "repo", "path", "website"),
                            Map.of("check", "committed", "path", "index.html"),
                            Map.of("check", "neverCommitted", "path", "passwort.txt"),
                            Map.of("check", "commits", "min", 1)),
                    "solution", List.of("cd website", "git init", "git add index.html", "git commit -m \"Start\"")));
            assertTrue(handler.evaluate(s, handler.sampleAnswer(s, CONTEXT), CONTEXT).isCorrect());
            Evaluation leaked = handler.evaluate(s, Map.of("commands", List.of("cd website", "git init", "git add .", "git commit -m \"Alles\"")), CONTEXT);
            assertEquals(Evaluation.Outcome.WRONG, leaked.outcome());
            assertTrue(leaked.feedback().contains("3 von 4") && leaked.feedback().contains("passwort.txt"), leaked.feedback());
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(s, Map.of("commands", List.of("git init")), CONTEXT).outcome());
        }

        @Test
        void preparedRepositoryWithServer() {
            TaskSpec s = spec(TaskType.TERMINAL, 20, Map.of(
                    "git", Map.of("root", "app",
                            "history", List.of(Map.of("message", "Start", "author", "Mia", "files", Map.of("README.md", "App\n"))),
                            "remote", List.of(Map.of("message", "Übersetzung", "author", "Lena", "files", Map.of("README.md", "App v2\n")))),
                    "gitChecks", List.of(Map.of("check", "pushed", "branch", "main"), Map.of("check", "committed", "path", "notiz.txt"), Map.of("check", "clean")),
                    "solution", List.of("cd app", "echo hallo > notiz.txt", "git add .", "git commit -m Notiz", "git pull", "git push")));
            assertTrue(handler.evaluate(s, handler.sampleAnswer(s, CONTEXT), CONTEXT).isCorrect());
            Evaluation rejected = handler.evaluate(s, Map.of("commands", List.of("cd app", "echo hallo > notiz.txt", "git add .", "git commit -m Notiz", "git push")), CONTEXT);
            assertEquals(Evaluation.Outcome.WRONG, rejected.outcome());
            assertTrue(rejected.feedback().contains("origin"), rejected.feedback());
        }

        @Test
        void validationAsksForSolutionAndKnownChecks() {
            assertTrue(handler.validate(Map.of("answer", "x"), 10).stream().anyMatch(p -> p.contains("Musterlösung")));
            assertTrue(handler.validate(Map.of("gitChecks", List.of(Map.of("check", "zaubern")), "solution", List.of("git init")), 10)
                    .stream().anyMatch(p -> p.contains("zaubern")));
            assertTrue(handler.validate(logTask, 10).isEmpty());
            assertFalse(handler.validate(Map.of(), 10).isEmpty());
        }

        @Test
        void expensiveInputsStayCheap() {
            TerminalSimulator sim = new TerminalSimulator(List.of(), Map.of("a".repeat(200) + ".txt", ""));
            long start = System.nanoTime();
            assertEquals("", sim.execute("find . -name \"" + "*a".repeat(40) + "*b\""));
            assertEquals("./" + "a".repeat(200) + ".txt", sim.execute("find . -name \"*a*.txt\""));
            assertTrue(System.nanoTime() - start < 1_000_000_000L, "Muster ohne exponentielles Backtracking");

            TerminalSimulator git = new TerminalSimulator(List.of(), Map.of());
            git.execute("git init");
            for (int i = 0; i < 6; i++) git.execute("echo " + "x".repeat(400) + " >> a.txt");
            for (int f = 0; f < 30; f++) git.execute("cp a.txt f" + f + ".txt");
            git.execute("git add .");
            git.execute("git commit -m alles");
            String out = git.execute("git show");
            assertTrue(out.endsWith("… (Ausgabe gekürzt)"), out.substring(Math.max(0, out.length() - 80)));
            assertTrue(out.length() < 60_000);
        }

        @Test
        void absentPathsAndAllowedLeftovers() {
            TaskSpec s = spec(TaskType.TERMINAL, 10, Map.of(
                    "initialFiles", Map.of("downloads/foto.png", "", "downloads/brief.pdf", "", "downloads/virus.exe", ""),
                    "expectedFiles", List.of("bilder/foto.png", "dokumente/brief.pdf"),
                    "absent", List.of("downloads/virus.exe"),
                    "allowExtra", true,
                    "solution", List.of("mkdir bilder dokumente", "mv downloads/foto.png bilder", "mv downloads/brief.pdf dokumente", "rm downloads/virus.exe")));
            assertTrue(handler.evaluate(s, handler.sampleAnswer(s, CONTEXT), CONTEXT).isCorrect());
            Evaluation kept = handler.evaluate(s, Map.of("commands", List.of("mkdir bilder dokumente", "mv downloads/foto.png bilder", "mv downloads/brief.pdf dokumente")), CONTEXT);
            assertTrue(kept.feedback().contains("virus.exe"), kept.feedback());
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
                if (code.contains("verdoppeln") && code.endsWith("noch_einmal()\n")) return new RunResult(RunResult.Status.OK, "7\n", "");
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
        void appendedCallsRunAfterTheStudentCode() {
            TaskSpec withAppend = spec(TaskType.CODE, 10, Map.of("starterCode", "", "solution", "verdoppeln", "tests", List.of(
                    Map.of("append", "noch_einmal()", "expectedOutput", "7"))));
            assertTrue(new CodeHandler(fakeRunner).evaluate(withAppend, Map.of("code", "verdoppeln"), CONTEXT).isCorrect());
            assertTrue(new CodeHandler(fakeRunner).validate(withAppend.config(), 10).isEmpty());
            assertFalse(new CodeHandler(fakeRunner).validate(spec.config(), 10).isEmpty(), "Ohne Musterlösung kein Probelauf");
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

    @Nested
    class Matching {
        final MatchingHandler handler = new MatchingHandler();
        final TaskSpec spec = spec(TaskType.MATCHING, 10, handler.template());

        @Test
        void partialCreditPerPairAndNoSolutionInView() {
            assertTrue(handler.validate(spec.config(), 10).isEmpty());
            assertNoSolution(handler.publicView(spec, CONTEXT));
            assertTrue(handler.evaluate(spec, handler.sampleAnswer(spec, CONTEXT), CONTEXT).isCorrect());
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, handler.wrongAnswer(spec, CONTEXT), CONTEXT).outcome());
            Evaluation partial = handler.evaluate(spec, Map.of("pairs", Map.of("l1", "r1", "l2", "r3", "l3", "r2")), CONTEXT);
            assertEquals(Evaluation.Outcome.PARTIAL, partial.outcome());
            assertEquals(1 / 3.0, partial.score(), 1e-9);
            // Unvollständig oder kaputt zählt nicht als Versuch
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of("pairs", Map.of("l1", "r1")), CONTEXT).outcome());
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of("pairs", "l1=r1"), CONTEXT).outcome());
            // Alles auf ein Element zu setzen bringt höchstens einen Treffer
            Evaluation same = handler.evaluate(spec, Map.of("pairs", Map.of("l1", "r1", "l2", "r1", "l3", "r1")), CONTEXT);
            assertEquals(1 / 3.0, same.score(), 1e-9);
        }

        @Test
        void validation() {
            assertFalse(handler.validate(Map.of(
                    "left", List.of(Map.of("id", "a", "text", "A"), Map.of("id", "b", "text", "B")),
                    "right", List.of(Map.of("id", "x", "text", "X")),
                    "pairs", List.of(Map.of("left", "a", "right", "x"), Map.of("left", "b", "right", "x"))), 10).isEmpty());
        }
    }

    @Nested
    class Parsons {
        final ParsonsHandler handler = new ParsonsHandler();
        final TaskSpec spec = spec(TaskType.PARSONS, 10, handler.template());

        static List<Map<String, Object>> lines(Object... idIndent) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < idIndent.length; i += 2) result.add(Map.of("id", idIndent[i], "indent", idIndent[i + 1]));
            return result;
        }

        @Test
        void orderIndentAndDistractors() {
            assertTrue(handler.validate(spec.config(), 10).isEmpty());
            Map<String, Object> view = handler.publicView(spec, CONTEXT);
            assertNoSolution(view);
            assertEquals(4, ((List<?>) view.get("blocks")).size());
            assertTrue(handler.evaluate(spec, handler.sampleAnswer(spec, CONTEXT), CONTEXT).isCorrect());
            assertFalse(handler.evaluate(spec, handler.wrongAnswer(spec, CONTEXT), CONTEXT).isCorrect());

            Evaluation indent = handler.evaluate(spec, Map.of("lines", lines("z1", 0, "z2", 0, "z3", 0)), CONTEXT);
            assertEquals(0.75, indent.score(), 1e-9);
            assertTrue(indent.feedback().contains("Einrückung"));

            Evaluation distractor = handler.evaluate(spec, Map.of("lines", lines("d1", 0, "z2", 1, "z3", 0)), CONTEXT);
            assertFalse(distractor.isCorrect());
            assertTrue(distractor.feedback().contains("Nicht jede Zeile"));

            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of("lines", lines("z1", 0, "z1", 0)), CONTEXT).outcome());
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of("lines", lines("zz", 0)), CONTEXT).outcome());
        }

        @Test
        void identicalLinesAreInterchangeable() {
            TaskSpec twice = spec(TaskType.PARSONS, 10, Map.of("lines", List.of(
                    Map.of("id", "a", "code", "print('x')"), Map.of("id", "b", "code", "print('y')"), Map.of("id", "c", "code", "print('x')"))));
            assertTrue(handler.evaluate(twice, Map.of("lines", lines("c", 0, "b", 0, "a", 0)), CONTEXT).isCorrect());
        }
    }

    @Nested
    class NumberConvert {
        final NumberConvertHandler handler = new NumberConvertHandler();
        final TaskSpec spec = spec(TaskType.NUMBER_CONVERT, 10, Map.of("from", 10, "to", 2, "min", 1, "max", 255, "count", 3));

        @Test
        void valuesPerStudentAndLenientParsing() {
            assertTrue(handler.validate(spec.config(), 10).isEmpty());
            Map<String, Object> view = handler.publicView(spec, CONTEXT);
            assertNoSolution(view);
            assertEquals(view, handler.publicView(spec, CONTEXT));
            assertNotEquals(view, handler.publicView(spec, new TaskContext(99)), "Nachbarn bekommen andere Zahlen");

            List<String> values = Cfg.strings(view, "values");
            List<String> answers = values.stream().map(v -> "0b " + Integer.toBinaryString(Integer.parseInt(v))).toList();
            assertTrue(handler.evaluate(spec, Map.of("answers", answers), CONTEXT).isCorrect());
            assertTrue(handler.evaluate(spec, handler.sampleAnswer(spec, CONTEXT), CONTEXT).isCorrect());
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, handler.wrongAnswer(spec, CONTEXT), CONTEXT).outcome());
            // Die Dezimalzahl abschreiben ist keine Binärzahl
            assertFalse(handler.evaluate(spec, Map.of("answers", values), CONTEXT).isCorrect());
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of("answers", List.of("1")), CONTEXT).outcome());
        }

        @Test
        void fixedHexValues() {
            TaskSpec hex = spec(TaskType.NUMBER_CONVERT, 10, Map.of("from", 16, "to", 10, "values", List.of("FF", "1a")));
            assertTrue(handler.validate(hex.config(), 10).isEmpty());
            assertEquals(List.of("FF", "1A"), handler.publicView(hex, CONTEXT).get("values"));
            assertTrue(handler.evaluate(hex, Map.of("answers", List.of("255", " 26 ")), CONTEXT).isCorrect());
            assertFalse(handler.validate(Map.of("from", 10, "to", 10), 10).isEmpty());
            assertFalse(handler.validate(Map.of("from", 2, "to", 10, "values", List.of("102")), 10).isEmpty());
        }
    }

    @Nested
    class Hotspot {
        final HotspotHandler handler = new HotspotHandler();
        final TaskSpec spec = spec(TaskType.HOTSPOT, 10, handler.template());

        @Test
        void wrongMarksCostPoints() {
            assertTrue(handler.validate(spec.config(), 10).isEmpty());
            Map<String, Object> view = handler.publicView(spec, CONTEXT);
            assertNoSolution(view);
            assertEquals(4, view.get("count"));
            assertTrue(handler.evaluate(spec, handler.sampleAnswer(spec, CONTEXT), CONTEXT).isCorrect());
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, handler.wrongAnswer(spec, CONTEXT), CONTEXT).outcome());
            assertEquals(0.5, handler.evaluate(spec, Map.of("selected", List.of(1, 2)), CONTEXT).score(), 1e-9);
            // Alles markieren lohnt sich nicht
            assertFalse(handler.evaluate(spec, Map.of("selected", List.of(1, 2, 3, 4, 5, 6, 7, 8)), CONTEXT).isCorrect());
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of("selected", List.of()), CONTEXT).outcome());
        }

        @Test
        void validation() {
            assertFalse(handler.validate(Map.of("text", "a\nb", "targets", List.of(3)), 10).isEmpty());
            assertFalse(handler.validate(Map.of("text", "a\nb", "targets", List.of(1, 2)), 10).isEmpty());
        }
    }

    @Nested
    class ImageHotspot {
        final ImageHotspotHandler handler = new ImageHotspotHandler();
        final TaskSpec spec = spec(TaskType.IMAGE_HOTSPOT, 10, Map.of(
                "image", "a".repeat(64) + ".png", "alt", "Schreibtisch",
                "targets", List.of(Map.of("id", "a", "x", 10, "y", 10, "w", 20, "h", 20, "label", "Zettel"),
                        Map.of("id", "b", "x", 60.5, "y", 50, "w", 10, "h", 10))));

        static Map<String, Object> marks(double... xy) {
            List<Map<String, Object>> marks = new ArrayList<>();
            for (int i = 0; i < xy.length; i += 2) marks.add(Map.of("x", xy[i], "y", xy[i + 1]));
            return Map.of("marks", marks);
        }

        @Test
        void hitsMissesAndCarpetBombing() {
            assertTrue(handler.validate(spec.config(), 10).isEmpty());
            Map<String, Object> view = handler.publicView(spec, CONTEXT);
            assertNoSolution(view);
            assertFalse(view.toString().contains("Zettel"));
            assertEquals(2, view.get("count"));
            assertEquals(4, view.get("maxMarks"));
            assertTrue(handler.evaluate(spec, handler.sampleAnswer(spec, CONTEXT), CONTEXT).isCorrect());
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, handler.wrongAnswer(spec, CONTEXT), CONTEXT).outcome());
            assertEquals(0.5, handler.evaluate(spec, marks(15, 15), CONTEXT).score(), 1e-9);
            // Zweimal dasselbe Ziel zählt einmal, daneben kostet
            assertEquals(0.5, handler.evaluate(spec, marks(15, 15, 16, 16), CONTEXT).score(), 1e-9);
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, marks(15, 15, 90, 90), CONTEXT).outcome());
            // Mehr Markierungen als erlaubt oder kaputte Werte zählen nicht als Versuch
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, marks(1, 1, 2, 2, 3, 3, 4, 4, 5, 5), CONTEXT).outcome());
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, marks(150, 10), CONTEXT).outcome());
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of("marks", "alles"), CONTEXT).outcome());
        }

        @Test
        void validation() {
            assertFalse(handler.validate(handler.template(), 10).isEmpty(), "Ohne Bild ungültig");
            assertFalse(handler.validate(Map.of("image", "x.png", "alt", "a", "targets", List.of(Map.of("id", "a", "x", 1, "y", 1, "w", 5, "h", 5))), 10).isEmpty());
            assertFalse(handler.validate(Map.of("image", "a".repeat(64) + ".png", "alt", "a",
                    "targets", List.of(Map.of("id", "a", "x", 90, "y", 1, "w", 20, "h", 5))), 10).isEmpty(), "ragt aus dem Bild");
            assertFalse(handler.validate(Map.of("image", "a".repeat(64) + ".png", "alt", "a",
                    "targets", List.of(Map.of("id", "a", "x", 0, "y", 0, "w", 100, "h", 100))), 10).isEmpty(), "ganzes Bild");
        }
    }

    @Nested
    class LogicCircuitTask {
        final LogicCircuitHandler handler = new LogicCircuitHandler();
        final TaskSpec xor = spec(TaskType.LOGIC_CIRCUIT, 10, Map.of(
                "inputs", List.of("A", "B"), "expression", "A XOR B", "allowedGates", List.of("AND", "OR", "NOT"), "maxGates", 4,
                "solution", Map.of("gates", List.of(
                        gate("g1", "OR", "A", "B"), gate("g2", "AND", "A", "B"), gate("g3", "NOT", "g2"), gate("g4", "AND", "g1", "g3")),
                        "output", "g4")));

        static Map<String, Object> gate(String id, String type, String... inputs) {
            return Map.of("id", id, "type", type, "inputs", List.of(inputs));
        }

        @Test
        void gradesByTruthTable() {
            assertTrue(handler.validate(xor.config(), 10).isEmpty(), handler.validate(xor.config(), 10).toString());
            Map<String, Object> view = handler.publicView(xor, CONTEXT);
            assertNoSolution(view);
            assertEquals(List.of(false, true, true, false), view.get("expected"));
            assertTrue(handler.evaluate(xor, handler.sampleAnswer(xor, CONTEXT), CONTEXT).isCorrect());
            assertFalse(handler.evaluate(xor, handler.wrongAnswer(xor, CONTEXT), CONTEXT).isCorrect());
            // Eine andere richtige Schaltung zählt genauso
            Map<String, Object> other = Map.of("gates", List.of(gate("n", "NOT", "A"), gate("m", "NOT", "B"),
                    gate("x", "AND", "A", "m"), gate("y", "AND", "n", "B")), "output", "y");
            Evaluation partial = handler.evaluate(xor, other, CONTEXT);
            assertEquals(Evaluation.Outcome.PARTIAL, partial.outcome(), "3 von 4 Zeilen");
            assertEquals(0.5, partial.score(), 1e-9);
            // ODER statt XOR: 3 von 4; konstante 0: 2 von 4 → keine Punkte
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(xor, Map.of("gates", List.of(gate("z", "AND", "A", "B"), gate("y", "NOT", "z"), gate("w", "AND", "z", "y")), "output", "w"), CONTEXT).outcome());
        }

        @Test
        void rejectsForbiddenOrBrokenCircuits() {
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(xor, Map.of("gates", List.of(gate("g1", "XOR", "A", "B")), "output", "g1"), CONTEXT).outcome());
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(xor, Map.of("gates", List.of(gate("g1", "AND", "A", "g2"), gate("g2", "OR", "A", "g1")), "output", "g2"), CONTEXT).outcome(), "Schleife");
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(xor, Map.of("gates", List.of(gate("g1", "NOT", "A", "B")), "output", "g1"), CONTEXT).outcome());
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(xor, Map.of("gates", List.of(), "output", "C"), CONTEXT).outcome());
            List<Map<String, Object>> many = new ArrayList<>();
            for (int i = 0; i < 5; i++) many.add(gate("g" + i, "NOT", "A"));
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(xor, Map.of("gates", many, "output", "g4"), CONTEXT).outcome());
        }
    }

    @Nested
    class DevTools {
        final DevToolsHandler handler = new DevToolsHandler(
                new de.codepath.backend.engine.lab.LabScenarios(List.of(new de.codepath.backend.engine.lab.ShopLab())),
                () -> "geheim-fuer-tests".getBytes());
        final TaskSpec spec = spec(TaskType.DEVTOOLS, 30, Map.of("scenario", "shop"));

        @Test
        void personalCodesWithPartialCredit() {
            assertTrue(handler.validate(spec.config(), 30).isEmpty());
            assertFalse(handler.validate(Map.of("scenario", "unbekannt"), 30).isEmpty());
            Map<String, Object> view = handler.publicView(spec, CONTEXT);
            assertNoSolution(view);
            assertEquals("XXXX-XXXX-XXXX", view.get("format"));

            String code = (String) handler.sampleAnswer(spec, CONTEXT).get("code");
            assertTrue(code.matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}"), code);
            assertFalse(view.toString().contains(code.substring(0, 4)));
            assertTrue(handler.evaluate(spec, Map.of("code", code), CONTEXT).isCorrect());
            assertTrue(handler.evaluate(spec, Map.of("code", " " + code.replace("-", "").toLowerCase() + " "), CONTEXT).isCorrect());
            assertEquals(Evaluation.Outcome.WRONG, handler.evaluate(spec, handler.wrongAnswer(spec, CONTEXT), CONTEXT).outcome());
            Evaluation twoOfThree = handler.evaluate(spec, Map.of("code", code.substring(0, 10) + "ZZZZ"), CONTEXT);
            assertEquals(Evaluation.Outcome.PARTIAL, twoOfThree.outcome());
            assertEquals(Evaluation.Outcome.INVALID, handler.evaluate(spec, Map.of("code", "ABC"), CONTEXT).outcome());

            // Andere Person, anderer Code
            TaskContext other = new TaskContext(CONTEXT.seed() + 1);
            assertNotEquals(code, handler.sampleAnswer(spec, other).get("code"));
            assertFalse(handler.evaluate(spec, Map.of("code", code), other).isCorrect());
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

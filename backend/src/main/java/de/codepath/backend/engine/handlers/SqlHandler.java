package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.*;

/**
 * Config: { schemaSql, solutionSql, starterSql?, orderMatters? }
 * Antwort: { query }
 * Rückmeldung: die ersten Zeilen des eigenen Ergebnisses als Tabelle.
 */
@Component
public class SqlHandler implements TaskHandler {

    private static final int PREVIEW_ROWS = 20;

    private final SqlSandbox sandbox;

    public SqlHandler(SqlSandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Override
    public TaskType type() {
        return TaskType.SQL;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        Cfg.Problems problems = new Cfg.Problems().requireText(config, "schemaSql").requireText(config, "solutionSql");
        if (Cfg.string(config, "solutionSql") != null && Cfg.string(config, "schemaSql") != null) {
            try {
                String solution = sandbox.requireSingleSelect(Cfg.string(config, "solutionSql"));
                sandbox.compare(Cfg.string(config, "schemaSql"), solution, solution);
            } catch (SqlSandbox.SqlRejectedException | SQLException e) {
                problems.require(false, "Musterlösung oder Schema laufen nicht: " + e.getMessage());
            }
        }
        return problems.list();
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        return Map.of("starterSql", Cfg.string(spec.config(), "starterSql", ""), "tables", tableNames(spec));
    }

    /** Tabellennamen helfen beim Schreiben der Abfrage, verraten aber keine Lösung. */
    private static List<String> tableNames(TaskSpec spec) {
        String schema = Cfg.string(spec.config(), "schemaSql", "");
        List<String> names = new ArrayList<>();
        var matcher = java.util.regex.Pattern.compile("(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([A-Za-z_][A-Za-z0-9_]*)").matcher(schema);
        while (matcher.find()) names.add(matcher.group(1).toLowerCase(Locale.ROOT));
        return names;
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        String query;
        try {
            query = sandbox.requireSingleSelect(Cfg.string(answer, "query"));
        } catch (SqlSandbox.SqlRejectedException e) {
            return Evaluation.invalid(e.getMessage());
        }
        SqlSandbox.QueryResult[] results;
        try {
            results = sandbox.compare(Cfg.string(spec.config(), "schemaSql"), Cfg.string(spec.config(), "solutionSql"), query);
        } catch (SQLException e) {
            return Evaluation.wrong("Die Datenbank meldet einen Fehler: " + firstLine(e.getMessage()));
        }
        SqlSandbox.QueryResult expected = results[0];
        SqlSandbox.QueryResult actual = results[1];
        if (actual.rows().size() > SqlSandbox.MAX_ROWS) {
            return Evaluation.wrong("Das Ergebnis hat mehr als " + SqlSandbox.MAX_ROWS + " Zeilen.");
        }
        Map<String, Object> preview = Map.of(
                "columns", actual.columns(),
                "rows", actual.rows().subList(0, Math.min(PREVIEW_ROWS, actual.rows().size())),
                "rowCount", actual.rows().size());
        boolean same = Cfg.bool(spec.config(), "orderMatters")
                ? expected.rows().equals(actual.rows())
                : sorted(expected.rows()).equals(sorted(actual.rows()));
        if (same) {
            return new Evaluation(Evaluation.Outcome.CORRECT, 1, "Richtig! Deine Abfrage liefert genau das gesuchte Ergebnis.", Map.of("result", preview));
        }
        String hint = actual.rows().size() != expected.rows().size()
                ? "Dein Ergebnis hat " + actual.rows().size() + " Zeilen – das stimmt noch nicht."
                : "Die Anzahl der Zeilen passt, aber die Inhalte noch nicht.";
        return new Evaluation(Evaluation.Outcome.WRONG, 0, hint, Map.of("result", preview));
    }

    private static List<String> sorted(List<List<Object>> rows) {
        return rows.stream().map(Object::toString).sorted().toList();
    }

    private static String firstLine(String message) {
        if (message == null) return "unbekannt";
        String line = message.lines().findFirst().orElse(message);
        return line.length() > 200 ? line.substring(0, 200) : line;
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        return Map.of("query", Cfg.string(spec.config(), "solutionSql", ""));
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("query", Cfg.string(spec.config(), "solutionSql", ""));
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("query", "SELECT 1 AS falsch");
    }
}

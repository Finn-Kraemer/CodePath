package de.codepath.backend.engine.handlers;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Führt SQL-Aufgaben in einer frischen H2-Datenbank je Abgabe aus. Schema und Musterlösung laufen als
 * Owner, die Schülerabfrage als Nutzer mit reinem SELECT-Recht; admin-pflichtige Funktionen wie
 * FILE_READ, CSVREAD oder LINK_SCHEMA schlagen damit fehl. Der SQL-Parser lässt vorab nur genau
 * eine SELECT-Anweisung durch.
 */
@Component
public class SqlSandbox {

    static final int MAX_ROWS = 1000;
    static final int TIMEOUT_SECONDS = 2;
    static final int MAX_QUERY_LENGTH = 5000;

    // Zweite Verteidigungslinie zusätzlich zu den Rechten des Datenbanknutzers
    private static final Pattern FORBIDDEN_FUNCTIONS = Pattern.compile(
            "\\b(FILE_READ|FILE_WRITE|CSVREAD|CSVWRITE|LINK_SCHEMA|RUNSCRIPT|SCRIPT|SHUTDOWN|BACKUP|JAVA_OBJECT|SLEEP)\\b",
            Pattern.CASE_INSENSITIVE);

    public record QueryResult(List<String> columns, List<List<Object>> rows) {
    }

    public static class SqlRejectedException extends Exception {
        public SqlRejectedException(String message) {
            super(message);
        }
    }

    /** Prüft, dass genau eine lesende Abfrage vorliegt. */
    public String requireSingleSelect(String query) throws SqlRejectedException {
        if (query == null || query.isBlank()) throw new SqlRejectedException("Bitte schreibe eine Abfrage.");
        if (query.length() > MAX_QUERY_LENGTH) throw new SqlRejectedException("Die Abfrage ist zu lang.");
        String trimmed = query.strip();
        while (trimmed.endsWith(";")) trimmed = trimmed.substring(0, trimmed.length() - 1).strip();
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(trimmed);
            if (statements.size() != 1) throw new SqlRejectedException("Bitte genau eine Abfrage abschicken.");
            Statement statement = statements.getFirst();
            if (!(statement instanceof Select)) {
                throw new SqlRejectedException("Hier sind nur SELECT-Abfragen erlaubt.");
            }
        } catch (SqlRejectedException e) {
            throw e;
        } catch (Exception e) {
            throw new SqlRejectedException("Die Abfrage enthält einen Syntaxfehler.");
        }
        if (FORBIDDEN_FUNCTIONS.matcher(withoutStringLiterals(trimmed)).find()) {
            throw new SqlRejectedException("Diese Funktion ist in der Übungsdatenbank nicht erlaubt.");
        }
        return trimmed;
    }

    /**
     * Legt das Schema an, führt die Musterlösung als Owner und die Schülerabfrage als Lesenutzer aus.
     *
     * @return [Musterlösung, Schülerergebnis]
     */
    public QueryResult[] compare(String schemaSql, String solutionSql, String studentQuery) throws SQLException {
        String url = "jdbc:h2:mem:sql_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        String ownerPassword = UUID.randomUUID().toString();
        String studentPassword = UUID.randomUUID().toString();

        try (Connection owner = DriverManager.getConnection(url, "owner", ownerPassword)) {
            QueryResult expected;
            try (java.sql.Statement stmt = owner.createStatement()) {
                if (schemaSql != null && !schemaSql.isBlank()) {
                    stmt.execute(schemaSql);
                }
                expected = query(stmt, solutionSql);
                stmt.execute("CREATE USER student PASSWORD '" + studentPassword + "'");
                stmt.execute("GRANT SELECT ON SCHEMA PUBLIC TO student");
            }
            try (Connection student = DriverManager.getConnection(url, "student", studentPassword);
                 java.sql.Statement stmt = student.createStatement()) {
                student.setReadOnly(true);
                stmt.setQueryTimeout(TIMEOUT_SECONDS);
                stmt.setMaxRows(MAX_ROWS + 1);
                return new QueryResult[]{expected, query(stmt, studentQuery)};
            }
            // Die Datenbank verschwindet, sobald die letzte Verbindung geschlossen ist
        }
    }

    private static QueryResult query(java.sql.Statement stmt, String sql) throws SQLException {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) columns.add(meta.getColumnLabel(i).toLowerCase(Locale.ROOT));
            List<List<Object>> rows = new ArrayList<>();
            while (rs.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= columns.size(); i++) row.add(normalizeValue(rs.getObject(i)));
                rows.add(row);
            }
            return new QueryResult(columns, rows);
        }
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Number n && !(value instanceof Double || value instanceof Float)) {
            return new java.math.BigDecimal(n.toString()).stripTrailingZeros();
        }
        return value == null ? null : value instanceof Number ? value : value.toString();
    }

    static String withoutStringLiterals(String sql) {
        return sql.replaceAll("'([^']|'')*'", "''");
    }
}

package de.codepath.backend.engine;

/**
 * Führt Python-Code serverseitig in einer Sandbox aus (ADR-4). Ist kein Runner eingerichtet,
 * zählen Code-Aufgaben als Übung ohne Punkte.
 */
public interface CodeRunner {

    boolean available();

    RunResult run(String code, String stdin);

    /**
     * @param status OK, ERROR (Python-Fehler), TIMEOUT, UNAVAILABLE
     */
    record RunResult(Status status, String stdout, String stderr) {
        public enum Status { OK, ERROR, TIMEOUT, UNAVAILABLE }
    }
}

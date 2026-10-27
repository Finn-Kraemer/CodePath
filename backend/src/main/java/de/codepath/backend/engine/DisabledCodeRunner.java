package de.codepath.backend.engine;

/** Platzhalter, solange kein Runner eingerichtet ist: Code-Aufgaben sind dann Übungen ohne Punkte. */
public class DisabledCodeRunner implements CodeRunner {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public RunResult run(String code, String stdin) {
        return new RunResult(RunResult.Status.UNAVAILABLE, "", "");
    }
}

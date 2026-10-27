package de.codepath.backend.engine;

import java.util.List;

/** Die Konfiguration einer Aufgabe passt nicht zum Schema ihres Typs. */
public class InvalidTaskConfigException extends RuntimeException {

    private final List<String> problems;

    public InvalidTaskConfigException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}

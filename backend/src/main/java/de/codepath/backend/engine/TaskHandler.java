package de.codepath.backend.engine;

import java.util.List;
import java.util.Map;

/**
 * Vertrag eines Aufgabentyps. Ein Typ = ein Handler + eine Frontend-Komponente + Tests.
 * Der Browser sieht nur {@link #publicView}, nie eine Lösung, und meldet nie selbst ein Ergebnis.
 */
public interface TaskHandler {

    TaskType type();

    /** Prüft die Konfiguration (beim Speichern im Editor und beim Import). Leere Liste = in Ordnung. */
    List<String> validate(Map<String, Object> config, int points);

    /** Was der Browser zum Anzeigen bekommt – ohne Lösung. */
    Map<String, Object> publicView(TaskSpec spec, TaskContext context);

    /** Bewertet die Antwort serverseitig. Darf nie eine Ausnahme wegen kaputter Eingaben werfen. */
    Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context);

    /** Was nach dem Lösen oder Aufdecken gezeigt wird. */
    Map<String, Object> solutionView(TaskSpec spec, TaskContext context);

    /** Musterlösung für den Probelauf im Editor; muss als richtig bewertet werden. */
    Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context);

    /** Eine falsche Antwort für den Probelauf; muss als falsch bewertet werden. */
    Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context);

    /** false = Lehrkraft bewertet (PRACTICE). */
    default boolean autoGraded() {
        return true;
    }
}

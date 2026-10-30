package de.codepath.backend.classroom;

import de.codepath.backend.content.Module;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.identity.User;

/**
 * Schnittstelle zum Lernpfad-Paket: Entscheidet für eine Schülerin oder einen Schüler, welche Aufgaben
 * und Module durch einen verbindlichen Lernpfad blockiert, ausgeschlossen oder nach einer Station
 * freigeschaltet sind.
 */
public interface PathGate {

    enum State {
        /** Nicht durch den Lernpfad eingeschränkt */
        OPEN,
        /** Im Lernpfad sichtbar, aber Vorgänger oder Schwelle noch offen (Inhalt und Abgabe gesperrt) */
        BLOCKED,
        /** Außerhalb des verbindlichen Pfads gesperrt oder nicht gewählter Zweig (nicht sichtbar) */
        HIDDEN,
        /** Durch „Frei ab Station X“ außerhalb des Lernpfads freigeschaltet (auch ohne module_releases) */
        UNLOCKED_OUTSIDE
    }

    record Decision(State state, String reason) {
        public static final Decision OPEN = new Decision(State.OPEN, null);
        public static final Decision UNLOCKED = new Decision(State.UNLOCKED_OUTSIDE, null);
    }

    interface Evaluation {
        Decision task(PublishedTask task);

        Decision module(Module module);
    }

    Evaluation NO_OP = new Evaluation() {
        @Override
        public Decision task(PublishedTask task) {
            return Decision.OPEN;
        }

        @Override
        public Decision module(Module module) {
            return Decision.OPEN;
        }
    };

    Evaluation evaluate(User user, AccessPolicy.Releases baseReleases);
}

package de.codepath.backend.classroom;

/** Abweichung einer Aufgabe von ihrem Modul in einem Kurs. Keine Abweichung = folgt dem Modul. */
public enum TaskReleaseMode {
    /** sichtbar, auch wenn das Modul gesperrt ist */
    SHOWN,
    /** unsichtbar, auch wenn das Modul freigeschaltet ist */
    HIDDEN
}

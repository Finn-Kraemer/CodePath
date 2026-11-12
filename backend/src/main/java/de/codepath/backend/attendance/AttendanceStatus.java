package de.codepath.backend.attendance;

/** PRESENT und LATE zählen als teilgenommen, EXCUSED wird gesondert ausgewiesen. */
public enum AttendanceStatus {
    PRESENT, ABSENT, EXCUSED, LATE;

    public boolean attended() {
        return this == PRESENT || this == LATE;
    }
}

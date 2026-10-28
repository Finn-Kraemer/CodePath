-- Einzelne Aufgaben je Kurs freigeben oder sperren, unabhängig vom Modul.
-- Keine Zeile = die Aufgabe folgt ihrem Modul.
--   SHOWN  – sichtbar, auch wenn das Modul gesperrt ist
--   HIDDEN – unsichtbar, auch wenn das Modul freigeschaltet ist
CREATE TABLE task_releases (
    course_id BIGINT      NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    task_id   BIGINT      NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    mode      VARCHAR(10) NOT NULL CHECK (mode IN ('SHOWN', 'HIDDEN')),
    PRIMARY KEY (course_id, task_id)
);
CREATE INDEX ix_task_releases_course ON task_releases (course_id);

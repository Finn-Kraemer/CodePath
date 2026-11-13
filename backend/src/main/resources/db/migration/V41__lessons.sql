-- Stundenabläufe: vorbereitete Schritte (Freigaben, Quiz, Aufgabenblatt, Bewertungsrunde, Live-Mitteilung,
-- Notiz), die die Lehrkraft in der Stunde nacheinander auslöst. Ohne Kurs = Vorlage.
CREATE TABLE lessons (
    id                BIGSERIAL PRIMARY KEY,
    course_id         BIGINT       REFERENCES courses (id) ON DELETE CASCADE,
    title             VARCHAR(200) NOT NULL,
    lesson_date       DATE,
    steps             TEXT         NOT NULL DEFAULT '[]',
    position          INT          NOT NULL DEFAULT 0,
    status            VARCHAR(10)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'RUNNING', 'DONE')),
    -- Index des zuletzt ausgeführten Schritts, -1 = noch keiner
    current_step      INT          NOT NULL DEFAULT -1,
    step_started_at   TIMESTAMP,
    restore_releases  BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Freigaben des Kurses beim Start (zum Wiederherstellen am Ende)
    releases_snapshot TEXT,
    -- Ergebnis jedes ausgeführten Schritts
    run_log           TEXT         NOT NULL DEFAULT '[]',
    started_at        TIMESTAMP,
    finished_at       TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_lessons_course ON lessons (course_id, position);

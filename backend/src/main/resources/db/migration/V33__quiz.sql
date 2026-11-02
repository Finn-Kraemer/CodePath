-- Quiz-Runde am Beamer: Live-Runde aus Auswahl-Aufgaben, Antworten anonym ausgewertet.
CREATE TABLE quiz_sessions (
    id                  BIGSERIAL    PRIMARY KEY,
    course_id           BIGINT       REFERENCES courses (id) ON DELETE CASCADE,
    created_by          BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    title               VARCHAR(200) NOT NULL,
    -- Aufgaben-IDs, kommagetrennt, in Fragereihenfolge
    task_ids            TEXT         NOT NULL,
    -- LOBBY | QUESTION | REVEAL | FINISHED
    phase               VARCHAR(20)  NOT NULL,
    current_index       INT          NOT NULL DEFAULT -1,
    question_started_at TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at         TIMESTAMP
);

-- Höchstens eine laufende Runde
CREATE UNIQUE INDEX ux_quiz_sessions_active ON quiz_sessions ((true)) WHERE phase <> 'FINISHED';

CREATE TABLE quiz_answers (
    id             BIGSERIAL PRIMARY KEY,
    session_id     BIGINT    NOT NULL REFERENCES quiz_sessions (id) ON DELETE CASCADE,
    user_id        BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_index INT       NOT NULL,
    -- gewählte Options-IDs, kommagetrennt
    selected       TEXT      NOT NULL,
    correct        BOOLEAN   NOT NULL,
    answered_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_quiz_answers UNIQUE (session_id, user_id, question_index)
);

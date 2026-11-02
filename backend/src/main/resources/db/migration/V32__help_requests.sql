-- Stiller Hilfe-Ruf „Ich hänge fest“ (#38): nur Lehrkräfte sehen Rufe und Entwürfe.
CREATE TABLE help_requests (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    course_id       BIGINT       REFERENCES courses (id) ON DELETE CASCADE,
    task_id         BIGINT       NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    -- OPEN | CLAIMED | RESOLVED | CANCELLED
    status          VARCHAR(20)  NOT NULL,
    category        VARCHAR(30),
    note            VARCHAR(280),
    -- Stand der Antwort beim Rufen; wird nach dem Erledigen geleert
    draft           TEXT,
    last_output     TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_by      BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    claimed_at      TIMESTAMP,
    resolved_at     TIMESTAMP,
    -- TEACHER | SELF_SOLVED | CANCELLED
    resolution      VARCHAR(20),
    teacher_message VARCHAR(280)
);

-- Höchstens ein offener Ruf je Person
CREATE UNIQUE INDEX ux_help_requests_open ON help_requests (user_id) WHERE status IN ('OPEN', 'CLAIMED');
CREATE INDEX ix_help_requests_status ON help_requests (status, created_at);

-- Anwesenheit je Kurs und Termin. Termine werden beim Beenden eines Kurses gelöscht, Einträge zusätzlich
-- mit dem Konto.
CREATE TABLE attendance_sessions (
    id         BIGSERIAL PRIMARY KEY,
    course_id  BIGINT       NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    date       DATE         NOT NULL,
    title      VARCHAR(200),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_attendance_sessions_course ON attendance_sessions (course_id, date);

CREATE TABLE attendance_entries (
    session_id BIGINT      NOT NULL REFERENCES attendance_sessions (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status     VARCHAR(10) NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'EXCUSED', 'LATE')),
    note       VARCHAR(500),
    PRIMARY KEY (session_id, user_id)
);
CREATE INDEX ix_attendance_entries_user ON attendance_entries (user_id);

-- CodePath 2.0, Phase 2: Kurse, Modulfreigaben je Kurs, versionierte Aufgaben, Versuche,
-- Punkte-Journal, Rubrik-Bewertung und Audit-Log. Bestehende Nutzer, Fortschritte, Punkte und
-- Praxisabgaben werden übernommen; die Aufgabentexte legt der Start als Version 1 aus den
-- Content-Paketen an.

-- Kurse mit Beitrittscode ---------------------------------------------------------------------
CREATE TABLE courses (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    join_code  VARCHAR(16)  NOT NULL UNIQUE,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    ends_on    DATE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Jede Schülerin, jeder Schüler gehört zu genau einem Kurs
CREATE TABLE memberships (
    user_id   BIGINT    PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    course_id BIGINT    NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    joined_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_memberships_course ON memberships (course_id);

INSERT INTO courses (name, join_code)
SELECT 'Bestandskurs', upper(substr(md5(random()::text), 1, 6))
WHERE EXISTS (SELECT 1 FROM users WHERE role = 'STUDENT');

INSERT INTO memberships (user_id, course_id)
SELECT u.id, c.id FROM users u CROSS JOIN courses c WHERE u.role = 'STUDENT';

-- Modulfreigaben je Kurs statt global -----------------------------------------------------------
CREATE TABLE module_releases (
    course_id       BIGINT    NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    module_id       BIGINT    NOT NULL REFERENCES modules (id) ON DELETE CASCADE,
    unlocked        BOOLEAN   NOT NULL DEFAULT FALSE,
    available_until TIMESTAMP,
    PRIMARY KEY (course_id, module_id)
);

INSERT INTO module_releases (course_id, module_id, unlocked, available_until)
SELECT c.id, m.id, COALESCE(m.is_unlocked, FALSE), m.available_until FROM courses c CROSS JOIN modules m;

ALTER TABLE modules DROP COLUMN is_unlocked;
ALTER TABLE modules DROP COLUMN available_until;

-- Versionierte Aufgaben ------------------------------------------------------------------------
CREATE TABLE task_versions (
    id            BIGSERIAL PRIMARY KEY,
    task_id       BIGINT       NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    version       INT          NOT NULL,
    title         VARCHAR(200) NOT NULL,
    learning_goal TEXT,
    story         TEXT,
    assignment    TEXT,
    explanation   TEXT,
    hints         JSONB        NOT NULL DEFAULT '[]',
    points        INT          NOT NULL,
    difficulty    VARCHAR(20)  NOT NULL,
    config        JSONB        NOT NULL,
    rules         JSONB        NOT NULL DEFAULT '{}',
    -- NULL = Entwurf; veröffentlichte Versionen werden nicht mehr verändert
    published_at  TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by    BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    UNIQUE (task_id, version)
);

ALTER TABLE tasks ADD COLUMN current_version_id BIGINT REFERENCES task_versions (id) ON DELETE SET NULL;
ALTER TABLE tasks ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tasks DROP COLUMN title;
ALTER TABLE tasks DROP COLUMN description;
ALTER TABLE tasks DROP COLUMN story;
ALTER TABLE tasks DROP COLUMN difficulty;
ALTER TABLE tasks DROP COLUMN points;
ALTER TABLE tasks DROP COLUMN config;

-- Fortschritt je Schüler und Aufgabe -------------------------------------------------------------
CREATE TABLE progress (
    user_id            BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    task_id            BIGINT       NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    status             VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    started_at         TIMESTAMP,
    completed_at       TIMESTAMP,
    failed_attempts    INT          NOT NULL DEFAULT 0,
    hints_used         INT          NOT NULL DEFAULT 0,
    solution_revealed  BOOLEAN      NOT NULL DEFAULT FALSE,
    locked             BOOLEAN      NOT NULL DEFAULT FALSE,
    best_score         NUMERIC(5,4) NOT NULL DEFAULT 0,
    time_spent_seconds BIGINT       NOT NULL DEFAULT 0,
    review_comment     TEXT,
    PRIMARY KEY (user_id, task_id)
);

INSERT INTO progress (user_id, task_id, status, started_at, completed_at, failed_attempts, hints_used,
                      locked, best_score, time_spent_seconds)
SELECT user_id, task_id,
       CASE WHEN is_completed THEN 'SOLVED' ELSE 'OPEN' END,
       completed_at, completed_at, failed_attempts,
       CASE WHEN support_used THEN 1 ELSE 0 END,
       is_locked,
       CASE WHEN is_completed THEN 1 ELSE 0 END,
       time_spent_seconds
FROM user_task_completions;

-- Jeder Versuch wird gespeichert ------------------------------------------------------------------
CREATE TABLE attempts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    task_id         BIGINT       NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    task_version_id BIGINT       REFERENCES task_versions (id) ON DELETE SET NULL,
    answer          JSONB        NOT NULL,
    outcome         VARCHAR(20)  NOT NULL,
    score           NUMERIC(5,4) NOT NULL DEFAULT 0,
    feedback        TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    legacy_submission_id BIGINT
);
CREATE INDEX ix_attempts_user_task ON attempts (user_id, task_id, created_at DESC);

-- Punkte-Journal: der Punktestand ist die Summe; Korrekturen sind eigene Buchungen -----------
CREATE TABLE points_entries (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    task_id    BIGINT      REFERENCES tasks (id) ON DELETE CASCADE,
    amount     INT         NOT NULL,
    reason     VARCHAR(20) NOT NULL,
    note       TEXT,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by BIGINT      REFERENCES users (id) ON DELETE SET NULL
);
-- Für das Lösen einer Aufgabe gibt es genau einmal Punkte
CREATE UNIQUE INDEX ux_points_award ON points_entries (user_id, task_id) WHERE reason IN ('SOLVED', 'PARTIAL', 'REVIEW');
CREATE INDEX ix_points_user ON points_entries (user_id);

INSERT INTO points_entries (user_id, task_id, amount, reason, created_at)
SELECT user_id, task_id, points_awarded, 'SOLVED', COALESCE(completed_at, NOW())
FROM user_task_completions
WHERE is_completed AND points_awarded > 0;

-- Praxisabgaben werden Versuche mit Rubrik-Bewertung --------------------------------------------
CREATE TABLE practice_reviews (
    id             BIGSERIAL PRIMARY KEY,
    attempt_id     BIGINT      NOT NULL UNIQUE REFERENCES attempts (id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scores         JSONB       NOT NULL DEFAULT '{}',
    awarded_points INT,
    comment        TEXT,
    lock_task      BOOLEAN     NOT NULL DEFAULT FALSE,
    reviewer_id    BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at    TIMESTAMP
);
CREATE INDEX ix_practice_reviews_status ON practice_reviews (status);

INSERT INTO attempts (user_id, task_id, answer, outcome, score, created_at, legacy_submission_id)
SELECT user_id, task_id, jsonb_build_object('content', COALESCE(content, '')), 'MANUAL', 0,
       COALESCE(submitted_at, NOW()), id
FROM practice_submissions;

INSERT INTO practice_reviews (attempt_id, status, comment, reviewer_id, reviewed_at)
SELECT a.id, s.status, s.admin_comment, s.reviewer_id, s.reviewed_at
FROM attempts a JOIN practice_submissions s ON s.id = a.legacy_submission_id;

UPDATE progress p SET status = 'PENDING_REVIEW'
FROM practice_submissions s
WHERE s.user_id = p.user_id AND s.task_id = p.task_id AND s.status = 'PENDING' AND p.status <> 'SOLVED';

INSERT INTO progress (user_id, task_id, status)
SELECT DISTINCT s.user_id, s.task_id, 'PENDING_REVIEW'
FROM practice_submissions s
WHERE s.status = 'PENDING'
  AND NOT EXISTS (SELECT 1 FROM progress p WHERE p.user_id = s.user_id AND p.task_id = s.task_id);

ALTER TABLE attempts DROP COLUMN legacy_submission_id;

DROP TABLE practice_submissions;
DROP TABLE user_task_completions;
DROP TABLE task_comments;
ALTER TABLE users DROP COLUMN total_points;

-- Audit-Log: nur anhängen, nie ändern -----------------------------------------------------------
CREATE TABLE audit_events (
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    actor      VARCHAR(100),
    action     VARCHAR(60)  NOT NULL,
    target     VARCHAR(200),
    details    JSONB        NOT NULL DEFAULT '{}'
);
CREATE INDEX ix_audit_created ON audit_events (created_at DESC);

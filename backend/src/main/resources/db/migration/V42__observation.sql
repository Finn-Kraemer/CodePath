-- Klassenraum mit Sitzplan und Beobachtungsbogen (Paket observation). Ein Bogen je Kurs; ohne Kurs = Vorlage
-- (Raum + Kriterien, ohne Personen). Bewertungen und Notizen sind personenbezogen: Sie hängen am Kurs und
-- am Konto und werden mit ihnen gelöscht.
CREATE TABLE observation_boards (
    id         BIGSERIAL PRIMARY KEY,
    course_id  BIGINT UNIQUE REFERENCES courses (id) ON DELETE CASCADE,
    -- Name der Vorlage (nur ohne Kurs)
    name       VARCHAR(200),
    -- Tische, Lehrerpult, Tafel mit x/y, Größe, Drehung und Sitzplätzen
    layout     TEXT        NOT NULL DEFAULT '{"items":[]}',
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE observation_criteria (
    id        BIGSERIAL PRIMARY KEY,
    board_id  BIGINT        NOT NULL REFERENCES observation_boards (id) ON DELETE CASCADE,
    position  INT           NOT NULL DEFAULT 0,
    name      VARCHAR(100)  NOT NULL,
    kind      VARCHAR(10)   NOT NULL CHECK (kind IN ('NUMBER', 'CHOICE')),
    min_value NUMERIC(12, 2),
    max_value NUMERIC(12, 2),
    -- Auswahlliste: [{"value": "1", "description": "Neugier durch Fragen zum Beruf"}, …]
    options   TEXT          NOT NULL DEFAULT '[]'
);
CREATE INDEX ix_observation_criteria_board ON observation_criteria (board_id, position);

-- Eine Zeile je Person im Bogen: ein Schülerkonto des Kurses oder ein frei eingetragener Name.
CREATE TABLE observation_people (
    id         BIGSERIAL PRIMARY KEY,
    board_id   BIGINT       NOT NULL REFERENCES observation_boards (id) ON DELETE CASCADE,
    user_id    BIGINT       REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(100),
    seat_id    VARCHAR(40),
    note       TEXT,
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CHECK ((user_id IS NULL) <> (name IS NULL)),
    CONSTRAINT uq_observation_people_user UNIQUE (board_id, user_id),
    -- aufgeschoben, damit zwei Personen in einer Transaktion die Plätze tauschen können
    CONSTRAINT uq_observation_people_seat UNIQUE (board_id, seat_id) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX ix_observation_people_user ON observation_people (user_id);

CREATE TABLE observation_ratings (
    person_id    BIGINT      NOT NULL REFERENCES observation_people (id) ON DELETE CASCADE,
    criterion_id BIGINT      NOT NULL REFERENCES observation_criteria (id) ON DELETE CASCADE,
    value        VARCHAR(40) NOT NULL,
    updated_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (person_id, criterion_id)
);
CREATE INDEX ix_observation_ratings_criterion ON observation_ratings (criterion_id);

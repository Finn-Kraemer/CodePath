-- Freigaben für einzelne Schülerinnen und Schüler (Differenzierung). Sie gehen den Kursfreigaben vor:
-- Aufgabe der Person > Modul der Person > Aufgabe im Kurs > Modul im Kurs.
--   SHOWN  – für diese Person sichtbar, auch wenn der Kurs es nicht freigegeben hat
--   HIDDEN – für diese Person gesperrt, auch wenn der Kurs es freigegeben hat
-- Genau eins von module_id und task_id ist gesetzt. Mit dem Konto gelöscht (Kurs beenden).
CREATE TABLE user_releases (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    module_id       BIGINT      REFERENCES modules (id) ON DELETE CASCADE,
    task_id         BIGINT      REFERENCES tasks (id) ON DELETE CASCADE,
    mode            VARCHAR(10) NOT NULL CHECK (mode IN ('SHOWN', 'HIDDEN')),
    available_until TIMESTAMP,
    -- Zusatzaufgabe: Punkte zählen für die Person, aber nicht in der Rangliste
    bonus           BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    CHECK ((module_id IS NULL) <> (task_id IS NULL))
);
CREATE UNIQUE INDEX ux_user_releases_module ON user_releases (user_id, module_id) WHERE module_id IS NOT NULL;
CREATE UNIQUE INDEX ux_user_releases_task ON user_releases (user_id, task_id) WHERE task_id IS NOT NULL;
CREATE INDEX ix_user_releases_user ON user_releases (user_id);

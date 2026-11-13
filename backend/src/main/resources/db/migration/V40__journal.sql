-- Persönliches Lerntagebuch: private Notizen je Modul (optional je Aufgabe). Nur die Person selbst sieht
-- sie; geteilte Einträge sieht zusätzlich die Lehrkraft. Mit dem Konto gelöscht (Kurs beenden).
CREATE TABLE journal_entries (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    module_id  BIGINT    NOT NULL REFERENCES modules (id) ON DELETE CASCADE,
    task_id    BIGINT    REFERENCES tasks (id) ON DELETE CASCADE,
    text       TEXT      NOT NULL,
    shared     BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX ux_journal_module ON journal_entries (user_id, module_id) WHERE task_id IS NULL;
CREATE UNIQUE INDEX ux_journal_task ON journal_entries (user_id, task_id) WHERE task_id IS NOT NULL;

-- Leitfragen der Lehrkraft je Modul (eine Frage je Zeile)
CREATE TABLE journal_prompts (
    module_id BIGINT PRIMARY KEY REFERENCES modules (id) ON DELETE CASCADE,
    prompts   TEXT   NOT NULL
);

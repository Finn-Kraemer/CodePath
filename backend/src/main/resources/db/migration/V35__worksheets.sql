-- Aufgabenblätter: von der Lehrkraft aus Blöcken zusammengestellt und als PDF exportiert
-- (Schülerfassung und Lösung). Inhalt und Layout-Einstellungen liegen als JSON in content.
CREATE TABLE worksheets (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

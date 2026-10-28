-- Berufe-Kompass und Mitnehm-Paket: Berufsfeld und Links zum Weiterlernen je Modul
ALTER TABLE modules ADD COLUMN career VARCHAR(300);
ALTER TABLE modules ADD COLUMN learn_more JSONB NOT NULL DEFAULT '[]';

-- Lernpfade: von der Lehrkraft gezeichnete Karte aus Modulen, Aufgaben und Meilensteinen (Graph als JSON)
CREATE TABLE learning_paths (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    graph TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    position INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

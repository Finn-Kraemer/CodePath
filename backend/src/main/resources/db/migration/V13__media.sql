-- Medien (Bilder) für Aufgaben. Der Schlüssel ist der SHA-256 des Inhalts plus Endung: gleiche
-- Dateien werden nur einmal gespeichert, und ein Schlüssel zeigt immer auf denselben Inhalt.
-- Die Bilder liegen in der Datenbank, damit Backup, Export und Import nichts extra brauchen.
CREATE TABLE media (
    media_key     VARCHAR(80)  PRIMARY KEY,
    content_type  VARCHAR(40)  NOT NULL,
    original_name VARCHAR(200),
    size_bytes    INT          NOT NULL,
    width         INT,
    height        INT,
    data          BYTEA        NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by    BIGINT       REFERENCES users (id) ON DELETE SET NULL
);

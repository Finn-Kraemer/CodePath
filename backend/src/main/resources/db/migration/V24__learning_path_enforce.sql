-- Verbindliche Lernpfade (#21): Reihenfolge erzwingen und übrige Module optional ab einer Station freigeben
ALTER TABLE learning_paths ADD COLUMN enforce BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE learning_paths ADD COLUMN unlock_rest_after VARCHAR(40);

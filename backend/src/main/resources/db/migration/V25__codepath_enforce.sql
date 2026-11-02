-- Verbindliche CodePaths (#21): Reihenfolge erzwingen und übrige Module optional ab einer Station freigeben
ALTER TABLE codepaths ADD COLUMN enforce BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE codepaths ADD COLUMN unlock_rest_after VARCHAR(40);

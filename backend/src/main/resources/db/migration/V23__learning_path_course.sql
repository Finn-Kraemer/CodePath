-- Lernpfad für einen Kurs: Seine Module und Aufgaben werden in die Modul-Steuerung des Kurses übernommen.
-- NULL: Lernpfad für alle Kurse, ändert keine Freigaben.
ALTER TABLE learning_paths ADD COLUMN course_id BIGINT REFERENCES courses (id) ON DELETE SET NULL;
-- Module, die im Lernpfad nicht vorkommen, beim Übernehmen für den Kurs sperren
ALTER TABLE learning_paths ADD COLUMN lock_others BOOLEAN NOT NULL DEFAULT FALSE;

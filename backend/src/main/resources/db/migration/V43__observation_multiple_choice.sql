-- Beobachtungsbogen: Auswahllisten mit Mehrfachauswahl. Mehrere Werte stehen in observation_ratings.value
-- als Optionswerte in Reihenfolge der Optionen, getrennt durch ", " (z. B. "1, 2").
ALTER TABLE observation_criteria ADD COLUMN multiple BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE observation_ratings ALTER COLUMN value TYPE VARCHAR(2000);

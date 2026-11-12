-- Kurse archivieren (lesbar, aber ohne Anmeldung der Schüler, automatische Löschung nach einer Frist)
-- und ins nächste Schuljahr übernehmen (neuer Kurs mit denselben Mitgliedern).
ALTER TABLE courses ADD COLUMN archived_at TIMESTAMP;
ALTER TABLE courses ADD COLUMN purge_after TIMESTAMP;
-- Rangliste neu starten: nur Punkte ab diesem Zeitpunkt zählen (Punkte bleiben im Journal)
ALTER TABLE courses ADD COLUMN leaderboard_since TIMESTAMP;
-- Übernommener Kurs: wohin die Mitglieder gewechselt sind
ALTER TABLE courses ADD COLUMN successor_id BIGINT REFERENCES courses (id) ON DELETE SET NULL;

-- Die Startseite bearbeitet die Lehrkraft jetzt als Blöcke (homepage_draft / homepage_published,
-- angelegt beim ersten Speichern). Die alte, nie ausgewertete Konfiguration aus V9 entfällt.
DELETE FROM system_settings WHERE key = 'frontpage_config';

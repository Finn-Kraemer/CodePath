-- Passender Wissensartikel zu einer Aufgabe (Adresse des Artikels), versioniert wie die übrigen Texte
ALTER TABLE task_versions ADD COLUMN knowledge_article VARCHAR(80);

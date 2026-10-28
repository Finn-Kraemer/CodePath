-- Neues Modul "Die Kommandozeile" und ein Git-Modul, in dem im simulierten Terminal gearbeitet wird.
-- Die neuen Aufgaben legt der Start an (ContentSeeder); hier wird nur umgebaut, was schon da ist.

-- Reihenfolge: Kommandozeile (8) vor Git (9). Nur ändern, wo noch die ausgelieferte Reihenfolge gilt.
UPDATE modules SET sort_order = 11 WHERE slug = 'abschlusstest' AND sort_order = 10;
UPDATE modules SET sort_order = 10 WHERE slug = 'algorithmen' AND sort_order = 9;
UPDATE modules SET sort_order = 9 WHERE slug = 'git' AND sort_order = 8;

-- Die Terminal-Aufgabe aus dem Git-Modul zieht samt Versuchen und Punkten ins neue Modul um
INSERT INTO modules (slug, title, description, icon_emoji, sort_order)
SELECT 'terminal', 'Die Kommandozeile',
       'Dein erster Tag als Azubi im IT-Team der Stadtwerke. Der Server hat keinen Bildschirm und keine Maus – nur ein Terminal. Und heute Nacht ist etwas schiefgelaufen …',
       '🖥️', 8
WHERE EXISTS (SELECT 1 FROM tasks t JOIN modules m ON m.id = t.module_id
              WHERE m.slug = 'git' AND t.slug = 'terminal-git-init')
  AND NOT EXISTS (SELECT 1 FROM modules WHERE slug = 'terminal');

UPDATE tasks
SET module_id = (SELECT id FROM modules WHERE slug = 'terminal'), sort_order = 4
WHERE slug = 'terminal-git-init'
  AND module_id = (SELECT id FROM modules WHERE slug = 'git')
  AND EXISTS (SELECT 1 FROM modules WHERE slug = 'terminal');

-- Git-Aufgaben, die durch Aufgaben im Terminal ersetzt werden, archivieren: Versuche und Punkte bleiben
-- erhalten, die Klasse sieht sie nicht mehr. Die Lehrkraft kann sie im Editor zurückholen.
UPDATE tasks
SET archived = TRUE
WHERE module_id = (SELECT id FROM modules WHERE slug = 'git')
  AND slug IN ('g2', 'g4', 'g8', 'g9', 'g10', 'g11', 'g13', 'g14');

-- Behaltene Fragen an ihren Platz zwischen den neuen Aufgaben
UPDATE tasks
SET sort_order = CASE slug
                     WHEN 'g1' THEN 1 WHEN 'g3' THEN 2 WHEN 'g5' THEN 5
                     WHEN 'g6' THEN 6 WHEN 'g7' THEN 10 WHEN 'g12' THEN 15 END
WHERE module_id = (SELECT id FROM modules WHERE slug = 'git')
  AND slug IN ('g1', 'g3', 'g5', 'g6', 'g7', 'g12');

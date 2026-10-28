-- Endgültig gelöschte Aufgaben merken: Sonst legt der Start (oder „Mitgelieferte Inhalte übernehmen“)
-- eine gelöschte mitgelieferte Aufgabe einfach wieder an. Ein bewusster ZIP-Import holt sie zurück.
CREATE TABLE deleted_tasks (
    module_slug VARCHAR(100) NOT NULL,
    task_slug   VARCHAR(100) NOT NULL,
    deleted_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_by  BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    PRIMARY KEY (module_slug, task_slug)
);

-- Der Abschlusstest des Browser-Labors bekommt ein eigenes Modul. Bestehende Installationen
-- verschieben die Aufgabe samt Versuchen und Punkten, statt eine zweite anzulegen.
INSERT INTO modules (slug, title, description, icon_emoji, sort_order)
SELECT 'abschlusstest', 'Abschlusstest',
       'Zeig, was du gelernt hast: Prüfungsaufgaben für den Schluss des Kurses.', '🏁', 10
WHERE EXISTS (SELECT 1 FROM tasks t JOIN modules m ON m.id = t.module_id
              WHERE m.slug = 'web-entwicklung' AND t.slug = 'lab-escape')
  AND NOT EXISTS (SELECT 1 FROM modules WHERE slug = 'abschlusstest');

UPDATE tasks
SET module_id = (SELECT id FROM modules WHERE slug = 'abschlusstest'), sort_order = 1
WHERE slug = 'lab-escape'
  AND module_id = (SELECT id FROM modules WHERE slug = 'web-entwicklung')
  AND EXISTS (SELECT 1 FROM modules WHERE slug = 'abschlusstest');

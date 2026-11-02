-- Lernpfade heißen jetzt CodePaths – Tabelle samt Sequenz und Constraints umbenennen
ALTER TABLE learning_paths RENAME TO codepaths;
ALTER SEQUENCE learning_paths_id_seq RENAME TO codepaths_id_seq;
ALTER TABLE codepaths RENAME CONSTRAINT learning_paths_pkey TO codepaths_pkey;
ALTER TABLE codepaths RENAME CONSTRAINT learning_paths_slug_key TO codepaths_slug_key;
ALTER TABLE codepaths RENAME CONSTRAINT learning_paths_course_id_fkey TO codepaths_course_id_fkey;
-- Audit-Einträge mit den neuen Namen
UPDATE audit_events SET action = REPLACE(action, 'LEARNING_PATH_', 'CODEPATH_') WHERE action LIKE 'LEARNING\_PATH\_%';

-- Kurzbeschreibung einer eigenen Seite für die Seitenübersicht (/seiten)
ALTER TABLE pages ADD COLUMN description VARCHAR(300) NOT NULL DEFAULT '';

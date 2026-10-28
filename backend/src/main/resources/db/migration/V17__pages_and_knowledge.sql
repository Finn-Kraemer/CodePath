-- Eigene Seiten aus Blöcken (wie die Startseite) und die Wissensdatenbank, beides im Browser bearbeitet.

CREATE TABLE pages (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    -- PUBLIC: auch ohne Anmeldung, LOGGED_IN: nur angemeldet
    visibility VARCHAR(20) NOT NULL DEFAULT 'LOGGED_IN',
    show_in_nav BOOLEAN NOT NULL DEFAULT FALSE,
    position INT NOT NULL DEFAULT 0,
    draft TEXT NOT NULL,
    -- NULL: nicht veröffentlicht
    published TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE knowledge_articles (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    icon VARCHAR(16) NOT NULL DEFAULT '',
    body TEXT NOT NULL,
    -- Gekoppelt an ein Modul: für Schüler erst sichtbar, wenn das Modul freigegeben ist
    module_slug VARCHAR(100),
    published BOOLEAN NOT NULL DEFAULT TRUE,
    position INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Gelöschte mitgelieferte Artikel kommen beim nächsten Start nicht wieder
CREATE TABLE deleted_knowledge_articles (
    slug VARCHAR(80) PRIMARY KEY
);

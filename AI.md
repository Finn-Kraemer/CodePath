# CodePath – Technische Dokumentation (AI.md)

Stand: CodePath 2.0 (Phasen 0–5 der Überarbeitung). Was sich in welcher Phase geändert hat, steht in `docs/historie/`, die Entscheidungen in `docs/adr/`.

## Architektur

Modularer Monolith (ADR-1): Eine Spring-Boot-Anwendung liefert API, Live-Updates und das statisch gebaute Frontend aus; dazu PostgreSQL. Optional ein dritter Container, der Code-Runner (ADR-4).

- **Backend:** Java 25, Spring Boot 4.1 (Jackson 3, Security 7, Hibernate 7), Flyway, Spring Session JDBC, springdoc (OpenAPI unter `/api/admin/openapi`)
- **Frontend:** SvelteKit 2 / Svelte 5 mit adapter-static, Tailwind CSS 4, Monaco, Pyodide, TipTap (Praxisabgaben), marked + DOMPurify – alles lokal gebündelt (`scripts/copy-vendor.mjs`), API-Typen aus OpenAPI generiert (`pnpm gen:api`)
- **Code-Runner:** `runner/runner.py`, Python-Standardbibliothek + bubblewrap
- **Tests:** JUnit 5 mit Testcontainers (PostgreSQL 17), ArchUnit, Vitest, Playwright

### Pakete (`backend/src/main/java/de/codepath/backend`)

| Paket | Inhalt |
|---|---|
| `identity` | Nutzer, Kurse mit Beitrittscode, Mitgliedschaften, Login/Registrierung, Sessions |
| `content` | Module, Aufgaben, unveränderliche Aufgabenversionen, Content-Pakete, Editor |
| `engine` | Aufgabentypen: `TaskHandler` je Typ, `Evaluation`, `AttemptRules`, `CodeRunner` – kennt keine anderen Fachpakete |
| `progress` | Fortschritt, Versuche, Punkte-Journal, Schüler-API, Rangliste, Lehrkraft-Statistik |
| `review` | Praxisabgaben mit Rubrik-Bewertung |
| `classroom` | Modulfreigaben und Fristen je Kurs, Ankündigungen, Feedback |
| `report` | Teilnahmebestätigungen als PDF (OpenPDF) |
| `platform` | Fehlerbehandlung (ProblemDetail), Audit-Log, Einstellungen, Startseite, SSE, Auslieferung des Frontends |
| `site` | Eigene Seiten (`/seiten/<slug>`) und Wissensdatenbank |

`ArchitectureTest` erzwingt: Repositories nur im eigenen Paket, keine Zyklen, `engine` hängt von keinem Fachpaket ab.

## Sicherheit

- **Sessions statt JWT** (ADR-3): Cookie `CODEPATH_SESSION` (HttpOnly, SameSite=Lax, mit `COOKIE_SECURE=true` nur über HTTPS), Sessions in PostgreSQL. CSRF-Token im Cookie `XSRF-TOKEN`, das Frontend schickt es als `X-XSRF-TOKEN`.
- **Login-Bremse:** 10 Versuche pro Minute je Benutzername, 100 je IP (Schulnetz hinter NAT).
- **Keine Standardpasswörter:** Datenbank- und Admin-Passwort kommen aus `.env`; ohne Admin-Passwort wird eins erzeugt und ins Log geschrieben.
- **Lösungen bleiben auf dem Server:** Der Browser bekommt nur `TaskHandler.publicView` und meldet nie selbst ein Ergebnis. `SecurityCatalogTest` prüft jede mitgelieferte Aufgabe darauf.
- **SQL-Aufgaben:** eigene H2-Datenbank je Abgabe, JSqlParser lässt genau ein `SELECT` zu, Schülerabfrage läuft als Nutzer ohne Admin-Rechte, read-only, 2 s Timeout, höchstens 1.000 Zeilen.
- **Code-Aufgaben:** Pyodide im Browser nur zum Ausprobieren. Bewertet wird im Runner: bubblewrap ohne Netz und ohne Dateisystem außer `/usr` (lesend), 2 s CPU, 256 MB, 32 Prozesse, 5 s Wandzeit, 64 KB Ausgabe, eigene Benutzer-ID je parallelem Lauf. Versteckte Tests mit `append` entlarven fest einprogrammierte Ausgaben. Ohne Runner: Übung ohne Punkte.

## Aufgaben-Engine

Ein Aufgabentyp besteht aus `TaskHandler` (validieren, öffentliche Sicht, bewerten, Lösungssicht, Musterlösung und Falschantwort für den Probelauf, Vorlage für den Editor), einer Svelte-Komponente unter `frontend/src/lib/tasks/` und einem Editor-Formular unter `frontend/src/lib/editor/config/`.

Typen: `MULTIPLE_CHOICE`, `FILL_BLANK`, `FILL_CODE`, `CODE`, `SORTING`, `SQL`, `TERMINAL`, `PRACTICE` (Lehrkraft bewertet nach Rubrik), `MATCHING`, `PARSONS`, `NUMBER_CONVERT`, `HOTSPOT`, `IMAGE_HOTSPOT`, `LOGIC_CIRCUIT`, `DEVTOOLS` (Browser-Labor, `docs/browser-labor.md`). Formate: `content/README.md`.

Bewertung: CORRECT, PARTIAL (Teilpunkte), WRONG, MANUAL, UNGRADED, INVALID (zählt nicht als Versuch). `AttemptRules` je Typ, je Aufgabe überschreibbar: Versuchsgrenze, 25 % Abzug je Hinweis (höchstens 75 %), 20 % Bonus beim ersten Versuch ohne Hinweis, Lösung aufdecken = 0 Punkte. Zufällige Reihenfolgen und Zahlen hängen an einem Seed je Person und Aufgabe.

Punkte stehen in einem Journal (`points_entries`); ein eindeutiger Index sorgt dafür, dass eine Aufgabe genau einmal Punkte bringt, auch bei parallelen Abgaben. Korrekturen sind eigene Buchungen.

## Inhalte

Content-Pakete unter `content/<modul>/` (`module.json`, `tasks/NN-slug.json`, Markdown), geprüft per JSON-Schema (`content/validate.py`) und Probelauf (`ContentPackagesTest`). Beim Start werden fehlende Aufgaben als Version 1 angelegt. Im Editor (Admin → Inhalte) entstehen Entwürfe; Veröffentlichen friert eine Version ein, bisherige Versuche bleiben an ihrer Version (ADR-5). Export/Import als ZIP. Archivieren blendet eine Aufgabe aus und behält Ergebnisse; **endgültig löschen** geht nur für archivierte Aufgaben, nach Anzeige der Folgen und Bestätigung mit dem Slug, und entfernt alle Versionen, Versuche, Fortschritte und Punkte. Gelöschte Aufgaben stehen in `deleted_tasks`, damit mitgelieferte Inhalte sie nicht wieder anlegen; ein ZIP-Import holt sie bewusst zurück.

## Medien

Bilder liegen in PostgreSQL, Schlüssel = SHA-256 + Endung (ADR-7). Upload nur für die Lehrkraft, nur PNG/JPEG/GIF/WebP bis 2 MB, Auslieferung unter `/api/media/` mit restriktiver CSP. Markdown zeigt nur diese Bilder. Details: `docs/historie/medien-und-neue-typen.md`.

## Startseite

Blöcke als JSON in `system_settings` (`homepage_draft`, `homepage_published`), bearbeitet unter `/admin/homepage`. `HomepageService` prüft nur die Form (bekannte Blocktypen, höchstens 60 Blöcke, 200.000 Zeichen); Blocktypen, Felder und die Standardseite stehen in `frontend/src/lib/homepage/types.ts`, gerendert von `HomepageView.svelte` (Texte escaped, Markdown über DOMPurify). `GET /api/public/homepage` ist ohne Anmeldung erreichbar; `/api/media/<key>` liefert ohne Anmeldung nur Bilder veröffentlichter öffentlicher Seiten (Startseite, eigene Seiten).

## Eigene Seiten und Wissensdatenbank

Paket `site`. **Seiten** (`pages`): Blöcke wie die Startseite (geprüft von `BlockContent`), Entwurf + veröffentlichte Fassung, Sichtbarkeit `PUBLIC`/`LOGGED_IN`, alle veröffentlichten, sichtbaren Seiten über `GET /api/public/pages` für Aufklappmenü „Seiten“ und Übersicht `/seiten`, `showInNav` holt eine Seite zusätzlich direkt in die Menüleiste. Frontend: `/seiten`, `/seiten/[slug]`, Editor `PageEditor.svelte` (gemeinsam mit der Startseite). **Wissensdatenbank** (`knowledge_articles`): Markdown, optional an ein Modul gekoppelt (sichtbar, sobald das Modul für den Kurs sichtbar ist, über `AccessPolicy`), `published=false` nur für die Lehrkraft. Mitgelieferte Artikel unter `content/wissen/*.md` (Kopf: `title`, `icon`, optional `module`) legt `KnowledgeSeeder` an, wenn sie fehlen und nicht in `deleted_knowledge_articles` stehen; `seeded_hash` (MD5 des zuletzt mitgelieferten Textes) erlaubt, unveränderte Artikel beim Start auf eine neue Fassung zu bringen. Bildverwendung melden Startseite, Seiten und Artikel über `platform.media.MediaUsage` (öffentliche Auslieferung, Löschschutz).

## Live-Updates

Server-Sent Events unter `/api/events` (ADR-2): `announcement`, `leaderboard`, gesendet erst nach dem Commit. Die Beamer-Ansicht `/projector` nutzt dasselbe. Gesendet wird auf einem eigenen Thread (`codepath-live-updates`), damit ein langsamer Client keine Abgabe aufhält; Ranglisten-Updates, die sich in der Warteschlange stauen, werden zu einem zusammengefasst.

## Betrieb

`docker-compose.yml`: `postgres`, `app`, optional `runner` (Profil) und `pgadmin` (Profil `tools`). TLS mit Caddy: `deploy/`. Backup: `deploy/backup.sh`. CI (`.github/workflows/ci.yml`): Backend mit Testcontainers und echtem Runner, Frontend (Lint, Typen, Tests, Build), Content-Schema, Runner-Angriffe im gehärteten Container, E2E gegen den gebauten Stack.

# Entwicklerhandbuch

Wie CodePath aufgebaut ist, wie man lokal entwickelt und testet und wie neue Aufgabentypen entstehen. Die Gründe hinter den großen Entscheidungen stehen in den [ADRs](adr/).

## ◼ Architektur

CodePath ist ein **modularer Monolith** ([ADR-1](adr/0001-modularer-monolith-ohne-broker.md)): eine Spring-Boot-Anwendung liefert API, Live-Updates und das statisch gebaute Frontend aus, PostgreSQL hält allen Zustand. Der optionale Code-Runner ist ein eigener, kleiner Python-Dienst.

| Schicht | Technik |
|---|---|
| Backend | Java 25, Spring Boot 4.1 (Jackson 3, Security 7, Hibernate 7), Spring Session JDBC, Flyway, springdoc |
| Frontend | SvelteKit 2 mit Svelte 5 (adapter-static), Tailwind CSS 4, Monaco, Pyodide, TipTap, marked + DOMPurify |
| Datenbank | PostgreSQL 17; H2 nur als Sandbox für SQL-Aufgaben |
| Code-Runner | Python-Standardbibliothek + bubblewrap |
| Tests | JUnit 5, Testcontainers, ArchUnit, Vitest, Playwright |

### Pakete im Backend

`backend/src/main/java/de/codepath/backend/`

| Paket | Verantwortung |
|---|---|
| `identity` | Konten, Kurse mit Beitrittscode, Anmeldung, Sessions, Sicherheitskonfiguration |
| `content` | Module, Aufgaben, unveränderliche Versionen, Content-Pakete, Editor, Bilder |
| `engine` | Aufgabentypen (`TaskHandler`), Bewertung, Regeln, Code-Runner-Anbindung, Browser-Labor – **kennt kein anderes Fachpaket** |
| `progress` | Fortschritt, Versuche, Punkte-Journal, Schüler-API, Rangliste, Statistik, Labor-Auslieferung |
| `review` | Praxisabgaben mit Rubrik-Bewertung |
| `classroom` | Freigaben von Modulen und einzelnen Aufgaben, Fristen je Kurs (`AccessPolicy`), Mitteilungen, Feedback |
| `report` | Teilnahmebestätigungen als PDF |
| `platform` | Fehlerbehandlung, Audit-Log, Einstellungen, Server-Sent Events, Auslieferung des Frontends |

`ArchitectureTest` erzwingt die Grenzen: Repositories nur im eigenen Paket, keine Zyklen, `engine` unabhängig.

### Weg einer Abgabe

```
Browser ──POST /api/modules/{modul}/{aufgabe}/submit {antwort}──▶ StudentController
  SubmissionService
    ├─ Zugriff prüfen (Kurs, Freigabe, Frist, Sperre, Versuche)
    ├─ TaskHandler.evaluate(spec, antwort, kontext)      ← engine, rein fachlich
    ├─ Versuch speichern (an der Aufgabenversion)
    ├─ ScoreCalculator: Punkte mit Hinweisabzug und Bonus
    ├─ PointsService.awardOnce  ← eindeutiger Index: genau einmal Punkte
    └─ nach dem Commit: Live-Update „leaderboard“ (eigener Sende-Thread)
◀── Ergebnis, Rückmeldung, Details je Typ
```

### Datenmodell (Auszug)

| Tabelle | Inhalt |
|---|---|
| `modules`, `tasks` | Struktur; `tasks.current_version_id` zeigt auf die veröffentlichte Version |
| `task_versions` | unveränderliche Fassungen (Texte, Konfiguration, Regeln); Entwürfe haben kein `published_at` |
| `courses`, `memberships`, `module_releases` | Kurse, Zugehörigkeit, Freigaben und Fristen je Kurs |
| `task_releases` | Abweichungen einzelner Aufgaben je Kurs (`SHOWN`/`HIDDEN`); ohne Eintrag gilt das Modul |
| `progress`, `attempts` | Status je Person und Aufgabe, jeder Versuch mit Antwort und Version |
| `points_entries` | Punkte-Journal; der Punktestand ist die Summe |
| `practice_reviews` | Rubrik-Bewertung von Praxisabgaben |
| `media` | Bilder, Schlüssel = SHA-256 + Endung ([ADR-7](adr/0007-medien-in-der-datenbank.md)) |
| `deleted_tasks` | endgültig gelöschte Aufgaben, damit Importe sie nicht wieder anlegen |
| `audit_events` | Audit-Log |

Migrationen liegen in `backend/src/main/resources/db/migration`. **Bestehende Migrationen nie ändern** – immer eine neue Version anlegen.

## ◼ Lokale Umgebung

Voraussetzungen: Java 25, Node.js 22 mit pnpm, Docker, Python 3 (für Inhalte und Runner).

```bash
# Datenbank (PostgreSQL auf localhost:5432, Passwort „codepath“)
cd backend/dev-stack && ./start.sh

# Backend auf http://localhost:8080
cd backend && SPRING_DATASOURCE_PASSWORD=codepath ADMIN_SEED_PASSWORD=admin-lokal ./mvnw spring-boot:run

# Frontend mit Hot Reload auf http://localhost:5173 (leitet /api an das Backend weiter)
cd frontend && pnpm install && pnpm dev
```

Optional der Code-Runner direkt auf dem Rechner (Linux mit bubblewrap):

```bash
cd runner && RUNNER_TOKEN=lokal-lokal-lokal-1 RUNNER_PORT=8765 python3 -I runner.py
# Backend dann mit CODEPATH_RUNNER_URL=http://localhost:8765 CODEPATH_RUNNER_TOKEN=lokal-lokal-lokal-1
```

## ◼ Prüfen und testen

| Befehl | Prüft |
|---|---|
| `cd backend && ./mvnw verify` | alle Backend-Tests gegen echtes PostgreSQL (Testcontainers, braucht Docker); schreibt dabei `frontend/src/lib/api/openapi.json` neu |
| `cd frontend && pnpm lint` | Prettier und ESLint |
| `cd frontend && pnpm check` | TypeScript und Svelte |
| `cd frontend && pnpm test` | Vitest (Markdown, Terminal-Simulation, Logikschaltungen) |
| `cd frontend && pnpm gen:api` | erzeugt `schema.d.ts` aus der OpenAPI-Beschreibung |
| `cd frontend && pnpm test:e2e` | Playwright gegen eine laufende Instanz (`E2E_BASE_URL`, `E2E_ADMIN_PASSWORD`) |
| `python content/validate.py` | Content-Pakete gegen das JSON-Schema, Bilder, Lücken, Rubriken |
| `cd runner && python -m unittest test_runner` | Angriffe auf die Sandbox |

Wichtige Testklassen: `TaskReleaseTest` (Freigaben einzelner Aufgaben bis zur Bestätigung), `SecurityCatalogTest` (keine Lösung im Browser, Zugriffsregeln, Punkte genau einmal), `ContentPackagesTest` (Probelauf jeder mitgelieferten Aufgabe), `BrowserLabTest` (jedes Labor-Szenario durchgespielt), `LegacyMigrationTest` (Migrationen mit Altdaten).

Die CI (`.github/workflows/ci.yml`) führt all das auf jedem Pull Request aus, startet dafür den echten Code-Runner und testet zum Schluss das gebaute Docker-Image mit Playwright.

## ◼ Frontend

```
frontend/src/
├── routes/              Seiten (Module, Aufgabe, Rangliste, Profil, Beamer, admin/…)
├── lib/api/             generierte Typen aus der OpenAPI-Beschreibung, api()-Helfer
├── lib/tasks/           eine Komponente je Aufgabentyp, TaskRenderer wählt aus
├── lib/editor/config/   ein Formular je Aufgabentyp für den Editor
├── lib/logic/           Logikschaltungen: Auswertung, Schaltplan, Baukasten
└── lib/…                Anmeldung, Live-Updates (SSE), Markdown, Monaco, Pyodide
```

Gestaltung: Navy (`--color-institutional-navy`) und Gold als Akzent, eckige Kanten, Überschriften in fetten Großbuchstaben, Beschriftungen in gesperrten Kapitälchen. Neue Komponenten orientieren sich an den bestehenden.

## ◼ Neuen Aufgabentyp hinzufügen

Ein Typ besteht aus Handler, Komponente, Editor-Formular und Tests.

1. **Typ anlegen:** Eintrag in `engine/TaskType` und Standardregeln in `engine/AttemptRules`.
2. **Handler:** Klasse in `engine/handlers`, die `TaskHandler` implementiert und eine Spring-Komponente ist:
   - `validate` – Konfiguration prüfen, verständliche Meldungen
   - `publicView` – nur, was der Browser zum Anzeigen braucht, **nie die Lösung**
   - `evaluate` – Antwort bewerten; darf bei kaputten Eingaben nie werfen, sondern gibt `INVALID` zurück
   - `solutionView`, `sampleAnswer`, `wrongAnswer` – für Lösungsansicht und Probelauf
   - `template` – Vorlage für neue Aufgaben im Editor
3. **Komponente:** `frontend/src/lib/tasks/<Typ>.svelte` mit den `TaskProps`, eintragen in `TaskRenderer.svelte` und `labels.ts`.
4. **Editor:** Formular in `frontend/src/lib/editor/config/`, eintragen in `ConfigForm.svelte`.
5. **Schema:** Regel in `content/schema/task.schema.json`, Format in `content/README.md`.
6. **Tests:** richtig, falsch, teilweise und manipuliert in `HandlersTest`; neue Lösungsfelder in die Listen von `HandlersTest` und `SecurityCatalogTest`; eine Beispielaufgabe in `content/`.

Für Szenarien des Browser-Labors siehe [Browser-Labor](browser-labor.md#neues-szenario-hinzufügen).

## ◼ Inhalte im Repository

Die mitgelieferten Inhalte liegen als Content-Pakete in `content/` und kommen beim Build ins Image. Format und Werkzeuge: [`content/README.md`](../content/README.md). Hilfsskripte in `content/tools/`:

| Skript | Zweck |
|---|---|
| `add_media.py` | Bild unter seinem Schlüssel in `<modul>/media/` ablegen |
| `code_tasks.py` | erwartete Ausgaben der Code-Aufgaben aus den Musterlösungen erzeugen |
| `draw_clean_desk.py` | Beispielbild der Clean-Desk-Aufgabe zeichnen |

## ◼ Konventionen

- Sprache in Code-Kommentaren, Oberfläche, Commits und Doku: **Deutsch**. Schüler werden geduzt.
- Kommentare erklären das *Warum*, nicht das *Was*.
- Eine API-Änderung heißt: `./mvnw verify` (schreibt `openapi.json`) und `pnpm gen:api`, beides einchecken – die CI prüft, dass sie aktuell sind.
- Neue Entscheidungen von Gewicht bekommen ein ADR in `docs/adr/`.

## ◼ Release

Ein Tag `v*` (z. B. `v2.2.0`) startet `.github/workflows/docker-release.yml`: Die Images `ghcr.io/finn-kraemer/codepath` und `ghcr.io/finn-kraemer/codepath-runner` werden für `amd64` und `arm64` gebaut und mit dem Tag sowie `latest` veröffentlicht. Vorher das [Änderungsprotokoll](../CHANGELOG.md) ergänzen.

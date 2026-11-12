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
| `review` | Praxisabgaben mit Rubrik-Bewertung, Peer-Review (`/api/peer-reviews`) |
| `classroom` | Modulfreigaben und Fristen je Kurs, Ankündigungen, Feedback-Nachrichten |
| `evaluation` | Bewertungsbögen: Vorlagen, Runden je Kurs mit Selbsteinschätzung, Unterrichtsbewertung und Lehrerbewertung (`/api/evaluations`, `/api/admin/evaluations`) |
| `report` | Teilnahmebestätigungen als PDF (OpenPDF) |
| `platform` | Fehlerbehandlung (ProblemDetail), Audit-Log, Einstellungen, Startseite, SSE, Auslieferung des Frontends |
| `site` | Eigene Seiten (`/seiten/<slug>`) und Wissensdatenbank |
| `pathway` | CodePaths (Karte als Graph) und empfohlene nächste Aufgabe |
| `transfer` | Zentraler Export/Import; sammelt alle `platform.transfer.TransferSection`-Beans |
| `help` | Stiller Hilfe-Ruf „Ich hänge fest“: Warteschlange für Lehrkräfte (`/api/help`, `/api/admin/help`), Statistik je Aufgabe |
| `quiz` | Quiz-Runde am Beamer aus Auswahl-Aufgaben (`/api/quiz`, `/api/admin/quiz`), höchstens eine laufende Runde |

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

Code-Visualizer („Schritt für Schritt“): Worker-Nachricht `trace` in `static/pyodide-worker.js` (sys.settrace, nur Dateiname `<student>`), Auswertung in `lib/visualizer/trace.ts`, Anzeige `CodeVisualizer.svelte`; Konfiguration `visualizer`/`visualizerMaxSteps` reicht `CodeHandler.withVisualizer` durch. In Markdown wird ein Code-Block ```` ```schritte ```` nach dem Bereinigen in `Markdown.svelte` durch `VisualizerBlock.svelte` ersetzt.

Typen: `MULTIPLE_CHOICE`, `FILL_BLANK`, `FILL_CODE`, `CODE`, `SORTING`, `SQL`, `TERMINAL`, `PRACTICE` (Lehrkraft bewertet nach Rubrik), `MATCHING`, `PARSONS`, `NUMBER_CONVERT`, `HOTSPOT`, `IMAGE_HOTSPOT`, `LOGIC_CIRCUIT`, `DEVTOOLS` (Browser-Labor, `docs/browser-labor.md`), `PREDICT` (Vorhersage am Haltepunkt, Auflösung im Code-Visualizer). Formate: `content/README.md`.

Bewertung: CORRECT, PARTIAL (Teilpunkte), WRONG, MANUAL, UNGRADED, INVALID (zählt nicht als Versuch). `AttemptRules` je Typ, je Aufgabe überschreibbar: Versuchsgrenze, 25 % Abzug je Hinweis (höchstens 75 %), 20 % Bonus beim ersten Versuch ohne Hinweis, Lösung aufdecken = 0 Punkte. Zufällige Reihenfolgen und Zahlen hängen an einem Seed je Person und Aufgabe.

Punkte stehen in einem Journal (`points_entries`); ein eindeutiger Index sorgt dafür, dass eine Aufgabe genau einmal Punkte bringt, auch bei parallelen Abgaben. Korrekturen sind eigene Buchungen.

## Inhalte

Content-Pakete unter `content/<modul>/` (`module.json`, `tasks/NN-slug.json`, Markdown), geprüft per JSON-Schema (`content/validate.py`) und Probelauf (`ContentPackagesTest`). Beim Start werden fehlende Aufgaben als Version 1 angelegt. Im Editor (Admin → Inhalte) entstehen Entwürfe; Veröffentlichen friert eine Version ein, bisherige Versuche bleiben an ihrer Version (ADR-5). Export/Import als ZIP. Archivieren blendet eine Aufgabe aus und behält Ergebnisse; **endgültig löschen** geht nur für archivierte Aufgaben, nach Anzeige der Folgen und Bestätigung mit dem Slug, und entfernt alle Versionen, Versuche, Fortschritte und Punkte. Gelöschte Aufgaben stehen in `deleted_tasks`, damit mitgelieferte Inhalte sie nicht wieder anlegen; ein ZIP-Import holt sie bewusst zurück.

## Medien

Bilder liegen in PostgreSQL, Schlüssel = SHA-256 + Endung (ADR-7). Upload nur für die Lehrkraft, nur PNG/JPEG/GIF/WebP bis 2 MB, Auslieferung unter `/api/media/` mit restriktiver CSP. Markdown zeigt nur diese Bilder. Details: `docs/historie/medien-und-neue-typen.md`. Medienverwaltung unter `/admin/media`: `GET /api/admin/media/library` liefert jedes Bild mit allen Verwendungen (`MediaReference`: Aufgaben über `MediaRepository.taskReferences`, alle anderen über `MediaUsage.references`), `PATCH /api/admin/media/{key}` ändert nur den angezeigten Namen, `POST /api/admin/media/delete-unused` löscht alle nirgends verwendeten Bilder.

## Startseite

Blöcke als JSON in `system_settings` (`homepage_draft`, `homepage_published`), bearbeitet unter `/admin/homepage`. `HomepageService` prüft nur die Form (bekannte Blocktypen, höchstens 60 Blöcke, 200.000 Zeichen); Blocktypen, Felder und die Standardseite stehen in `frontend/src/lib/homepage/types.ts`, gerendert von `HomepageView.svelte` (Texte escaped, Markdown über DOMPurify). `GET /api/public/homepage` ist ohne Anmeldung erreichbar; `/api/media/<key>` liefert ohne Anmeldung nur Bilder veröffentlichter öffentlicher Seiten (Startseite, eigene Seiten).

## Eigene Seiten und Wissensdatenbank

Paket `site`. **Seiten** (`pages`): Blöcke wie die Startseite (geprüft von `BlockContent`), Entwurf + veröffentlichte Fassung, Sichtbarkeit `PUBLIC`/`LOGGED_IN`, alle veröffentlichten, sichtbaren Seiten über `GET /api/public/pages` für Aufklappmenü „Seiten“ und Übersicht `/seiten`, `showInNav` holt eine Seite zusätzlich direkt in die Menüleiste. Frontend: `/seiten`, `/seiten/[slug]`, Editor `PageEditor.svelte` (gemeinsam mit der Startseite). **Wissensdatenbank** (`knowledge_articles`): Markdown, optional an ein Modul gekoppelt (sichtbar, sobald das Modul für den Kurs sichtbar ist, über `AccessPolicy`), `published=false` nur für die Lehrkraft. Mitgelieferte Artikel unter `content/wissen/*.md` (Kopf: `title`, `icon`, optional `module`) legt `KnowledgeSeeder` an, wenn sie fehlen und nicht in `deleted_knowledge_articles` stehen; `seeded_hash` (MD5 des zuletzt mitgelieferten Textes) erlaubt, unveränderte Artikel beim Start auf eine neue Fassung zu bringen. Suche: `GET /api/search/knowledge?q=` (alle Wörter, nur sichtbare Artikel, Ausschnitt ohne Markdown). Aufgaben verweisen über `knowledgeArticle` (versioniert, im Content-Paket) auf einen Artikel; ohne Angabe verlinkt das Frontend den Artikel zum Modul. Bildverwendung melden Startseite, Seiten und Artikel über `platform.media.MediaUsage` (öffentliche Auslieferung, Löschschutz).

## Mitnehm-Paket und Berufe-Kompass

Paket `report`. `ReportService.scores` liefert je sichtbarem Modul die Punkte; `compass` rankt Module mit `career` nach Anteil erreichter Punkte (Top 3 > 0 %). `GET /api/reports/take-home` (`TakeHomeService`, `TakeHomeRenderer`): PDF im Speicher mit Ergebnissen je Aufgabe, Code aus der letzten Abgabe (CODE, SQL, TERMINAL, FILL_CODE, PRACTICE), Kompass und Links (`learnMore` der Module, nach Kompass-Rang, plus allgemeine Links). Kein Speichern, kein Audit-Eintrag, `Cache-Control: no-store`. Standardschriften: Zeichen außerhalb Windows-1252 werden zu `?`.

## CodePaths

CodePaths (früher Lernpfade; V24 hat die Tabelle umbenannt). Paket `pathway` (`CodePath`, `CodePathService`, `CodePathController`, `CodePathTransfer`), API unter `/api/codepaths` und `/api/admin/codepaths`, Export-Bereich `codepaths`, Tabelle `codepaths` (Graph als JSON: Knoten `MODULE`/`TASK`/`MILESTONE`/`REMEDIAL`/`PATH`/`BONUS` und Steuerknoten `CHOICE`/`AND`/`OR` mit x/y, optional `boss` und Punkte-Tor `requirement`; Kanten `from`→`to` mit `required`, Weg-`label`/`hint` und `condition`; kreisfrei, geprüft in `PathGraph`). Dazu `codepath_choices` (Weichen-Entscheidungen), `codepath_overrides` (Freigaben durch die Lehrkraft), `codepath_branch_results` (ausgewertete Bedingungen) und Bonusbuchungen in `points_entries` (`path_id`/`node_id`). `enforce` sperrt serverseitig über `classroom.PathGate`/`AccessPolicy`. Modul-Stationen können mit `tasks` nur einen Teil ihres Moduls nutzen. `course_id` (V23) bindet einen CodePath an einen Kurs: Schüler sehen nur CodePaths ihres Kurses und die ohne Kurs (Kurs-CodePaths zuerst). Ein veröffentlichter Kurs-CodePath schreibt beim Speichern über `ReleaseService.applyPlan` in die Modul-Steuerung (Modul frei, nicht genutzte Aufgaben `HIDDEN`, mit `lock_others` andere Module gesperrt). Zustand je Station für eine Person: DONE, AVAILABLE (alle Vorgänger erledigt), UPCOMING, CLOSED (Frist), LOCKED (nicht freigegeben); gesperrte Stationen lassen durch, wenn alles davor erledigt ist. `GET /api/learning/next`: erste AVAILABLE-Station des obersten veröffentlichten CodePaths in topologischer Reihenfolge, sonst nächste offene Aufgabe (begonnenes Modul zuerst); 204, wenn nichts offen ist. „Erledigt“ ist jeder Fortschritt außer OPEN. Frontend: `lib/pathway/` (Karte, Geometrie, `PathIcon` – Linien-Icons statt der Modul-Emojis), Editor `/admin/codepaths/[id]` (Bausteine per Drag & Drop; Stationen der Art `BLOCK` gibt es nur im Editor und müssen vor dem Speichern eingerichtet werden).

## Zentraler Export/Import

Jedes Fachpaket stellt `TransferSection`-Beans bereit (`settings`, `modules`, `homepage`, `pages`, `knowledge`, `codepaths`; `order` legt die Importreihenfolge fest). Exportdatei: ZIP mit `codepath-export.json` (`format`, `version`, `sections`) und `media/<schlüssel>` für alle darin gefundenen Bildschlüssel. `TransferService.importFile` erkennt ZIP mit Manifest, JSON-Manifest oder Content-Paket (module.json → bisheriger Content-Import). Jeder Eintrag läuft über `ItemRunner` in einer eigenen Transaktion und landet als `TransferItem` (CREATED/UPDATED/UNCHANGED/ERROR) im Bericht; Module/Aufgaben liefert `ContentImporter.ImportReport.items`. Abgleich über den Slug, gelöscht wird nichts.

## Sammel-Exporte (PDF)

`platform.pdf.PdfBundles` liefert mehrere `PdfFile` (Dateiname, Lesezeichen-Titel, Inhalt) einheitlich aus: `format=zip` (Standard) oder `format=pdf` (OpenPDF `PdfCopy`, Lesezeichen je Datei, mit `duplex=true` Leerseite nach ungerader Seitenzahl), `inline=true` öffnet im Browser statt herunterzuladen. Genutzt von `GET /api/admin/courses/{id}/certificates`, `GET /api/admin/courses/{id}/take-home` (Audit `TAKE_HOME_EXPORTED`) und `GET /api/admin/evaluations/rounds/{id}/export`; Frontend-Baustein `lib/components/PdfBundle.svelte` (ZIP, Eine PDF, Drucken, beidseitig). Neue Exporte „je Person“ liefern eine `List<PdfFile>` und rufen `PdfBundles.respond`.

## Bewertungsbögen

Paket `evaluation`. `evaluation_templates` (Kriterien als JSON, `kind` SELF/LESSON/TEACHER, Export-Bereich `evaluation-templates` über `slug`), `evaluation_rounds` (Kurs, Status DRAFT/OPEN/CLOSED, optionale Frist `closes_at`, kopierte Kriterien je Teil – `null` = Teil fehlt; `teacher_uses_self_criteria`, `lesson_anonymous`, `teacher_visibility` HIDDEN/VISIBLE/AFTER_SELF, `allow_edit`), `evaluation_responses` (eindeutig je Runde, Teil und `subject_id`; bei TEACHER ist `subject_id` die bewertete Person, `author_id` die Lehrkraft). Kriterientypen: HEADING, SCALE (min/max 0–10, höchstens 11 Stufen, `labels` je Stufe, `style` NUMBERS/STARS), CHOICE (`options`, `multiple`), YES_NO, TEXT (`maxLength` ≤ 5000); `Criteria.normalize` vergibt fehlende IDs, `Criteria.answers` prüft Antworten und verwirft unbekannte Kriterien. Gleiche Kriterien-IDs in Selbsteinschätzung und Lehrerbewertung werden im Frontend verglichen; `selfScore`/`teacherScore` sind das Mittel der Skalen in Prozent. Anonyme Unterrichtsbewertungen erscheinen ohne Namen und in hash-sortierter Reihenfolge (keine Zuordnung über die Abgabezeit). PDF (`EvaluationExportService`, `EvaluationPdf`, OpenPDF, entsteht beim Abruf, wird nicht gespeichert): `GET /api/admin/evaluations/rounds/{id}/students/{userId}/pdf`, `…/summary.pdf`, `…/export?format=zip|pdf` (Klassenauswertung + `Bewertung_<benutzer>.pdf` je Person, Audit-Eintrag; `export.zip` bleibt als Kurzform), `GET /api/evaluations/{id}/pdf` für den eigenen Bogen mit denselben Sichtbarkeitsregeln wie die Oberfläche; anonyme Unterrichtsbewertungen stehen nur ohne Namen in der Klassenauswertung. Frontend: `lib/evaluation/` (`CriteriaEditor`, `EvaluationForm`, `ResultBars`), Schüler `/feedback`, `/feedback/[id]`, Lehrkraft `/admin/feedback` (Runden), `/admin/feedback/rounds/[id]` (Einstellungen, Auswertung, Schüler bewerten), `/admin/feedback/templates`, `/admin/feedback/messages` (freie Nachrichten, weiterhin `general_feedback`).

## Live-Updates

Server-Sent Events unter `/api/events` (ADR-2): `announcement`, `leaderboard`, `codepath`, `help`, `quiz` (Phase an alle, Antwortzahl nur an Lehrkräfte), gesendet erst nach dem Commit. Jede Verbindung kennt ihr Konto und ob es eine Lehrkraft ist: `help` geht nur an Lehrkräfte (`publishToTeachers`) und betroffene Schüler (`publishToUsers`), nie an alle. Die Beamer-Ansicht `/projector` nutzt dasselbe. Gesendet wird auf einem eigenen Thread (`codepath-live-updates`), damit ein langsamer Client keine Abgabe aufhält; Ranglisten-Updates, die sich in der Warteschlange stauen, werden zu einem zusammengefasst.

## Betrieb

`docker-compose.yml`: `postgres`, `app`, optional `runner` (Profil) und `pgadmin` (Profil `tools`). TLS mit Caddy: `deploy/`. Backup: `deploy/backup.sh`. CI (`.github/workflows/ci.yml`): Backend mit Testcontainers und echtem Runner, Frontend (Lint, Typen, Tests, Build), Content-Schema, Runner-Angriffe im gehärteten Container, E2E gegen den gebauten Stack.

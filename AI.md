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
| `identity` | Nutzer, Kurse mit Beitrittscode, Mitgliedschaften, Login/Registrierung, Sessions, Kurse archivieren/übernehmen |
| `content` | Module, Aufgaben, unveränderliche Aufgabenversionen, Content-Pakete, Editor |
| `engine` | Aufgabentypen: `TaskHandler` je Typ, `Evaluation`, `AttemptRules`, `CodeRunner` – kennt keine anderen Fachpakete |
| `progress` | Fortschritt, Versuche, Punkte-Journal, Schüler-API, Rangliste, Lehrkraft-Statistik |
| `review` | Praxisabgaben mit Rubrik-Bewertung, Peer-Review (`/api/peer-reviews`) |
| `classroom` | Modulfreigaben und Fristen je Kurs, Freigaben pro Person (`user_releases`), Ankündigungen, Feedback-Nachrichten |
| `evaluation` | Bewertungsbögen: Vorlagen, Runden je Kurs mit Selbsteinschätzung, Unterrichtsbewertung und Lehrerbewertung (`/api/evaluations`, `/api/admin/evaluations`) |
| `report` | Teilnahmebestätigungen als PDF (OpenPDF) |
| `platform` | Fehlerbehandlung (ProblemDetail), Audit-Log, Einstellungen, Startseite, SSE, Auslieferung des Frontends |
| `site` | Eigene Seiten (`/seiten/<slug>`) und Wissensdatenbank |
| `pathway` | CodePaths (Karte als Graph) und empfohlene nächste Aufgabe |
| `transfer` | Zentraler Export/Import; sammelt alle `platform.transfer.TransferSection`-Beans |
| `help` | Stiller Hilfe-Ruf „Ich hänge fest“: Warteschlange für Lehrkräfte (`/api/help`, `/api/admin/help`), Statistik je Aufgabe |
| `quiz` | Quiz-Runde am Beamer aus Auswahl-Aufgaben (`/api/quiz`, `/api/admin/quiz`), höchstens eine laufende Runde |
| `attendance` | Anwesenheit je Kurs und Termin (`/api/admin/attendance`), Quote für Teilnahmebestätigung und Profil |
| `journal` | Lerntagebuch: private Notizen je Modul/Aufgabe (`/api/journal`), Leitfragen, geteilte Einträge für die Lehrkraft |
| `lesson` | Stundenabläufe: Schritte planen, starten, „Weiter“ (`/api/admin/lessons`), Vorlagen im Export |
| `worksheet` | Aufgabenblätter der Lehrkraft als PDF (OpenPDF): Blöcke + Layout als JSON, Schülerfassung und Lösung (`/api/admin/worksheets`) |

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

Paket `report`. `ReportService.scores` liefert je sichtbarem Modul die Punkte; `compass` rankt Module mit `career` nach Anteil erreichter Punkte (Top 3 > 0 %). `GET /api/reports/take-home` (`TakeHomeService`, `TakeHomeRenderer`): PDF im Speicher mit Ergebnissen je Aufgabe, Code aus der letzten Abgabe (CODE, SQL, TERMINAL, FILL_CODE, PRACTICE), Kompass und Links (`learnMore` der Module, nach Kompass-Rang, plus allgemeine Links). Kein Speichern, kein Audit-Eintrag, `Cache-Control: no-store`. Schriften: `platform.pdf.PdfFonts` (siehe unten).

## Aufgabenblätter

Paket `worksheet`. Tabelle `worksheets` (V35: `title`, `content` als JSON-Text). Inhalt `{settings, blocks}`; Blocktypen und Felder in `frontend/src/lib/worksheet/types.ts`, geprüft von `WorksheetContent` (bekannte Typen, höchstens 300 Blöcke, 500.000 Zeichen, Farbe `#rrggbb`, Bildschlüssel), gezeichnet von `WorksheetRenderer` (fehlende Werte → Standardwerte, Schriften aus `PdfFonts`, Serif = DejaVu Serif). Aufgaben (`task`, `choice`, `gap`, `truefalse`, `matching`, `order`, `table` mit `prompt`) werden nummeriert und summieren Punkte. `show: student|solution` blendet Blöcke je Fassung aus; gemischte Reihenfolgen hängen am Block-`id` (ersatzweise an der Position im ganzen Blatt) und sind damit in beiden Fassungen gleich. `pagebreak`/`divider`/`space` am Ende und direkt nach einem Umbruch werden übersprungen (keine leeren Seiten). API: CRUD unter `/api/admin/worksheets`, `POST …/{id}/copy`, `GET …/{id}/pdf?variant=student|solution&download=`, `POST …/preview?variant=` (ungespeicherter Stand für die Live-Vorschau). PDFs werden nicht gespeichert. Bilder meldet `WorksheetMediaUsage` (eigene Bean, sonst Zyklus mit `MediaService`), Export-Bereich `worksheets` (`WorksheetTransfer`, Abgleich über den Titel). Blatt je Person: `WorksheetRenderer.Personalization` (Name, Klasse, Datum, Kennzeichen, Seed); der Seed (`personSeed(userId, worksheetId)`, SplitMix64) mischt die Aufgaben je Abschnitt (Folgen von Aufgabenblöcken), Optionen, Aussagen, Zuordnungen und Reihenfolgen, ohne Personalisierung bleibt alles wie im Editor. `GET …/{id}/students?courseId=&solution=&format=` (Audit `WORKSHEET_EXPORTED`) und `GET …/{id}/variants?count=2..6` liefern über `PdfBundles`. Kopffelder wachsen mit vorbelegten Werten. Frontend: `/admin/worksheets`, Editor `/admin/worksheets/[id]` mit `WorksheetBlockForm.svelte`.

## CodePaths

CodePaths (früher Lernpfade; V24 hat die Tabelle umbenannt). Paket `pathway` (`CodePath`, `CodePathService`, `CodePathController`, `CodePathTransfer`), API unter `/api/codepaths` und `/api/admin/codepaths`, Export-Bereich `codepaths`, Tabelle `codepaths` (Graph als JSON: Knoten `MODULE`/`TASK`/`MILESTONE`/`REMEDIAL`/`PATH`/`BONUS` und Steuerknoten `CHOICE`/`AND`/`OR` mit x/y, optional `boss` und Punkte-Tor `requirement`; Kanten `from`→`to` mit `required`, Weg-`label`/`hint` und `condition`; kreisfrei, geprüft in `PathGraph`). Dazu `codepath_choices` (Weichen-Entscheidungen), `codepath_overrides` (Freigaben durch die Lehrkraft), `codepath_branch_results` (ausgewertete Bedingungen) und Bonusbuchungen in `points_entries` (`path_id`/`node_id`). `enforce` sperrt serverseitig über `classroom.PathGate`/`AccessPolicy`. Modul-Stationen können mit `tasks` nur einen Teil ihres Moduls nutzen. `course_id` (V23) bindet einen CodePath an einen Kurs: Schüler sehen nur CodePaths ihres Kurses und die ohne Kurs (Kurs-CodePaths zuerst). Ein veröffentlichter Kurs-CodePath schreibt beim Speichern über `ReleaseService.applyPlan` in die Modul-Steuerung (Modul frei, nicht genutzte Aufgaben `HIDDEN`, mit `lock_others` andere Module gesperrt). Zustand je Station für eine Person: DONE, AVAILABLE (alle Vorgänger erledigt), UPCOMING, CLOSED (Frist), LOCKED (nicht freigegeben); gesperrte Stationen lassen durch, wenn alles davor erledigt ist. `GET /api/learning/next`: erste AVAILABLE-Station des obersten veröffentlichten CodePaths in topologischer Reihenfolge, sonst nächste offene Aufgabe (begonnenes Modul zuerst); 204, wenn nichts offen ist. „Erledigt“ ist jeder Fortschritt außer OPEN. Frontend: `lib/pathway/` (Karte, Geometrie, `PathIcon` – Linien-Icons statt der Modul-Emojis), Editor `/admin/codepaths/[id]` (Bausteine per Drag & Drop; Stationen der Art `BLOCK` gibt es nur im Editor und müssen vor dem Speichern eingerichtet werden).

## Zentraler Export/Import

Jedes Fachpaket stellt `TransferSection`-Beans bereit (`settings`, `modules`, `homepage`, `pages`, `knowledge`, `codepaths`, `worksheets`, `evaluation-templates`, `lessons`; `order` legt die Importreihenfolge fest). Exportdatei: ZIP mit `codepath-export.json` (`format`, `version`, `sections`) und `media/<schlüssel>` für alle darin gefundenen Bildschlüssel. `TransferService.importFile` erkennt ZIP mit Manifest, JSON-Manifest oder Content-Paket (module.json → bisheriger Content-Import). Jeder Eintrag läuft über `ItemRunner` in einer eigenen Transaktion und landet als `TransferItem` (CREATED/UPDATED/UNCHANGED/ERROR) im Bericht; Module/Aufgaben liefert `ContentImporter.ImportReport.items`. Abgleich über den Slug, gelöscht wird nichts.

## Sammel-Exporte (PDF)

`platform.pdf.PdfBundles` liefert mehrere `PdfFile` (Dateiname, Lesezeichen-Titel, Inhalt) einheitlich aus: `format=zip` (Standard) oder `format=pdf` (OpenPDF `PdfCopy`, Lesezeichen je Datei, mit `duplex=true` Leerseite nach ungerader Seitenzahl), `inline=true` öffnet im Browser statt herunterzuladen. Genutzt von `GET /api/admin/courses/{id}/certificates`, `GET /api/admin/courses/{id}/take-home` (Audit `TAKE_HOME_EXPORTED`) und `GET /api/admin/evaluations/rounds/{id}/export`; Frontend-Baustein `lib/components/PdfBundle.svelte` (ZIP, Eine PDF, Drucken, beidseitig). Neue Exporte „je Person“ liefern eine `List<PdfFile>` und rufen `PdfBundles.respond`.

## PDF-Schriften

`platform.pdf.PdfFonts` ist die einzige Schriftkonfiguration aller PDFs: DejaVu Sans/Mono/Serif als Hauptschriften (`IDENTITY_H`, eingebettet, nur benutzte Zeichen), Noto Sans JP und Noto Emoji (schwarzweiß) als Ausweichschriften. `PdfFonts.phrase`/`paragraph` setzen Text per `FontSelector` aus Haupt- und Ausweichschrift zusammen; `PdfFonts.clean` entfernt nur noch Steuerzeichen. `ArchitectureTest.allPdfsUseTheSharedFonts` verbietet `FontFactory` außerhalb von `platform.pdf`. Schriften und Lizenzen: `backend/src/main/resources/fonts/`, `docs/lizenzen.md`.

## Bewertungsbögen

Paket `evaluation`. `evaluation_templates` (Kriterien als JSON, `kind` SELF/LESSON/TEACHER, Export-Bereich `evaluation-templates` über `slug`), `evaluation_rounds` (Kurs, Status DRAFT/OPEN/CLOSED, optionale Frist `closes_at`, kopierte Kriterien je Teil – `null` = Teil fehlt; `teacher_uses_self_criteria`, `lesson_anonymous`, `teacher_visibility` HIDDEN/VISIBLE/AFTER_SELF, `allow_edit`), `evaluation_responses` (eindeutig je Runde, Teil und `subject_id`; bei TEACHER ist `subject_id` die bewertete Person, `author_id` die Lehrkraft). Kriterientypen: HEADING, SCALE (min/max 0–10, höchstens 11 Stufen, `labels` je Stufe, `style` NUMBERS/STARS), CHOICE (`options`, `multiple`), YES_NO, TEXT (`maxLength` ≤ 5000); `Criteria.normalize` vergibt fehlende IDs, `Criteria.answers` prüft Antworten und verwirft unbekannte Kriterien. Gleiche Kriterien-IDs in Selbsteinschätzung und Lehrerbewertung werden im Frontend verglichen; `selfScore`/`teacherScore` sind das Mittel der Skalen in Prozent. Anonyme Unterrichtsbewertungen erscheinen ohne Namen und in hash-sortierter Reihenfolge (keine Zuordnung über die Abgabezeit). PDF (`EvaluationExportService`, `EvaluationPdf`, OpenPDF, entsteht beim Abruf, wird nicht gespeichert): `GET /api/admin/evaluations/rounds/{id}/students/{userId}/pdf`, `…/summary.pdf`, `…/export?format=zip|pdf` (Klassenauswertung + `Bewertung_<benutzer>.pdf` je Person, Audit-Eintrag; `export.zip` bleibt als Kurzform), `GET /api/evaluations/{id}/pdf` für den eigenen Bogen mit denselben Sichtbarkeitsregeln wie die Oberfläche; anonyme Unterrichtsbewertungen stehen nur ohne Namen in der Klassenauswertung. Frontend: `lib/evaluation/` (`CriteriaEditor`, `EvaluationForm`, `ResultBars`), Schüler `/feedback`, `/feedback/[id]`, Lehrkraft `/admin/feedback` (Runden), `/admin/feedback/rounds/[id]` (Einstellungen, Auswertung, Schüler bewerten), `/admin/feedback/templates`, `/admin/feedback/messages` (freie Nachrichten, weiterhin `general_feedback`).

## Freigaben pro Person

`user_releases` (V38: `user_id`, genau eins von `module_id`/`task_id`, `mode` SHOWN/HIDDEN, `available_until`, `bonus`; mit dem Konto gelöscht). `AccessPolicy.Releases` wertet in dieser Rangfolge aus: Aufgabe der Person → Modul der Person → Aufgabe im Kurs → Modul im Kurs; eine Freigabe der Person gilt auch vor dem `PathGate`. `courseReleases(user)` enthält die Freigaben der Person, damit auch der CodePath-Status (AVAILABLE/LOCKED) sie berücksichtigt; `forCourse(courseId)` (Modul-Steuerung, Lehrkraftsicht) nicht. `UserReleaseService` (`POST /api/admin/user-releases` für mehrere Personen, `GET/DELETE /api/admin/students/{u}/releases`, `GET /api/admin/modules/{id}/user-releases?courseId=`), Audit `USER_RELEASE_SET`. Bonus: `LeaderboardService` zieht Punkte aus Aufgaben ab, die die Person nur als Bonus hat (`UserReleaseService.bonusTasks`); `TaskSummary.personal`/`bonus` für die Kennzeichnung. Frontend: `lib/components/UserReleases.svelte` (Schülerakte und Modul-Steuerung).

## Stundenabläufe

Paket `lesson` (V41): `lessons` mit Kurs (null = Vorlage), Titel, Datum, `steps` (JSON, `LessonStep`: RELEASE/QUIZ/WORKSHEET/EVALUATION/ANNOUNCEMENT/NOTE, `minutes`), `status` DRAFT/RUNNING/DONE, `current_step`, `step_started_at`, `restore_releases` mit `releases_snapshot` (`ReleaseService.snapshot`/`restore`) und `run_log` (`StepResult` je Schritt). `LessonService.start/next` sperren die Zeile (`findByIdForUpdate`), prüfen `expectedStep` (409 bei Doppelklick) und führen den Schritt danach in einer eigenen Transaktion aus (`TransactionTemplate`, REQUIRES_NEW) – ein Fehler (ApiException) landet als `ok=false` mit Meldung im Protokoll und bricht nicht ab. Schritte nutzen `ReleaseService.setUnlocked/setTaskMode/setDeadline`, `QuizService.start`, `EvaluationService.setStatus`, `ClassroomService.updateAnnouncement`. Audit: `LESSON_STARTED`, `LESSON_STEP_EXECUTED` (mit `ok`), `LESSON_FINISHED`. Export-Bereich `lessons` (`LessonTransfer`, als Vorlagen über den Titel). Frontend: `/admin/lessons`, Editor `/admin/lessons/[id]`, „Stunde läuft“ `/admin/lessons/[id]/run`, `lib/lesson/types.ts`.

## Lerntagebuch

Paket `journal` (V40): `journal_entries` (Person, Modul, optional Aufgabe, Markdown-Text, `shared`; eindeutig je Person+Modul bzw. Person+Aufgabe) und `journal_prompts` (Leitfragen je Modul). Lesen und Schreiben nur für die eigene Person und nur in sichtbaren Modulen/Aufgaben (`AccessPolicy`), höchstens 10.000 Zeichen je Modul, leerer Text löscht (204), kein Audit-Eintrag. Die Lehrkraft sieht nur `shared` Einträge (`GET /api/admin/journal/shared?courseId|username`). `TakeHomeService.collect(user, withJournal)`: der eigene Download enthält „Mein Lerntagebuch“ (Markdown vereinfacht in `TakeHomeRenderer.markdown`), der Sammel-Export der Lehrkraft nicht. Frontend: `lib/journal/` (`JournalPanel` mit Autospeichern und lokalem Entwurf, `JournalOverview` im Profil, `JournalPrompts` in der Modul-Steuerung, `SharedJournal` in der Schülerakte).

## Anwesenheit

Paket `attendance`. `attendance_sessions` (Kurs, Datum, Titel) und `attendance_entries` (Termin, Person, Status PRESENT/ABSENT/EXCUSED/LATE, Notiz) aus V39. Ein neuer Termin legt für alle Mitglieder PRESENT an. `activeThatDay` (Abgaben an dem Tag, per `JdbcTemplate` auf `attempts`) ist nur ein Vorschlag. Quote `AttendanceRate`: PRESENT und LATE zählen als teilgenommen. Schalter in `system_settings`: `attendance_on_certificate` (Zeile auf der Teilnahmebestätigung über `AttendanceService.certificateLine`) und `attendance_visible_to_students` (`GET /api/attendance/me` ohne Notizen, sonst 403). Exporte: `GET /api/admin/attendance/export.csv` (Semikolon, BOM, Schutz vor Formeln) und `GET /api/admin/attendance/export?format=zip|pdf` (`AttendancePdf` je Person über `PdfBundles`). Termine löscht der Listener auf `CourseEndingEvent`, Einträge zusätzlich die Konto-Löschung. Frontend: `/admin/attendance`, `lib/attendance.ts`, `AttendanceSummary.svelte` (Schülerakte, Profil).

## Kurse: beenden, archivieren, übernehmen

`CourseStatus` ACTIVE/ARCHIVED/ENDED (V37: `archived_at`, `purge_after`, `leaderboard_since`, `successor_id`). `CourseService.endCourse` veröffentlicht `CourseEndingEvent` und löscht die Schülerkonten. `archive(id, months)` (1–36, Standard 6): Status ARCHIVED, Sitzungen der Mitglieder enden (`SessionAdminService`), `AuthService.login` lehnt Mitglieder archivierter Kurse mit 403 ab, `activeCourseByJoinCode` lässt nur aktive Kurse zu. `Course.isReadable()` (nicht ENDED) nutzt `AccessPolicy.courseReleases`, damit Akten und Exporte der Lehrkraft für archivierte Kurse vollständig bleiben. `CourseArchiveJob` (stündlich) ruft `purgeExpired` – Löschung wie beim Beenden, Audit `COURSE_ARCHIVE_PURGED`; `GET /api/admin/courses/purging-soon` liefert Kurse mit Löschung in 14 Tagen (Hinweis im Leitstand). `promote(id, PromoteOptions)` legt einen neuen Kurs an, hängt `memberships` um, setzt den alten auf ENDED mit `successor_id` und veröffentlicht `CoursePromotedEvent`: `ReleaseService` kopiert mit `keepReleases` die Freigaben (ohne Fristen), `CodePathService` kopiert mit `copyCodePaths` die Kurs-CodePaths (Slug `…-k<kurs>`), ohne `keepProgress` löschen `CoursePromotionListener` (progress) Punkte, Versuche und Fortschritt und `CodePathService` Weichen/Overrides. „Rangliste neu starten“ setzt `leaderboard_since`; `LeaderboardService` zählt dann nur Buchungen ab diesem Zeitpunkt (`PointsService.totalsSince`). API: `POST /api/admin/courses/{id}/archive|restore|promote`. Audit: `COURSE_ARCHIVED`, `COURSE_RESTORED`, `COURSE_PROMOTED`.

## Live-Updates

Server-Sent Events unter `/api/events` (ADR-2): `announcement`, `leaderboard`, `codepath`, `help`, `quiz` (Phase an alle, Antwortzahl nur an Lehrkräfte), gesendet erst nach dem Commit. Jede Verbindung kennt ihr Konto und ob es eine Lehrkraft ist: `help` geht nur an Lehrkräfte (`publishToTeachers`) und betroffene Schüler (`publishToUsers`), nie an alle. Die Beamer-Ansicht `/projector` nutzt dasselbe. Gesendet wird auf einem eigenen Thread (`codepath-live-updates`), damit ein langsamer Client keine Abgabe aufhält; Ranglisten-Updates, die sich in der Warteschlange stauen, werden zu einem zusammengefasst.

## Betrieb

`docker-compose.yml`: `postgres`, `app`, optional `runner` (Profil) und `pgadmin` (Profil `tools`). TLS mit Caddy: `deploy/`. Backup: `deploy/backup.sh`. CI (`.github/workflows/ci.yml`): Backend mit Testcontainers und echtem Runner, Frontend (Lint, Typen, Tests, Build), Content-Schema, Runner-Angriffe im gehärteten Container, E2E gegen den gebauten Stack.

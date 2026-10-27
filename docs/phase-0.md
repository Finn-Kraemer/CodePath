# Phase 0 – Sicherheitslücken im bestehenden System schließen

Teil des Überarbeitungsplans „CodePath 2.0“. Phase 0 ist ein Patch auf dem heutigen System und läuft vor dem eigentlichen Umbau.

## Umgesetzt

| Befund | Maßnahme | Ort |
|---|---|---|
| Lösungen werden mit jeder Aufgabe an den Browser geschickt | Whitelist je Aufgabentyp; Lösungsfelder und Hinweise verlassen den Server nicht mehr. Sortier-Elemente werden gemischt mit IDs ausgeliefert. | `TaskConfigFilter`, `TaskService` |
| SQL-Sandbox läuft als H2-Admin (`FILE_READ`, `LINK_SCHEMA` …) | Schema und Musterlösung laufen als Owner, die Schülerabfrage als Nutzer mit reinem `SELECT`-Recht. Höchstens 1.000 Zeilen, 2 s Timeout, nur eine Anweisung. Schlüsselwortfilter mit Wortgrenzen (blockiert z. B. `created_at` nicht mehr). | `TaskSubmissionService.validateSql` |
| RabbitMQ-Admin-Zugang im Frontend-Code, Management-Port offen | Browser nutzt den Nur-Lese-Nutzer `codepath-viewer` (darf abonnieren, nicht veröffentlichen). Management-UI, Backend und pgAdmin nur noch auf `127.0.0.1`; AMQP nur im Docker-Netz. | `docker-compose.yml`, `mqtt.svelte.ts` |
| Standard-Zugangsdaten fest hinterlegt | `JWT_SECRET` ist Pflicht (≥ 32 Zeichen, alte Standardwerte werden abgelehnt). Admin-Passwort aus `ADMIN_SEED_PASSWORD` oder beim ersten Start zufällig erzeugt. Alle Secrets kommen aus `.env`; ohne sie startet `docker compose` nicht. pgAdmin nur noch per Profil `tools`. | `JwtService`, `DataInitializer`, `.env.example` |
| Abgaben an gesperrte Module werden angenommen | Abgabe und Hilfestellung prüfen die Modulfreigabe (403). | `TaskSubmissionService` |
| „Hilfe genutzt“ kommt vom Client | Neuer Endpunkt `POST /api/modules/{modul}/{aufgabe}/support` merkt sich die Hilfestellung serverseitig und liefert erst dann den Hinweis. Der Client-Wert wird ignoriert. Falsche Antworten enthalten den Hinweis nur noch nach angeforderter Hilfe. | `TaskSubmissionService.requestSupport` |
| Bearbeitungszeit kommt vom Client und steuert Bonuspunkte | Zeitbonus entfällt; Bonus (+5) nur noch für Lösen im ersten Versuch ohne Hilfe. Die Zeit wird nur noch für die Statistik gespeichert (begrenzt auf 0–3600 s pro Abgabe). | `calculatePoints` |
| Nach drei Fehlversuchen wird die Lösung gezeigt, danach gibt es trotzdem Punkte | Bei Multiple Choice, Lückentext und Sortieren: nach dem Aufdecken 0 Punkte. Bei Code, SQL und Terminal wird keine Lösung mehr automatisch aufgedeckt – dort gehört Ausprobieren dazu. | `calculatePoints` |

Nebenbei behoben: Lückentext-Aufgaben, deren Lücken als einfache Strings hinterlegt sind (13 von 16), ließen sich bisher nie lösen (`ClassCastException` in der Validierung).

## Bewusst noch offen (spätere Phasen)

- **CODE, FILL_CODE, TERMINAL** prüfen weiterhin, was der Browser als Ergebnis meldet. Ohne ausgelieferte Lösung ist das schwerer zu fälschen, aber nicht unmöglich. Abhilfe: serverseitige Ausführung (ADR-4) bzw. Nachspielen der Befehlsfolge.
- **Asynchrone Punktevergabe über RabbitMQ** bleibt bis zum Umbau auf den modularen Monolithen (ADR-1).
- **MQTT** bleibt bis zur Umstellung auf Server-Sent Events (ADR-2); der Viewer-Zugang ist öffentlich, aber nur lesend.
- **JWT** bleibt bis zu serverseitigen Sessions (ADR-3).
- **Inhalte**: Die SQL-Aufgabe `sql-query-pro` akzeptiert auch `WHERE gehalt > 5000` ohne Abteilungsfilter, weil die Testdaten beide Abfragen gleich beantworten.

## Betrieb nach dem Update

1. `.env` aus `.env.example` anlegen und alle Werte setzen.
2. Bestehende Installationen: Das alte Admin-Konto behält sein Passwort. Wurde `admin123` nie geändert, jetzt im Profil ändern.
3. Tokens, die mit dem alten Standard-Secret signiert wurden, sind ungültig – alle Nutzer melden sich einmal neu an.

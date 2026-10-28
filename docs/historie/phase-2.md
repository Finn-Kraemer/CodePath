# Phase 2 – Aufgaben-Engine

Gate laut Plan: Sicherheits-Testkatalog grün, Einsatz in einem echten Kurs.

## Aufbau

Das Backend ist jetzt in die Pakete aus dem Plan geschnitten; ArchUnit prüft, dass kein Paket die Repositories eines anderen benutzt, dass es keine Zyklen gibt und dass die Engine von keinem Fachpaket abhängt (`ArchitectureTest`).

| Paket | Inhalt |
|---|---|
| `identity` | Konten, Kurse mit Beitrittscode, gesammelte Konten, Login mit Rate-Limit, Sessions |
| `content` | Module, Aufgaben, unveränderliche Versionen, Content-Pakete (Import, Startinhalt) |
| `engine` | Aufgabentypen: ein Handler je Typ (Schema-Prüfung, öffentliche Sicht, Bewertung, Lösungssicht, Probelauf) |
| `progress` | Abgabe-Ablauf, Versuche, Fortschritt, Punkte-Journal, Rangliste, Schülerakte, Lernanalyse |
| `review` | Praxisabgaben mit Rubrik-Bewertung |
| `classroom` | Modulfreigaben je Kurs (`AccessPolicy`), Ankündigungen, Feedback-Runde |
| `report` | Teilnahmebestätigung (PDF), ZIP aller Bestätigungen eines Kurses |
| `platform` | Fehler als RFC-9457-ProblemDetail, Audit-Log, Einstellungen, Server-Sent Events, Auslieferung des Frontends, OpenAPI |

## Aufgaben-Engine

Jeder Typ ist ein `TaskHandler`. Der Browser sieht nur `publicView`, meldet nie selbst ein Ergebnis, und `evaluate` läuft auf dem Server:

| Typ | Antwort | Bewertung |
|---|---|---|
| MULTIPLE_CHOICE | Options-IDs | gemischte Optionen, Einfach-/Mehrfachauswahl explizit, Erklärung je Option nach dem Lösen |
| FILL_BLANK | Texte je Lücke | mehrere akzeptierte Antworten, Normalisierung, Teilpunkte je Lücke, Lücken optional als Auswahlliste |
| FILL_CODE | Texte je Lücke | über die Lückenantworten; mit Code-Runner zählt auch jede andere Ergänzung, die alle Tests besteht |
| CODE | Code | nur mit Code-Runner (Phase 4), sonst Übung ohne Punkte (ADR-4) |
| SORTING | Element-IDs | gemischt ausgeliefert, Teilpunkte nach längster richtiger Teilfolge |
| SQL | Abfrage | JSqlParser lässt nur genau ein SELECT durch; frische H2-Datenbank mit Lesenutzer; Ergebnis-Vorschau als Rückmeldung |
| TERMINAL | Befehlsfolge | der Server spielt die Befehle in seinem eigenen Dateisystem-Modell nach (`ls cd pwd mkdir touch rm cat echo > mv cp`) |
| PRACTICE | Rich-Text | Lehrkraft bewertet mit Rubrik (Punkte je Kriterium, Kommentar) |

Einheitliche Regeln, je Aufgabe überschreibbar (`rules` in der Version):

- Versuche: 3 bei Auswahl-, Lücken- und Sortieraufgaben, unbegrenzt bei Code, SQL, Terminal. Unvollständige Eingaben (`INVALID`) zählen nicht.
- Hinweise: bis zu drei, gestuft; jeder kostet 25 % der Punkte (höchstens 75 %). Der Server zählt mit.
- Lösung aufdecken: jederzeit möglich, danach 0 Punkte.
- Bonus: 20 % für Lösen im ersten Versuch ohne Hinweis.
- Teilpunkte: Sind die Versuche aufgebraucht, zählt der beste Versuch anteilig.
- Bearbeitungszeit misst der Server (erstes Öffnen bis Lösen).

Jeder Versuch wird mit der Aufgabenversion gespeichert. Punkte stehen in einem Journal (`points_entries`); ein eindeutiger Index garantiert, dass es für das Lösen genau einmal Punkte gibt. Korrekturen der Lehrkraft sind eigene Buchungen.

## Kurse

- Registrierung nur mit Beitrittscode eines aktiven Kurses (`/register?code=…` füllt ihn vor).
- Die Lehrkraft kann Konten gesammelt anlegen (Passwörter werden einmal angezeigt), Codes erneuern, alle Sitzungen beenden.
- Modulfreigaben und Fristen gelten je Kurs.
- „Kurs beenden“ löscht alle Schülerkonten des Kurses samt Daten; vorher lassen sich alle Teilnahmebestätigungen als ZIP laden.

## Inhalte

Die 9 Module (123 Aufgaben) liegen als Content-Pakete unter `content/` (Markdown statt HTML, neue Konfigurationsformate). `content/tools/convert_legacy.py` hat sie aus den alten JSON-Dateien erzeugt; aus nummerierten Aufgabenpunkten der Praxisaufgaben wurden Rubrik-Kriterien. Beim Start werden fehlende Aufgaben als Version 1 angelegt; was im Editor geändert wurde, bleibt unangetastet (ADR-5).

## Daten

Migration `V12` übernimmt bestehende Daten: Schüler kommen in einen „Bestandskurs“, globale Freischaltungen werden Kursfreigaben, gelöste Aufgaben werden Punkte-Buchungen, Praxisabgaben werden Versuche mit Bewertung (`LegacyMigrationTest`).

## Frontend

- Eine Komponente je Aufgabentyp unter `src/lib/tasks/`, gemeinsamer `TaskRenderer`.
- Texte werden als Markdown gerendert und mit DOMPurify bereinigt; `{@html}` steht nur noch an zwei Stellen mit bereinigtem Inhalt.
- Entwürfe (Code, SQL, Lücken, Praxis) werden lokal zwischengespeichert, falls das WLAN kurz weg ist.
- API-Typen werden aus der OpenAPI-Beschreibung erzeugt (`pnpm gen:api`); die CI prüft, dass sie aktuell sind.
- Neue Seiten für die Lehrkraft: Kurse & Konten, Lernanalyse; Modul-Steuerung und Schülerakte mit Kursauswahl; Bewertung mit Rubrik.

## Tests

- `HandlersTest`: je Typ richtige, falsche und manipulierte Antworten.
- `SecurityCatalogTest`: keine Lösung in irgendeiner Schülerantwort (alle 123 Aufgaben), gesperrte Module und fremde Kurse, Fristen, genau einmal Punkte bei 16 parallelen Abgaben, gefälschte Client-Felder, Hinweis- und Aufdeck-Regeln, Versuchsgrenze mit Teilpunkten, Sperre durch die Lehrkraft, Terminal und SQL serverseitig.
- `ContentPackagesTest`: Probelauf für jede mitgelieferte Aufgabe.
- `PracticeReviewTest`, `AuthIntegrationTest`, `LegacyMigrationTest`, `ArchitectureTest`.
- Playwright (`frontend/e2e`): Kurs anlegen, Modul freischalten, mit Code beitreten, Terminal-Aufgabe lösen, Punkte in der Rangliste sehen. Läuft in der CI gegen das frisch gebaute Docker-Image.

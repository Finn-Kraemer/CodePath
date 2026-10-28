# Phase 3 – Inhalte und Editor

Gate laut Plan: neue Aufgabe in unter 10 Minuten, ohne Deployment. Der Playwright-Test `e2e/editor.spec.ts` legt eine Aufgabe an, prüft und veröffentlicht sie und öffnet sie in der Schüleransicht – in wenigen Sekunden.

## Umgesetzt

| Thema | Umsetzung |
|---|---|
| Editor | **Admin → Inhalte**: Module anlegen, Aufgaben anlegen (mit Vorlage je Typ), bearbeiten, prüfen, veröffentlichen, archivieren. Ein Formular je Aufgabentyp; für neue Typen und Sonderfälle ein JSON-Modus. |
| Vorschau | Neben dem Formular die Aufgabe so, wie Schüler sie sehen (dieselben Komponenten), mit Probebewertung beliebiger Antworten – nichts wird gespeichert. |
| Prüfen | Schema-Prüfung des Handlers plus Probelauf: Die Musterlösung muss als richtig, eine Falschantwort als falsch bewertet werden. Ohne bestandene Prüfung kein Veröffentlichen. |
| Versionen | Ein Entwurf ist eine unveröffentlichte Version. Veröffentlichen friert sie ein; frühere Versuche bleiben an ihrer Version. Ältere Versionen lassen sich als Entwurf zurückholen. |
| Content-Pakete | Export aller Inhalte als ZIP, Import (geänderte Aufgaben werden neue Versionen), „Mitgelieferte Inhalte übernehmen“ für Updates der Startinhalte. JSON-Schema unter `content/schema/`, Prüfung in der CI (`content/validate.py`). |
| Durchsicht | Lernziel für alle 123 Aufgaben; Aufzählungen und Anrede (du) vereinheitlicht; Testdaten der SQL-Aufgabe verschärft. Format und offene Punkte: `content/README.md`. |

## Hinweise für bestehende Installationen

Inhalte in der Datenbank werden beim Start nicht überschrieben. Um die überarbeiteten Startinhalte (Lernziele, Korrekturen) zu übernehmen: **Admin → Inhalte → Mitgelieferte Inhalte übernehmen**. Geänderte Aufgaben bekommen eine neue Version; laufende Fortschritte bleiben erhalten.

## Tests

- `ContentEditorTest`: anlegen → prüfen → Vorschau → veröffentlichen; ungültiger Entwurf wird abgelehnt; Version 2 lässt den alten Versuch an Version 1; Archivieren blendet aus; Export/Import ohne Änderung erzeugt keine neuen Versionen.
- `ContentPackagesTest`: Probelauf aller mitgelieferten Aufgaben.
- CI-Job „Content-Pakete prüfen“.

# Phase 5 – Neue Aufgabentypen

Plan: MATCHING, PARSONS, NUMBER_CONVERT und HOTSPOT zuerst. Jeder Typ besteht aus einem Handler im Backend, einer Komponente für die Schüleransicht, einem Formular im Editor und Tests (ADR-5: ein Typ = Handler + Komponente + Tests). Bewertet wird nur auf dem Server; die öffentliche Sicht enthält keine Lösung (`SecurityCatalogTest` prüft dafür jetzt auch `pairs`, `targets` und `indent`).

| Typ | Schüleransicht | Bewertung | Beispiel |
|---|---|---|---|
| `MATCHING` – Zuordnen | Auswahlliste je Begriff (tastaturbedienbar), rechte Seite gemischt, Ablenker möglich | Teilpunkte je richtigem Paar | `cloud-netze/c12` Dienste und Ports |
| `PARSONS` – Code-Puzzle | Bausteine anklicken, im Programm ziehen oder mit ▲▼ ordnen, ⇤⇥ einrücken | Richtig nur mit Reihenfolge und Einrückung; nur die Einrückung falsch = 75 %; Ablenker kosten Punkte. Gleich lautende Zeilen sind austauschbar | `programmierung/p15` Countdown |
| `NUMBER_CONVERT` – Zahlensysteme | Eingabefeld je Zahl | Jede Person bekommt eigene Zufallszahlen (Seed je Person und Aufgabe), daher nicht abschreibbar; Eingaben großzügig gelesen (Leerzeichen, `0b`/`0x`, Groß-/Kleinschreibung) | `digitaltechnik/d15` Dezimal → binär |
| `HOTSPOT` – Markieren | Zeilen eines Texts oder Codes anklicken | (Treffer − Fehlmarkierungen) / Ziele, alles markieren lohnt sich also nicht | `it-sicherheit/s14` Phishing-Mail |

Das Config-Format jedes Typs steht in `content/README.md` und im JSON-Schema `content/schema/task.schema.json`. Im Editor hat jeder Typ ein eigenes Formular; „Prüfen“ führt den Probelauf aus (Musterlösung richtig, Falschantwort falsch).

## Nebenbei behoben

- Terminal-Aufgaben: Der Effekt, der frühere Befehle wieder abspielt, löste sich selbst erneut aus, sodass die Seite nach einer Abgabe sekundenlang hing (sichtbar als gelegentlich roter E2E-Test). Jetzt mit `untrack`.
- E2E-Rangliste: Der eigene Name kann zweimal auf der Seite stehen.

## Tests

- `HandlersTest`: je Typ richtig, falsch, teilweise und manipuliert (unvollständig, doppelte Zeilen, alles markieren, Dezimalzahl statt Binärzahl abschreiben), Validierung, keine Lösung in der öffentlichen Sicht, eigene Zahlen je Person.
- `ContentPackagesTest`: Probelauf jetzt für 127 Aufgaben.
- `e2e/task-types.spec.ts`: Eine Schülerin löst alle vier Typen über die Oberfläche.

## Später

Weitere Typen aus dem Plan (z. B. Bild-Hotspots mit Hochladen, Logikgatter) passen in dasselbe Muster. Für Bilder braucht es zuerst einen Upload für Medien in Content-Paketen.

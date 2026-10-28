# Content-Pakete

Startinhalt und Austauschformat für CodePath (ADR-5). Die Datenbank ist die Quelle der Wahrheit; diese
Pakete werden beim ersten Start eingespielt und lassen sich im Admin-Bereich unter **Inhalte**
exportieren und importieren.

```
content/
  <modul>/module.json          Modul: slug, title, description, icon, sortOrder
  <modul>/tasks/NN-<slug>.json eine Datei je Aufgabe
  schema/                      JSON-Schema für beide Dateiarten
  validate.py                  Prüfung in der CI
  tools/                       einmalige Skripte (Umwandlung der alten Inhalte, Lernziele)
```

## Aufgabe

```json
{
  "slug": "g1",
  "type": "MULTIPLE_CHOICE",
  "title": "Warum brauchen wir Git?",
  "difficulty": "EASY",
  "points": 10,
  "sortOrder": 1,
  "learningGoal": "Du kannst erklären, welches Problem Versionskontrolle löst.",
  "story": "Markdown …",
  "assignment": "Markdown …",
  "hints": ["höchstens drei, gestuft"],
  "explanation": "Markdown, wird nach dem Lösen gezeigt",
  "rules": { "maxAttempts": 3, "hintPenaltyPercent": 25, "firstTryBonusPercent": 20, "revealAllowed": true },
  "config": { "…": "je Typ, siehe unten" }
}
```

`rules` ist optional; fehlende Werte nehmen den Standard des Typs.

## Konfiguration je Typ

| Typ | `config` |
|---|---|
| `MULTIPLE_CHOICE` | `options: [{id, text, explanation?}]`, `correct: [id]`, `multiple?`, `keepOrder?` |
| `FILL_BLANK` | `template` mit `___` je Lücke, `blanks: [{answers: [...], caseSensitive?, options?}]` |
| `FILL_CODE` | wie `FILL_BLANK`, dazu `tests: [{stdin?, append?, expectedOutput, hidden?}]` |
| `CODE` | `starterCode`, `solution` (Pflicht), `tests: [{stdin?, append?, expectedOutput, hidden?}]` – Tests sind versteckt, solange nicht `hidden: false`; `append` wird an die Abgabe angehängt. Erwartete Ausgaben am besten mit `tools/code_tasks.py` oder im Editor aus der Musterlösung erzeugen. |
| `SORTING` | `items: [{id, text}]`, `correctOrder: [id]` (Schüler bekommen die Elemente gemischt) |
| `SQL` | `schemaSql`, `solutionSql` (ein SELECT), `starterSql?`, `orderMatters?` |
| `TERMINAL` | `initialDirectories?`, `initialFiles?`, `expectedDirectories`, `expectedFiles`, `expectedContents?`, `allowExtra?` |
| `PRACTICE` | `instructions` (Markdown), `submissionNote?`, `rubric: [{id, label, points}]` – Summe = Punkte der Aufgabe |
| `MATCHING` | `left: [{id, text}]`, `right: [{id, text}]` (darf Ablenker enthalten), `pairs: [{left, right}]` – Teilpunkte je Paar |
| `PARSONS` | `lines: [{id, code, indent?}]` in richtiger Reihenfolge, `distractors?: [{id, code}]`, `checkIndent?` (Standard `true`) |
| `NUMBER_CONVERT` | `from`, `to` (2, 8, 10, 16) und entweder `min`, `max`, `count` (eigene Zufallszahlen je Person) oder feste `values` |
| `HOTSPOT` | `text` (mehrzeilig), `targets: [Zeilennummer ab 1]`, `mono?`, `showCount?` (Standard `true`) – Fehlmarkierungen kosten Punkte |
| `IMAGE_HOTSPOT` | `image` (Bild-Schlüssel), `alt`, `targets: [{id, x, y, w, h, label?}]` in Prozent des Bildes, `showCount?` – höchstens Ziele + 2 Markierungen, daneben kostet Punkte |
| `LOGIC_CIRCUIT` | `inputs: ["A","B"]` (bis 4), `expression` (z. B. `(A OR B) AND NOT C`), `allowedGates?`, `maxGates?`, `showExpression?`, `solution: {gates: [{id, type, inputs}], output}` – bewertet wird die Wahrheitstabelle |

## Bilder

Bilder liegen unter `<modul>/media/`, benannt nach ihrem Inhalt (SHA-256 + Endung). So kommt ein Bild hinein:

```bash
python content/tools/add_media.py bild.png it-sicherheit   # gibt den Schlüssel aus
```

Verwenden: in `IMAGE_HOTSPOT` als `"image": "<schlüssel>"`, in Markdown als `![Beschreibung](/api/media/<schlüssel>)`. Andere Bildquellen zeigt CodePath nicht an (offline, keine Fremdabrufe). Im Editor geht beides per Upload („Bild in die Geschichte einfügen“, „Bild wählen oder hochladen“). Erlaubt: PNG, JPEG, GIF, WebP bis 2 MB, kein SVG.

## Prüfen

```bash
pip install jsonschema && python content/validate.py   # Schema, Lücken, Rubrik-Summen
cd backend && ./mvnw test -Dtest=ContentPackagesTest   # Probelauf jeder Aufgabe auf dem Server
```

## Inhaltliche Durchsicht (Stand Phase 3)

- Alle 123 Aufgaben haben ein Lernziel.
- Aus nummerierten Punkten der Praxisaufgaben wurden Rubrik-Kriterien mit gleich verteilten Punkten – die Gewichtung bitte im Editor anpassen.
- `sql-query-pro`: Testdaten so erweitert, dass eine Abfrage ohne Abteilungsfilter nicht mehr als richtig gilt.
- Offen für die Lehrkraft: Erklärungen nach dem Lösen (`explanation`) und Erklärungen je Antwortoption fehlen noch bei den meisten Aufgaben; Code-Aufgaben brauchen Musterlösungen und weitere Testfälle (siehe Phase 4).

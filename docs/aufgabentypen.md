# Aufgabentypen

CodePath kennt 15 Aufgabentypen. Alle werden **auf dem Server** bewertet: Der Browser bekommt nur, was er zum Anzeigen braucht, nie die Lösung, und er meldet nie selbst ein Ergebnis. Diese Seite beschreibt, wofür sich jeder Typ eignet und wie er bewertet. Das genaue Konfigurationsformat steht in [`content/README.md`](../content/README.md); im Editor hat jeder Typ ein eigenes Formular.

## ◼ Übersicht

| Typ | Name in CodePath | Die Klasse … | Bewertung |
|---|---|---|---|
| `MULTIPLE_CHOICE` | Auswahl | wählt eine oder mehrere Antworten | richtig, wenn genau die richtigen gewählt sind; nach dem Lösen Erklärungen je Antwort |
| `FILL_BLANK` | Lückentext | füllt Lücken im Text, frei oder per Auswahlliste | Teilpunkte je Lücke; mehrere richtige Schreibweisen möglich |
| `FILL_CODE` | Code-Lücken | ergänzt Lücken in einem Python-Programm und kann es im Browser ausführen | wie Lückentext; mit Code-Runner zählt auch jede andere Ergänzung, die alle Tests besteht |
| `CODE` | Programmieren | schreibt Python im Editor, probiert es im Browser aus | mit Code-Runner gegen sichtbare und versteckte Testfälle, Teilpunkte je Test; ohne Runner Übung ohne Punkte |
| `SORTING` | Sortieren | bringt Schritte per Drag & Drop in die richtige Reihenfolge | Teilpunkte nach der längsten richtig geordneten Folge |
| `SQL` | SQL | schreibt eine `SELECT`-Abfrage gegen eine Übungsdatenbank | Ergebnis wird mit dem der Musterlösung verglichen; Reihenfolge optional egal |
| `TERMINAL` | Terminal | arbeitet in einer simulierten Kommandozeile – Dateien (`ls`, `cd`, `mkdir`, `mv`, …), Suchen und Filtern (`grep`, `find`, `head`, `tail`, `wc`, Pipes) und Git (`add`, `commit`, `branch`, `merge`, `push`, …), optional mit Antwortfeld ([mehr](#-terminal-und-git)) | der Server spielt die Befehle nach und prüft Dateisystem, Git-Repository und Antwort |
| `PRACTICE` | Praxis | gibt einen Freitext mit Formatierung ab, optional mit Python-Programm aus der Werkbank | die Lehrkraft bewertet nach einer Rubrik und kann den Code ausführen |
| `MATCHING` | Zuordnen | ordnet Begriffe zu, rechts gibt es Ablenker | Teilpunkte je richtigem Paar |
| `PARSONS` | Code-Puzzle | setzt ein Programm aus Bausteinen zusammen und rückt ein | richtig nur mit Reihenfolge und Einrückung; nur die Einrückung falsch: 75 %; Ablenker kosten |
| `NUMBER_CONVERT` | Zahlensysteme | rechnet zwischen binär, oktal, dezimal und hexadezimal um | Teilpunkte je Zahl; jede Person bekommt eigene Zufallszahlen |
| `HOTSPOT` | Markieren | markiert Zeilen in einem Text oder Code | Treffer minus Fehlmarkierungen |
| `IMAGE_HOTSPOT` | Im Bild markieren | klickt Stellen in einem Bild an | getroffene Ziele minus Klicks daneben; höchstens Ziele + 2 Markierungen |
| `LOGIC_CIRCUIT` | Logikgatter | baut eine Schaltung aus Gattern, mit Schaltplan und Live-Wahrheitstabelle | jede Schaltung mit der richtigen Wahrheitstabelle zählt; Teilpunkte erst über der Hälfte der Zeilen |
| `DEVTOOLS` | Browser-Labor | untersucht eine Übungsseite im eigenen Tab mit Konsole, Inspektor, Netzwerk und Web-Speicher | Code aus mehreren Teilen, je Person verschieden; Teilpunkte je Teil ([mehr](browser-labor.md)) |
| `PREDICT` | Vorhersage | sieht ein Programm mit Haltepunkt (●) und sagt voraus, welche Werte Variablen dort haben; nach dem Lösen Auflösung im Code-Visualizer | wie Lückentext, Teilpunkte je Variable |

## ◼ Standardregeln

Jeder Typ hat Standardregeln für Versuche, Hinweise und Bonus. Im Editor lassen sie sich je Aufgabe überschreiben (*Regeln*).

| Typen | Versuche | Abzug je Hinweis | Bonus 1. Versuch | Lösung aufdecken |
|---|---|---|---|---|
| Auswahl, Lückentext, Sortieren, Zuordnen, Markieren, Im Bild markieren, Vorhersage | 3 | 25 % | 20 % | ja |
| Zahlensysteme, Code-Puzzle | 5 | 25 % | 20 % | ja |
| Browser-Labor | 10 | 25 % | 20 % | ja |
| Programmieren, Code-Lücken, SQL, Terminal, Logikgatter | unbegrenzt | 25 % | 20 % | ja |
| Praxis | unbegrenzt | 25 % | – | nein |

- **Hinweise:** bis zu drei je Aufgabe, gestuft vom Denkanstoß bis fast zur Lösung. Der Abzug summiert sich auf höchstens 75 %.
- **Bonus:** wer im ersten Versuch ohne Hinweis löst, bekommt 20 % zusätzlich.
- **Aufdecken:** zeigt die Lösung; die Aufgabe bringt danach keine Punkte mehr.
- **Teilpunkte:** Bei begrenzten Versuchen gibt es nach dem letzten Versuch Punkte für den besten Teilerfolg. Bei unbegrenzten Versuchen zählt nur die vollständige Lösung – man kann ja weiter probieren. Ungültige oder unvollständige Antworten zählen nicht als Versuch.
- **Einmal Punkte:** Jede Aufgabe bringt einer Person genau einmal Punkte – auch wenn mehrere Abgaben gleichzeitig ankommen.

## ◼ Welcher Typ wofür?

| Ziel | Gut geeignet |
|---|---|
| Begriffe und Zusammenhänge sichern | Auswahl, Zuordnen, Lückentext |
| Abläufe verstehen (Netzwerk, Softwareentwicklung) | Sortieren |
| Erste Schritte im Programmieren | Code-Lücken, Code-Puzzle |
| Selbst programmieren | Programmieren (am besten mit Code-Runner) |
| Datenbanken | SQL |
| Werkzeuge der Profis | Terminal (auch Git), Browser-Labor |
| Genau hinsehen: Fehler, Risiken, Phishing | Markieren, Im Bild markieren |
| Digitaltechnik | Zahlensysteme, Logikgatter |
| Kreative und offene Aufgaben | Praxis |

## ◼ Schritt für Schritt (Code-Visualizer)

Bei `CODE`, `FILL_CODE` und in der Python-Werkbank von `PRACTICE` gibt es neben „Ausprobieren“ den Button **„Schritt für Schritt“**. Er führt das Programm im Browser aus (Pyodide, offline) und zeichnet jede Zeile auf. Mit Vor/Zurück, Abspielen, Schieberegler oder den Pfeiltasten ←/→ sehen Schülerinnen und Schüler:

- die Zeile, die Python **als Nächstes** ausführt (gelb, ▶), und die Zeile davor (hell, ·),
- die Variablen je Funktionsaufruf als Stapel – Rekursion wird sichtbar,
- Listen, Tupel, Mengen und Dicts als Kästchenreihe mit Index; zeigen zwei Variablen auf **dieselbe** Liste, steht das dabei,
- Rückgabewerte, die Ausgabe bis zum aktuellen Schritt und jede Änderung zusätzlich als Text („`i` ist jetzt 3“).

Nach dem Lösen lässt sich auch die Musterlösung Schritt für Schritt ansehen (nur, wo sie ohnehin gezeigt wird). Der Visualizer ist ein reines Lernwerkzeug: Nichts wird bewertet, gezählt oder auf dem Server gespeichert. Eingaben für `input()` kommen aus dem ersten Beispiel bzw. dem Eingabefeld der Werkbank.

- **Grenzen:** nach 1.000 Schritten (bzw. 5 s) hält die Aufzeichnung an – „läuft deine Schleife endlos?“. Je Aufgabe einstellbar mit `visualizerMaxSteps` (höchstens 5.000).
- **Abschalten:** `visualizer: false` in der Konfiguration (im Editor: „Schritt für Schritt“ → Aus). Ohne Angabe ist er an, im Modul `abschlusstest` aus.
- **Vorhersage-Aufgaben (`PREDICT`):** „Welchen Wert hat `summe`, wenn Zeile 4 zum 3. Mal drankommt?“ Angehalten wird *bevor* die Zeile ausgeführt wird – genau wie beim gelben Balken. Im Editor trägt „Werte aus dem Programm ermitteln“ die richtigen Antworten ein. Nach dem Lösen springt „Auflösung Schritt für Schritt“ direkt zum Haltepunkt.
- **Baustein in Texten:** Ein Code-Block mit der Sprache `schritte` wird in Wissensartikeln, eigenen Seiten und Aufgabentexten zu einem Beispiel mit „Schritt für Schritt ansehen“ – gut zum Vorführen am Beamer:

  ````markdown
  ```schritte
  liste = [5, 3, 8, 1]
  liste[0], liste[1] = liste[1], liste[0]
  ```
  ````

## ◼ Terminal und Git

Das Terminal ist eine Simulation im Browser – es läuft nichts auf dem Server oder dem Schulrechner. Der Server spielt die Befehlsfolge bei der Abgabe selbst nach; was die Klasse im Browser sieht und was bewertet wird, kommt aus zwei gleichwertigen Modellen, die dieselben Testfälle bestehen.

**Befehle:** `pwd`, `ls [-a]`, `cd`, `mkdir [-p]`, `touch`, `rm [-r]`, `mv`, `cp [-r]`, `cat`, `echo`, `head`/`tail [-n N]`, `grep [-i -n -c -v -r]` (sucht Text, keine regulären Ausdrücke), `find [ordner] -name muster [-type f|d]`, `wc [-l -w -c]`, `sort [-r]`, `uniq [-c]`, `history`, `man befehl`, dazu Pipes `|` und Umleitungen `>`/`>>`. Mit ↑/↓ holt man alte Befehle zurück, Tab ergänzt Befehle und Pfade, ein Spickzettel steht unter dem Terminal.

**Git (vereinfacht):** `init`, `status`, `add`, `commit [-a] -m`, `log [--oneline --all]`, `show`, `diff [--staged]`, `restore [--staged]`, `rm [--cached]`, `branch [-d]`, `switch [-c]`/`checkout [-b]`, `merge` (mit Konflikten und `--abort`), `checkout --ours/--theirs`, `tag`, `push`/`pull`/`fetch` gegen einen simulierten Server `origin` und `.gitignore`. Sobald es ein Repository gibt, zeigt ein **Commit-Graph** unter dem Terminal live Commits, Branches, Tags und den Stand auf dem Server.

**Bewertet werden kann:**

| Was | Konfiguration |
|---|---|
| Ordner und Dateien sind da, Inhalte stimmen | `expectedDirectories`, `expectedFiles`, `expectedContents`; ohne `allowExtra` dürfen keine weiteren Dateien entstehen |
| Etwas ist weg | `absent` |
| Die Antwort im Antwortfeld | `answer` (eine oder mehrere, Groß-/Kleinschreibung egal), `answerLabel` als Frage |
| Der Zustand des Git-Repositorys | `gitChecks`, z. B. `commits` (Mindestzahl), `committed` (Datei, optional mit Inhalt), `neverCommitted` (z. B. ein Passwort), `branch`, `merged`, `clean`, `pushed`, `tag`, `ignored`, `head`, `author` |

Ohne Antwort zählt nur die vollständige Lösung; die Rückmeldung nennt, wie viele Anforderungen erfüllt sind und was noch offen ist – ohne die Lösung zu verraten. Ein **vorbereitetes Repository** mit Verlauf, Branches und Commits, die nur auf dem Server liegen, beschreibt `git`. Mit `variants` bekommt jede Person andere Werte (z. B. einen anderen Dienst im Log oder einen anderen Täter im Verlauf), `{{code}}` ist ein Code je Person – Abschreiben beim Nachbarn hilft dann nicht. Format: [`content/README.md`](../content/README.md).

## ◼ Gute Aufgaben schreiben

- **Geschichte vor Auftrag.** Jedes Modul ist ein Berufsfeld; eine Aufgabe beginnt mit einer Situation aus diesem Beruf („Der Shop nimmt keine Bestellungen an …“).
- **Ein Lernziel, ein Satz.** „Du kannst …“ – es steht über der Aufgabe und hilft beim Einordnen.
- **Hinweise stufen.** Erster Hinweis: wo suchen. Zweiter: wie vorgehen. Dritter: fast die Lösung.
- **Erklärung nach dem Lösen.** Warum ist die Lösung richtig, was war die Falle? Die Erklärung erscheint, sobald die Aufgabe gelöst oder aufgedeckt ist.
- **Vor dem Veröffentlichen prüfen.** *Prüfen* im Editor bewertet die Musterlösung und eine Falschantwort; erst wenn beides stimmt, lässt sich veröffentlichen.

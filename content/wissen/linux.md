---
title: Kommandozeile (Terminal)
icon: ⌨️
---

# Kommandozeile (Terminal)

Als Programmiererin oder Administrator klickt man sich selten durch Ordner. Stattdessen gibt man dem Computer im Terminal Befehle per Text. Das ist schneller, funktioniert auch bei Servern ohne Bildschirm und lässt sich leicht automatisieren.

Jeder Befehl folgt demselben Muster: **Befehl, Optionen, Ziel** – zum Beispiel `ls -a werkzeuge`. Optionen beginnen mit `-`. Mit `man befehl` bekommst du zu jedem Befehl eine kurze Hilfe.

## Wo bin ich? – Navigation

| Befehl | Bedeutung |
|---|---|
| `pwd` | zeigt den Ordner, in dem du gerade bist (*print working directory*) |
| `ls` | zeigt, was im Ordner liegt; Ordner enden mit `/` |
| `ls -a` | zeigt auch versteckte Dateien – deren Name beginnt mit einem Punkt, z. B. `.gitignore` |
| `cd ordner` | wechselt in einen Ordner (*change directory*) |
| `cd ..` | eine Ebene nach oben |
| `cd ~` | zurück in deinen Home-Ordner |

**Pfade:** `logs/app.log` ist *relativ* – er beginnt dort, wo du gerade bist. `~/server/logs/app.log` ist *absolut* – er führt von überall zum selben Ziel. `..` heißt „eine Ebene höher“, `../bilder` also „Nachbarordner bilder“.

## Dateien und Ordner verwalten

| Befehl | Bedeutung |
|---|---|
| `mkdir ordner` | legt einen Ordner an; `mkdir -p a/b/c` auch alle Zwischenordner |
| `touch datei.txt` | legt eine leere Datei an |
| `cp quelle ziel` | kopiert; `cp -r` kopiert ganze Ordner |
| `mv quelle ziel` | verschiebt – oder benennt um, wenn das Ziel ein neuer Name ist |
| `rm datei.txt` | löscht **endgültig**, ohne Papierkorb |
| `rm -r ordner` | löscht einen Ordner mit allem darin |

> Vor jedem `rm` mit `pwd` und `ls` prüfen, wo du bist. Auf einem Server gibt es kein „Rückgängig“.

## Dateien lesen und durchsuchen

| Befehl | Bedeutung |
|---|---|
| `cat datei` | zeigt die ganze Datei |
| `head -n 5 datei` | die ersten 5 Zeilen |
| `tail -n 5 datei` | die letzten 5 Zeilen – bei Logs stehen dort die neuesten Einträge |
| `grep text datei` | zeigt nur Zeilen, die `text` enthalten |
| `grep -i`, `-n`, `-c`, `-v`, `-r` | Groß-/Kleinschreibung egal, mit Zeilennummer, nur zählen, Zeilen *ohne* den Text, in allen Dateien eines Ordners |
| `find . -name "*.pdf"` | sucht ab hier in allen Unterordnern nach Namen; `*` steht für beliebige Zeichen |
| `wc -l datei` | zählt Zeilen (`-w` Wörter, `-c` Zeichen) |
| `sort`, `uniq -c` | sortiert Zeilen, zählt gleiche Zeilen hintereinander |

## Befehle verketten

- `befehl > datei` schreibt die Ausgabe in eine Datei (der alte Inhalt ist weg).
- `befehl >> datei` hängt die Ausgabe hinten an.
- `befehl1 | befehl2` – die **Pipe** – gibt die Ausgabe des ersten Befehls an den zweiten weiter.

```bash
echo "port=8080" >> server/config/app.conf   # Zeile anhängen
grep Fehlversuch auth.log | wc -l             # Fehlversuche zählen
sort namen.txt | uniq -c                       # wer kommt wie oft vor?
```

Kleine Werkzeuge, die je eine Sache gut können, werden so zu einer Kette – eine der wichtigsten Ideen der Kommandozeile.

## Schneller tippen

- **Tab** ergänzt Befehle, Ordner- und Dateinamen: `cd ser` + Tab wird zu `cd server/`.
- **↑ und ↓** holen frühere Befehle zurück, `history` zeigt alle.
- `clear` leert den Bildschirm.

---
title: Kommandozeile (Terminal)
icon: ⌨️
---

# Kommandozeile (Terminal)

Als Programmierer klickt man sich selten durch Ordner. Stattdessen gibt man dem Computer im **Terminal** (auch Konsole, Shell oder Kommandozeile) Befehle als Text. Das ist oft schneller, funktioniert auch auf Servern ohne Bildschirm und lässt sich leicht automatisieren.

---

## 1. Aufbau eines Befehls

```
ls   -l    dokumente
│    │     └─ Argument: worauf sich der Befehl bezieht
│    └─ Option: verändert das Verhalten (beginnt mit -)
└─ Befehl
```

Groß- und Kleinschreibung zählt: `Dokumente` und `dokumente` sind zwei verschiedene Ordner.

---

## 2. Pfade: Wo bin ich?

Ordner sind wie ein **Baum** aufgebaut. Ganz oben steht die Wurzel `/`, dein persönlicher Ordner heißt `~` (Home).

```
~
├── schule
│   ├── mathe
│   └── info
│       └── projekt.py
└── bilder
```

| Schreibweise    | Bedeutung                                            |
| --------------- | ---------------------------------------------------- |
| `~`             | dein Home-Ordner                                     |
| `.`             | der aktuelle Ordner                                  |
| `..`            | der Ordner eine Ebene darüber                        |
| `schule/info`   | **relativer** Pfad – vom aktuellen Ordner aus        |
| `/home/lena/schule` | **absoluter** Pfad – von der Wurzel aus          |

---

## 3. Navigation

| Befehl           | Bedeutung                                                    |
| ---------------- | ------------------------------------------------------------ |
| `pwd`            | **p**rint **w**orking **d**irectory – zeigt, wo du gerade bist |
| `ls`             | **l**i**s**t – zeigt Dateien und Ordner                      |
| `ls -l`          | ausführliche Liste mit Größe, Datum, Rechten                 |
| `ls -a`          | auch versteckte Dateien (Name beginnt mit `.`)               |
| `cd schule`      | **c**hange **d**irectory – in den Ordner `schule` wechseln    |
| `cd schule/info` | mehrere Ebenen auf einmal                                    |
| `cd ..`          | eine Ebene nach oben                                         |
| `cd` oder `cd ~` | zurück in den Home-Ordner                                    |

---

## 4. Dateien und Ordner verwalten

| Befehl                     | Bedeutung                                                         |
| -------------------------- | ----------------------------------------------------------------- |
| `mkdir projekt`            | **m**a**k**e **dir**ectory – neuen Ordner anlegen                 |
| `mkdir -p a/b/c`           | mehrere verschachtelte Ordner auf einmal                          |
| `touch index.html`         | leere Datei anlegen                                               |
| `touch projekt/style.css`  | Datei direkt in einem Unterordner anlegen                         |
| `echo "Hallo" > datei.txt` | Text in eine Datei schreiben (überschreibt!)                      |
| `echo "Mehr" >> datei.txt` | Text **anhängen**                                                 |
| `cat datei.txt`            | Inhalt einer Datei anzeigen                                       |
| `cp quelle ziel`           | **c**o**p**y – kopieren                                           |
| `mv alt neu`               | **m**o**v**e – verschieben oder umbenennen                        |
| `rm datei.txt`             | **r**e**m**ove – Datei löschen                                     |
| `rm -r ordner`             | Ordner mit allem Inhalt löschen                                   |

> ⚠️ `rm` löscht **endgültig** – es gibt keinen Papierkorb!

### Beispiel: Projekt anlegen

```bash
mkdir website
cd website
touch index.html styles.css
ls
# index.html  styles.css
cd ..
```

Oder ohne in den Ordner zu wechseln:

```bash
mkdir website
touch website/index.html website/styles.css
```

---

## 5. Hilfreiche Tricks

- **Tab-Taste:** vervollständigt Befehle und Namen. `cd sch` + Tab → `cd schule/`.
- **Pfeil nach oben:** holt die letzten Befehle zurück.
- **Strg + C:** bricht ein laufendes Programm ab.
- **`clear`** (oder Strg + L): leert den Bildschirm.
- **`help`**, **`man befehl`** oder **`befehl --help`**: zeigt die Hilfe zu einem Befehl.
- Namen mit Leerzeichen in Anführungszeichen setzen: `cd "Meine Dateien"` – oder Leerzeichen in Dateinamen ganz vermeiden.

---

## 6. Weitere nützliche Befehle

| Befehl                 | Bedeutung                                         |
| ---------------------- | ------------------------------------------------- |
| `head datei` / `tail datei` | erste / letzte Zeilen einer Datei            |
| `grep wort datei`      | Zeilen suchen, die `wort` enthalten               |
| `find . -name "*.py"`  | alle Python-Dateien unterhalb des Ordners finden  |
| `wc -l datei`          | Zeilen zählen (`-w` Wörter, `-c` Zeichen)         |
| `sort datei`, `uniq -c` | Zeilen sortieren, gleiche Zeilen zählen          |
| `befehl > datei`       | Ausgabe in eine Datei schreiben (`>>` hängt an)   |
| `befehl1 \| befehl2`   | Pipe: Ausgabe des ersten an den zweiten weitergeben, z. B. `grep Fehler log.txt \| wc -l` |
| `python3 programm.py`  | Python-Programm ausführen                         |
| `ping schule.de`       | Erreichbarkeit eines Rechners prüfen              |
| `sudo befehl`          | Befehl mit Administratorrechten ausführen (Vorsicht!) |

> **Hinweis zum Übungsterminal in CodePath:** Es simuliert die wichtigsten Befehle (`pwd`, `ls`, `cd`, `mkdir`, `touch`, `rm`, `cat`, `echo`, `mv`, `cp`, `head`, `tail`, `grep`, `find`, `wc`, `sort`, `uniq`, `history`, `man`, `clear`) mit Pipes und `>`/`>>` – und ein vereinfachtes `git`. Mit `help` siehst du alle, mit `submit` gibst du ab.

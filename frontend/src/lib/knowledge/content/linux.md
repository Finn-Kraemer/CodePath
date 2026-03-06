---
title: Kommandozeile (Terminal)
icon: ⌨️
---

# Kommandozeile (Terminal)

Als Programmierer klickt man sich selten durch Ordner. Stattdessen nutzt man das Terminal, um dem Computer Befehle per Text zu geben. Das ist oft viel schneller und lässt sich leicht automatisieren.

## Navigation (Wo bin ich?)

- `pwd` (Print Working Directory): Zeigt dir den Pfad des Ordners an, in dem du gerade bist.
- `ls` (List): Zeigt alle Dateien und Ordner im aktuellen Verzeichnis an.
  - `ls -a` zeigt auch versteckte Dateien (die mit einem Punkt beginnen).
- `cd` (Change Directory): Wechselt in einen anderen Ordner.
  - `cd desktop` wechselt in den Ordner "desktop".
  - `cd ..` springt einen Ordner nach oben.

## Dateien verwalten

- `mkdir ordnername` (Make Directory): Erstellt einen neuen Ordner.
- `touch datei.txt`: Erstellt eine neue, leere Datei.
- `rm datei.txt` (Remove): Löscht eine Datei dauerhaft (ohne Papierkorb!).
- `rm -r ordnername`: Löscht einen Ordner und alles, was darin ist.

## Autovervollständigung

Der wichtigste Knopf im Terminal ist die **Tab-Taste**. Wenn du `cd des` tippst und Tab drückst, vervollständigt das Terminal den Rest automatisch zu `cd desktop`, sofern es eindeutig ist.

---
title: Versionskontrolle (Git)
icon: 🔄
---

# Versionskontrolle mit Git

Stell dir vor, du schreibst eine Hausarbeit und speicherst Versionen wie `arbeit_v1.doc`, `arbeit_final.doc`, `arbeit_wirklich_final.doc`. In der Softwareentwicklung wäre das Chaos. Dafür gibt es **Git**.

Git merkt sich jede Änderung: wer sie gemacht hat, wann und warum. Du kannst jederzeit zu einem früheren Stand zurück und mit vielen Leuten gleichzeitig am selben Projekt arbeiten, ohne euch gegenseitig die Arbeit zu überschreiben.

## Die wichtigsten Begriffe

- **Repository (Repo):** Der Projektordner samt der gesamten Git-Historie. Die steckt im versteckten Unterordner `.git`.
- **Commit:** Ein Speicherpunkt – ein Schnappschuss des Projekts mit Kennung (z. B. `a1b2c3d`), Autor und Nachricht.
- **Staging Area:** Der Wartebereich. Mit `git add` legst du dort ab, was in den nächsten Commit soll.
- **Branch (Zweig):** Eine eigene Entwicklungslinie. Auf `main` liegt die Version, die alle benutzen; neue Funktionen entstehen auf eigenen Branches.
- **Merge:** Das Zusammenführen eines Branches in einen anderen.
- **Remote (`origin`):** Das gemeinsame Repository auf einem Server, z. B. GitHub, GitLab oder dem Schulserver.

## Der Weg einer Änderung

```
Arbeitsordner  ──git add──▶  Staging Area  ──git commit──▶  Repository  ──git push──▶  Server
```

```bash
git init                         # Ordner zum Repository machen
git status                       # Was ist neu, geändert, vorgemerkt?
git add index.html               # Datei vormerken (git add . = alles)
git commit -m "Startseite"       # Vorgemerktes speichern
git log --oneline                # Verlauf ansehen
```

**Gute Commit-Nachrichten** sagen, was sich ändert: „Login-Knopf reagiert wieder auf Klick“ statt „fix“.

## Nachsehen und zurückholen

```bash
git diff                         # Was habe ich seit dem letzten Commit geändert?
git show a1b2c3d                 # Was hat dieser Commit geändert? (- entfernt, + hinzugefügt)
git restore index.html           # Datei auf den letzten Commit zurücksetzen
git restore --staged index.html  # Vormerkung zurücknehmen
```

## Was nicht ins Repository gehört

In die Datei `.gitignore` schreibst du Muster für Dateien, die Git ignorieren soll – eins pro Zeile:

```
.env
*.log
node_modules/
```

Passwörter gehören **nie** in einen Commit. Was einmal gepusht wurde, gilt als verraten – auch wenn man den Commit später löscht.

## Branches und Zusammenführen

```bash
git switch -c kontakt            # neuen Branch anlegen und wechseln
git switch main                  # zurück zu main
git merge kontakt                # kontakt in den aktuellen Branch holen
git branch                       # alle Branches, * = aktueller
git tag v1.0                     # Version markieren
```

**Merge-Konflikt:** Haben zwei Branches dieselbe Zeile verschieden geändert, fragt Git nach. In der Datei stehen dann beide Versionen:

```
<<<<<<< HEAD
h1 { color: red; }
=======
h1 { color: purple; }
>>>>>>> design
```

Entscheide, was bleibt: Datei reparieren oder mit `git checkout --ours datei` (deine Seite) bzw. `--theirs` (die andere Seite) übernehmen – dann `git add datei` und `git commit`.

## Zusammenarbeit im Team

```bash
git pull                         # Änderungen der anderen holen
git push                         # eigene Commits hochladen
```

Der Server lehnt einen Push ab, wenn dort Commits liegen, die du noch nicht hast – sonst würdest du fremde Arbeit überschreiben. Deshalb gilt: **erst pullen, dann pushen.**

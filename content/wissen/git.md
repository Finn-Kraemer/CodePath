---
title: Versionskontrolle (Git)
icon: 🔄
---

# Versionskontrolle mit Git

Stell dir vor, du schreibst eine Hausarbeit und speicherst Versionen wie `arbeit_v1.doc`, `arbeit_final.doc`, `arbeit_wirklich_final.doc`. In der Softwareentwicklung wäre das Chaos. Dafür gibt es **Git**.

Git verfolgt jede einzelne Änderung in deinem Code. Du kannst jederzeit zu einem früheren Stand zurückspringen oder mit 100 anderen Leuten am selben Projekt arbeiten, ohne euch in die Quere zu kommen.

## Die wichtigsten Begriffe

- **Repository (Repo):** Der Ordner, in dem dein Projekt und die gesamte Git-Historie liegt.
- **Commit:** Ein Speicherpunkt. Du sagst Git: "Speichere den aktuellen Stand genau so ab." Du fügst immer eine Nachricht hinzu, was du geändert hast.
- **Branch (Zweig):** Wenn du ein neues Feature baust, erstellst du einen neuen "Ast". Dort kannst du experimentieren, ohne den Haupt-Code (meist `main` genannt) kaputt zu machen.
- **Merge:** Das Zusammenführen deines Branches zurück in den Haupt-Code, wenn dein Feature fertig ist.

## Wichtige Befehle im Terminal

```bash
# Zeigt an, welche Dateien geändert wurden
git status

# Bereitet geänderte Dateien für den Commit vor
git add meine-datei.py

# Speichert den Stand dauerhaft
git commit -m "Navigation hinzugefügt"

# Lädt den Code hoch zu GitHub/GitLab
git push
```

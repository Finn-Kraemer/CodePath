---
title: Versionskontrolle (Git)
icon: 🔄
---

# Versionskontrolle mit Git

Stell dir vor, du speicherst Versionen wie `arbeit_v1.doc`, `arbeit_final.doc`, `arbeit_wirklich_final.doc`. Im Team wäre das Chaos: Wer hat was geändert? Welche Datei ist aktuell? Wie komme ich zurück, wenn etwas kaputtgeht?

**Git** ist ein **Versionskontrollsystem**. Es merkt sich jede Änderung mit Autor, Zeitpunkt und Beschreibung. Du kannst jederzeit zu einem früheren Stand zurück, und viele Menschen können gleichzeitig am selben Projekt arbeiten.

---

## 1. Die wichtigsten Begriffe

| Begriff                | Bedeutung                                                                                  |
| ---------------------- | ------------------------------------------------------------------------------------------ |
| **Repository (Repo)**  | Projektordner **plus** die komplette Versionsgeschichte (im versteckten Ordner `.git`)     |
| **Commit**             | ein gespeicherter Schnappschuss des Projekts mit Nachricht, Autor und Zeitpunkt           |
| **Branch**             | ein eigener Entwicklungszweig, z. B. für ein neues Feature                                  |
| **main**               | der Hauptzweig mit dem stabilen Stand                                                      |
| **Merge**              | zwei Branches zusammenführen                                                               |
| **Remote**             | eine Kopie des Repos auf einem Server, z. B. bei GitHub oder GitLab (meist `origin` genannt) |
| **Clone**              | ein Remote-Repo komplett auf den eigenen Rechner kopieren                                  |
| **Push / Pull**        | eigene Commits hochladen / fremde Commits herunterladen                                    |

---

## 2. Die drei Zonen

Git unterscheidet drei Bereiche. Eine Änderung wandert von links nach rechts:

```
 Working Directory   ── git add ──▶   Staging Area   ── git commit ──▶   Repository
 (Arbeitsbereich)                     (Wartebereich)                     (Verlauf)
 Hier bearbeitest du                  Hier sammelst du,                  Hier liegen alle
 deine Dateien.                       was in den nächsten                Commits dauerhaft.
                                      Commit soll.
```

Warum der Zwischenschritt? Du kannst **auswählen**, welche Änderungen zusammengehören, und sie als einen sinnvollen Commit speichern – auch wenn du an mehreren Dingen gleichzeitig gearbeitet hast.

---

## 3. Der erste Commit

```bash
git init                         # Ordner zu einem Repository machen (einmalig)
git status                       # Was hat sich geändert?
git add index.html               # eine Datei in die Staging Area
git add .                        # alle Änderungen im Ordner
git commit -m "Startseite angelegt"   # Commit mit Nachricht
git log --oneline                # Verlauf anzeigen
```

Vor dem allerersten Commit sagst du Git, wer du bist:

```bash
git config --global user.name "Lena Muster"
git config --global user.email "lena@example.org"
```

---

## 4. `git status` lesen

```
On branch main
Changes to be committed:
        modified:   index.html          ← grün: in der Staging Area, kommt in den Commit

Changes not staged for commit:
        modified:   style.css           ← rot: geändert, aber noch nicht hinzugefügt

Untracked files:
        notizen.txt                     ← rot: neue Datei, Git kennt sie noch nicht
```

| Zustand        | Bedeutung                                 | Nächster Schritt             |
| -------------- | ----------------------------------------- | ---------------------------- |
| **untracked**  | neue Datei, noch nie hinzugefügt          | `git add datei`              |
| **modified**   | bekannte Datei wurde geändert             | `git add datei`              |
| **staged**     | vorgemerkt für den nächsten Commit        | `git commit -m "…"`          |
| **„nothing to commit, working tree clean“** | alles gespeichert | weiterarbeiten             |

---

## 5. Gute Commit-Nachrichten

Eine Commit-Nachricht erklärt **was** geändert wurde und **warum** – für dich in drei Monaten und für dein Team.

| ❌ Schlecht        | ✅ Gut                                             |
| ----------------- | ------------------------------------------------- |
| `fix`             | `Absturz beim Login ohne Passwort behoben`        |
| `änderungen`      | `Navigation für Handys als Klappmenü umgebaut`    |
| `asdf`            | `Preise im Warenkorb auf zwei Nachkommastellen runden` |
| `alles`           | (lieber mehrere kleine Commits mit je einem Thema) |

**Regeln:** kurz (ca. 50 Zeichen), aussagekräftig, ein Thema pro Commit, im Imperativ oder als Ergebnis formuliert („Fehler beheben“ / „Fehler behoben“ – Hauptsache einheitlich).

---

## 6. Branches

Ein **Branch** ist eine eigene Entwicklungslinie. Auf `main` liegt der funktionierende Stand. Neue Features entstehen auf eigenen Branches – so bleibt `main` stabil, und mehrere Personen kommen sich nicht in die Quere.

```
main:     A───B───────────E   (Merge)
               \         /
feature:        C───D───┘
```

```bash
git branch                       # alle Branches anzeigen (* = aktueller)
git branch feature-login         # neuen Branch anlegen
git switch feature-login         # zu einem Branch wechseln (älter: git checkout feature-login)
git switch -c feature-login      # anlegen und direkt wechseln (älter: git checkout -b …)
```

---

## 7. Merge – Branches zusammenführen

Ist das Feature fertig, holst du es nach `main`:

```bash
git switch main                  # zuerst auf den Ziel-Branch wechseln
git merge feature-login          # Änderungen von feature-login übernehmen
git branch -d feature-login      # erledigten Branch löschen
```

- Hat sich `main` inzwischen nicht verändert, „spult“ Git einfach vor (**Fast-Forward**).
- Sonst entsteht ein **Merge-Commit**, der beide Linien verbindet.

### Merge-Konflikte

Haben beide Branches **dieselbe Stelle** in einer Datei unterschiedlich geändert, kann Git nicht entscheiden. Es markiert die Stelle:

```
<<<<<<< HEAD
<h1>Willkommen!</h1>
=======
<h1>Herzlich willkommen</h1>
>>>>>>> feature-login
```

Du entscheidest, was richtig ist, entfernst die Markierungen, und schließt mit `git add datei` und `git commit` ab.

---

## 8. Remote: GitHub & Co.

Dein lokales Repo liegt nur auf deinem Rechner. Ein **Remote-Repository** auf einem Server (z. B. GitHub, GitLab, Codeberg) dient als gemeinsamer Treffpunkt fürs Team und als Sicherung.

```bash
git clone https://github.com/team/projekt.git   # Repo herunterladen (einmalig)
git pull                                         # neueste Änderungen holen
git push                                         # eigene Commits hochladen
git push -u origin feature-login                 # neuen Branch zum ersten Mal hochladen
```

> **Merksatz:** `pull` = herunter**ziehen**, `push` = hoch**schieben**. Vor dem Push immer erst pullen, sonst lehnt der Server ab, wenn jemand anderes schneller war.

---

## 9. Typischer Team-Workflow

1. `git pull` – mit dem neuesten Stand von `main` beginnen
2. `git switch -c feature-xyz` – eigenen Branch anlegen
3. Programmieren, dann `git add` + `git commit` (gerne mehrmals)
4. `git push -u origin feature-xyz` – Branch hochladen
5. **Pull Request** (bei GitLab: Merge Request) auf GitHub öffnen
6. Teammitglieder prüfen den Code (**Code Review**) und geben Feedback
7. Nach Freigabe wird der Branch in `main` gemergt
8. Alle holen sich den neuen Stand mit `git pull`

---

## 10. `.gitignore` – was nicht ins Repo gehört

In der Datei `.gitignore` stehen Muster für Dateien, die Git ignorieren soll:

```
# Passwörter und Schlüssel – niemals committen!
.env
*.key

# Automatisch erzeugte Dateien (lassen sich jederzeit neu erzeugen)
node_modules/
__pycache__/
*.log
build/

# Systemdateien
.DS_Store
Thumbs.db
```

**Gehört nicht ins Repo:** Passwörter, API-Schlüssel, persönliche Einstellungen, Abhängigkeiten (`node_modules`), Build-Ergebnisse, Logdateien, große Mediendateien.
**Gehört hinein:** Quellcode, Konfiguration ohne Geheimnisse, Dokumentation, die `.gitignore` selbst.

> ⚠️ Ein einmal gepushtes Passwort gilt als verraten – auch wenn du es später löschst, steht es noch im Verlauf. Sofort ändern!

---

## 11. Fehler rückgängig machen

| Situation                                        | Befehl                               |
| ------------------------------------------------ | ------------------------------------ |
| Änderung an einer Datei verwerfen (noch nicht gestaged) | `git restore datei`           |
| Datei wieder aus der Staging Area nehmen         | `git restore --staged datei`         |
| Unterschiede ansehen                             | `git diff`                           |
| Einen alten Commit rückgängig machen (sicher, neuer Commit) | `git revert <commit-id>`  |

---

## Spickzettel

| Befehl                     | Wirkung                              |
| -------------------------- | ------------------------------------ |
| `git init`                 | neues Repo anlegen                   |
| `git clone <url>`          | Repo herunterladen                   |
| `git status`               | aktuellen Zustand anzeigen           |
| `git add <datei>` / `.`    | für Commit vormerken                 |
| `git commit -m "…"`        | Schnappschuss speichern              |
| `git log --oneline`        | Verlauf anzeigen                     |
| `git branch <name>`        | Branch anlegen                       |
| `git switch <name>`        | Branch wechseln                      |
| `git merge <name>`         | Branch in den aktuellen holen        |
| `git pull` / `git push`    | mit dem Remote abgleichen            |

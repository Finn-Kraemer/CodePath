# Projektpflege

Was die Projektleitung (Repository-Admin) einrichtet und regelmäßig tut: Repository-Einstellungen, Pull Requests mergen, Dependabot, Sicherheitsmeldungen und Releases. Wie Beiträge entstehen, steht in [CONTRIBUTING.md](../CONTRIBUTING.md); die Technik der Workflows im [Entwicklerhandbuch](entwicklung.md#-prüfen-und-testen).

## ◼ Einmalig einrichten

Diese Schritte gehen nur mit Admin-Rechten im Browser (oder mit der GitHub-CLI) und stehen deshalb nicht im Code. Reihenfolge einhalten: Die Rulesets erst einspielen, wenn die Workflows auf `main` liegen und einmal gelaufen sind – sonst warten PRs auf Checks, die es noch nicht gibt.

### 1. Workflows auf `main` bringen

Den PR mit `.github/workflows/` mergen und unter **Actions** prüfen, dass `CI` und `CodeQL` auf `main` grün durchgelaufen sind.

### 2. Allgemeine Einstellungen

*Settings → General*

- **Pull Requests:** *Allow merge commits* und *Allow squash merging* an, *Allow rebase merging* aus.
- *Always suggest updating pull request branches* an.
- *Automatically delete head branches* an.
- *Allow auto-merge* an (PRs mergen sich selbst, sobald alles grün ist).

*Settings → Actions → General*

- **Workflow permissions:** *Read repository contents and packages permissions* (die Workflows fordern mehr Rechte selbst an, wo nötig).
- *Allow GitHub Actions to create and approve pull requests* aus.
- **Fork pull request workflows:** *Require approval for first-time contributors*.

### 3. Sicherheit

*Settings → Code security*

- *Private vulnerability reporting* an – damit funktioniert *Security → Report a vulnerability* (siehe [Sicherheit](sicherheit.md#-sicherheitslücke-melden)).
- *Dependency graph*, *Dependabot alerts* und *Dependabot security updates* an.
- *Secret Protection* bzw. *Secret scanning* mit *Push protection* an.
- **Code scanning:** Kein *Default setup* aktivieren – CodeQL läuft über `.github/workflows/codeql.yml` (*Advanced setup*). Beides gleichzeitig führt zu Fehlern.

### 4. Branch-Schutz (Rulesets)

Die Regeln liegen versioniert in `.github/rulesets/`:

| Datei | Schützt | Regeln |
|---|---|---|
| `main.json` | `main` | Nur per PR; kein Push, Force-Push oder Löschen; **CI erfolgreich** muss grün sein, Branch aktuell, Kommentare aufgelöst, keine CodeQL-Funde ab *hoch*. Keine Ausnahmen, auch nicht für Admins. |
| `release-tags.json` | Tags `v*` | Nur Admins dürfen sie anlegen, verschieben oder löschen (z. B. nach einem fehlgeschlagenen Release). |

Einspielen im Browser: *Settings → Rules → Rulesets → New ruleset → Import a ruleset*, Datei auswählen, *Create*. Oder mit der GitHub-CLI:

```bash
gh api -X POST repos/Finn-Kraemer/CodePath/rulesets --input .github/rulesets/main.json
```

```bash
gh api -X POST repos/Finn-Kraemer/CodePath/rulesets --input .github/rulesets/release-tags.json
```

Danach testen: Ein direkter Push auf `main` muss abgelehnt werden.

**Regel ändern:** Datei in `.github/rulesets/` per PR anpassen und danach in den Einstellungen übernehmen (Ruleset öffnen und ändern, oder `gh api -X PUT repos/Finn-Kraemer/CodePath/rulesets/<id> --input …`; die ID zeigt `gh api repos/Finn-Kraemer/CodePath/rulesets`). So bleibt nachvollziehbar, welche Regeln gelten.

**Weitere Mitarbeitende:** Sobald jemand außer dir Schreibrechte hat, in `main.json` `required_approving_review_count` auf `1` und `require_code_owner_review` auf `true` setzen und `.github/CODEOWNERS` ergänzen. Ohne zweite Person bleibt es bei `0` – eigene PRs kann man auf GitHub nicht selbst freigeben.

### 5. Container-Images

Nach dem ersten Release unter *Profil → Packages* bei `codepath` und `codepath-runner` die Sichtbarkeit auf **Public** stellen (sonst brauchen Schulen eine Anmeldung zum `docker pull`) und unter *Manage Actions access* das Repository mit *Write* eintragen.

## ◼ Laufender Betrieb

### Pull Requests mergen

1. Beschreibung lesen: Ist das Thema abgestimmt, die Vorlage ausgefüllt, `CHANGELOG.md` ergänzt?
2. Bei PRs aus Forks beim ersten Beitrag die Workflows mit *Approve and run* freigeben – vorher die Änderungen an `.github/` genau ansehen.
3. Code ansehen, Kommentare schreiben, auf Antworten warten.
4. Wenn **CI erfolgreich** grün ist: *Squash and merge* für kleine PRs mit vielen Zwischen-Commits, sonst *Create a merge commit*.

### Dependabot

Montags kommen gebündelte Update-PRs (Actions, Maven, npm, Docker). Sie laufen durch dieselbe CI.

- **Patch- und Minor-Updates** mit grüner CI: kurz ins verlinkte Changelog schauen, mergen.
- **Major-Updates** kommen einzeln: Release Notes lesen, lokal ausprobieren, bei Bedarf auf dem Dependabot-Branch nachbessern.
- **Actions** sind auf Commit-Hashes festgelegt; Dependabot aktualisiert Hash und Versionskommentar gemeinsam.
- Ein Update soll nicht kommen: im PR mit `@dependabot ignore this major version` (bzw. `minor`/`dependency`) antworten.

### Sicherheitsmeldungen

Unter **Security** landen CodeQL-Funde, Dependabot-Warnungen und vertrauliche Meldungen.

- **CodeQL ab *hoch*** blockiert Merges. Entweder beheben oder – wenn es ein Fehlalarm ist – den Fund mit Begründung schließen (*Dismiss alert → False positive / Used in tests*).
- **Gemeldete Lücke:** In der Meldung antworten, im privaten Fork (*Start a temporary private fork*) beheben, Release veröffentlichen, dann das Advisory veröffentlichen. Im Änderungsprotokoll unter **Sicherheit** eintragen.

## ◼ Release veröffentlichen

1. PR mit der Änderung in `CHANGELOG.md`: Abschnitt **Unveröffentlicht** in die neue Version umbenennen (z. B. `## 2.2.0`) und einen neuen, leeren **Unveröffentlicht**-Abschnitt darüber anlegen. Mergen.
2. Lokal `main` holen, taggen, pushen:

   ```bash
   git switch main && git pull && git tag v2.2.0 && git push origin v2.2.0
   ```

   **Oder im Browser:** *Releases → Draft a new release → Choose a tag*, `v2.2.0` eintippen und *Create new tag: v2.2.0 on publish* wählen, *Target* `main`, Titel und Text leer lassen, *Publish release*. Der Workflow startet genauso und ersetzt Titel und Text durch den Abschnitt aus dem Änderungsprotokoll. (*Save draft* legt keinen Tag an und startet nichts.)

3. Unter **Actions → Release** zusehen. Der Workflow prüft, dass der Tag auf `main` liegt und das Änderungsprotokoll einen Abschnitt zur Version hat, lässt die komplette CI laufen, baut die Images und legt das GitHub-Release an.

**Versionsnummer** nach [Semantic Versioning](https://semver.org/lang/de/): *Patch* (`2.2.1`) für Fehlerbehebungen, *Minor* (`2.3.0`) für neue Funktionen, *Major* (`3.0.0`) wenn Schulen beim Update mehr tun müssen als `docker compose pull`.

**Vorabversion zum Testen:** `v2.3.0-rc.1` taggen. Sie braucht keinen eigenen Abschnitt im Änderungsprotokoll, bekommt kein `latest` und erscheint als Pre-Release.

**Release schlägt fehl:**

| Meldung | Abhilfe |
|---|---|
| *Tag zeigt auf einen Commit, der nicht in main ist* | Tag löschen (nur Admins, siehe Ruleset): `git push --delete origin v2.2.0 && git tag -d v2.2.0`, auf dem richtigen Commit neu taggen |
| *CHANGELOG.md hat keinen Abschnitt* | Abschnitt per PR ergänzen, Tag wie oben löschen und neu setzen |
| CI rot | Fehler per PR beheben, Tag löschen und auf dem neuen Commit neu setzen |
| Images gebaut, Release fehlt | Job *GitHub-Release* mit *Re-run failed jobs* wiederholen |

Ein bereits veröffentlichtes Image wird nie überschrieben – stattdessen eine neue Patch-Version herausgeben.

## ◼ Notfall

Muss eine Änderung sofort auf `main`, ohne auf die CI zu warten (z. B. die CI selbst ist kaputt): Unter *Settings → Rules → Rulesets → main schützen* die *Enforcement* kurz auf **Disabled** stellen, die Änderung pushen, sofort wieder auf **Active** stellen. Das wird im Audit-Log festgehalten – danach im PR oder Issue begründen, warum.

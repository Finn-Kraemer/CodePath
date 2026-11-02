# Mitwirken an CodePath

Danke, dass du CodePath verbessern willst! Diese Anleitung beschreibt, wie ein Beitrag vom ersten Gedanken bis in `main` kommt. Technische Details (Architektur, Tests, neue Aufgabentypen) stehen im [Entwicklerhandbuch](docs/entwicklung.md).

## ◼ Kurz und knapp

1. Bei größeren Änderungen zuerst ein **Issue** anlegen und die Idee abstimmen.
2. Repository **forken** (oder als Mitglied einen Branch anlegen) – direkt auf `main` kann niemand pushen.
3. Änderung auf einem eigenen **Branch** umsetzen, lokal **prüfen** (siehe unten).
4. **Pull Request** gegen `main` öffnen und die Vorlage ausfüllen.
5. Warten, bis der Check **CI erfolgreich** grün ist, Review-Kommentare beantworten.
6. Die Projektleitung merged den PR.

## ◼ Bevor du anfängst

- **Fehler gefunden?** Issue mit Schritten zum Nachstellen, erwartetem und tatsächlichem Verhalten, Version (`CODEPATH_VERSION` bzw. Commit) und Browser.
- **Idee für eine Funktion oder neue Aufgaben?** Issue mit dem Anwendungsfall im Unterricht – *wer* braucht es *wofür*?
- **Sicherheitslücke?** Bitte **kein** öffentliches Issue. Vertraulich melden über *Security → Report a vulnerability* (siehe [Sicherheit](docs/sicherheit.md#-sicherheitslücke-melden)).
- Kleine Korrekturen (Tippfehler, Doku, offensichtliche Fehler) dürfen direkt als PR kommen.

## ◼ Lokale Umgebung

Voraussetzungen: Java 25, Node.js 22 mit `corepack enable` (für pnpm), Python 3.12, Docker.

```bash
cd backend/dev-stack && ./start.sh                                  # PostgreSQL
cd backend && SPRING_DATASOURCE_PASSWORD=codepath ./mvnw spring-boot:run
cd frontend && pnpm install && pnpm dev                             # http://localhost:5173
```

Mehr dazu unter [Lokale Umgebung](docs/entwicklung.md#-lokale-umgebung).

## ◼ Branches und Commits

- Branch-Namen beschreiben den Inhalt: `fix/rangliste-sortierung`, `feature/neuer-aufgabentyp`, `docs/betrieb-backup`, `content/modul-netzwerke`.
- Ein PR behandelt **ein** Thema. Lieber zwei kleine PRs als einen großen.
- Commit-Nachrichten auf Deutsch, im Imperativ und aussagekräftig: *„Rangliste nach Punkten und Zeit sortieren“* statt *„fix“*.
- Den Branch aktuell halten: Ist `main` weitergelaufen, `main` in den Branch mergen oder rebasen – GitHub verlangt vor dem Merge den aktuellen Stand.

## ◼ Vor dem Pull Request prüfen

Dieselben Prüfungen wie in der CI – so gibt es keine Überraschungen:

| Bereich | Befehl |
|---|---|
| Backend | `cd backend && ./mvnw verify` |
| Frontend | `cd frontend && pnpm lint && pnpm check && pnpm test` |
| Inhalte | `python content/validate.py` |
| Code-Runner | `cd runner && python -m unittest test_runner` |

Außerdem:

- **API geändert?** `./mvnw verify` schreibt `frontend/src/lib/api/openapi.json` neu, danach `pnpm gen:api`. Beide Dateien mit einchecken – die CI prüft das.
- **Sichtbare Änderung?** Eintrag in [`CHANGELOG.md`](CHANGELOG.md) unter **Unveröffentlicht** (*Neu*, *Verbessert*, *Geändert* oder *Sicherheit*).
- **Neue Migration?** Zusätzlich ein Hinweis unter **Update** im Änderungsprotokoll, was beim Update passiert.
- **Formatierung:** `pnpm format` im Frontend.

## ◼ Was im Pull Request passiert

Beim Öffnen des PRs laufen automatisch:

| Check | Was er prüft |
|---|---|
| Backend | Tests gegen PostgreSQL inkl. echtem Code-Runner, OpenAPI aktuell |
| Frontend | Lint, Typcheck, Tests, Build, API-Typen aktuell |
| Content-Pakete | Schema-Validierung aller Aufgaben |
| Code-Runner | Angriffe auf die Sandbox im gehärteten Container |
| Docker-Image + End-to-End | Fertiges Image mit Playwright durchgetestet |
| Workflows | `actionlint` für `.github/workflows/` |
| Abhängigkeiten | Neue Pakete ohne bekannte Lücken ab „hoch“ |
| CodeQL | Statische Sicherheitsanalyse |

Der Check **CI erfolgreich** fasst alle zusammen. Ein PR kann erst gemergt werden, wenn

- **CI erfolgreich** grün ist,
- CodeQL keine neuen Funde ab *hoch* meldet,
- der Branch auf dem Stand von `main` ist und
- alle Review-Kommentare aufgelöst sind.

**Ein Check ist rot?** Auf *Details* klicken. Bei Backend-Tests und End-to-End-Tests liegen unter *Summary → Artifacts* die Testberichte bzw. Playwright-Traces (`npx playwright show-trace <datei>.zip`).

> Hinweis für Forks: Bei PRs aus einem Fork muss die Projektleitung die Workflows beim ersten Beitrag einmal freigeben.

## ◼ Inhalte beitragen

Module und Aufgaben liegen als Content-Pakete in `content/`. Format und Beispiele: [Content-Format](content/README.md), Übersicht der Typen: [Aufgabentypen](docs/aufgabentypen.md). Vollständige Lösungen von Programmieraufgaben gehören nicht in Wiki-Artikel.

## ◼ Verhalten

Freundlich, sachlich, geduldig – CodePath ist ein Projekt für Schulen, und so gehen wir auch miteinander um.

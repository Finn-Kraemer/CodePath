# Phase 4 – Code-Runner

Plan: Spike mit nsjail, danach Testfälle für CODE und FILL_CODE – oder Code-Aufgaben ohne Punkte (ADR-4).

**Ergebnis:** Der Spike ist bestanden, mit **bubblewrap statt nsjail** (Begründung in ADR-4). Code-Aufgaben werden serverseitig gegen Testfälle bewertet, wenn der optionale dritte Container `runner` läuft. Ohne ihn bleiben sie Übungen ohne Punkte – der Stack braucht weiterhin nur zwei Container.

## Spike

`runner/test_runner.py` greift die Sandbox an; alle Angriffe sind abgewehrt, auf dem Host und im gehärteten Container:

| Angriff | Ergebnis |
|---|---|
| Netzwerk (`socket.create_connection`) | kein Netz (eigener Network-Namespace) |
| `/etc/passwd`, App-Verzeichnis, Umgebungsvariablen (Token) | nicht vorhanden |
| Schreiben nach `/usr` | schreibgeschützt |
| Fork-Bombe | `RLIMIT_NPROC` (32 je Lauf, eigene Benutzer-ID je Platz) |
| `'a' * 10**9` | `MemoryError` (256 MB Adressraum) |
| Endlosschleife | nach 2 s CPU beendet → TIMEOUT |
| `time.sleep(30)` | nach 5 s Wandzeit beendet → TIMEOUT |
| Ausgabeflut | nach 64 KB abgebrochen |
| Riesendatei in `/tmp` | `RLIMIT_FSIZE` (1 MB) |
| Prozess lässt sich nicht beenden | Start schlägt fehl (Selbsttest beim Start) |

## Umgesetzt

| Thema | Umsetzung |
|---|---|
| Runner | `runner/runner.py`, nur Python-Standardbibliothek. `POST /run` mit Bearer-Token, `GET /health` mit Selbsttest (rechnet, sieht kein `/etc/passwd`, kein Netz). Höchstens 4 Läufe parallel, jeder unter eigener Benutzer-ID; danach Warteschlange (10 s), sonst 503. Abgaben landen nicht im Log. |
| Container | `runner/Dockerfile` (python:3.13-slim + bubblewrap). In `docker-compose.yml` als Profil `runner`: schreibgeschütztes Dateisystem, alle Capabilities entfernt außer SETUID/SETGID/KILL, eigenes internes Netz ohne Weg nach draußen, Pids-, Speicher- und CPU-Grenze. |
| Backend | `HttpCodeRunner` (aktiv mit `CODEPATH_RUNNER_URL` und `CODEPATH_RUNNER_TOKEN`). Health wird 30 s zwischengespeichert. Ist der Runner weg, wird die Abgabe gespeichert, aber nicht bewertet (UNGRADED) – nie als falsch. |
| Testfälle | `{stdin?, append?, expectedOutput, hidden?}`. `append` hängt Code an die Abgabe, z. B. weitere Aufrufe der Funktion mit anderen Werten; so fällt fest einprogrammierte Ausgabe auf. Versteckte Tests verraten weder Eingabe noch Erwartung. |
| Inhalte | Alle 8 CODE-Aufgaben haben eine Musterlösung, den sichtbaren Test und versteckte Tests; die erwarteten Ausgaben erzeugt `content/tools/code_tasks.py` durch Ausführen der Musterlösung. Repariert: s10 (Startcode hatte einen Syntaxfehler und keine Lücke, erwartete Ausgabe war falsch). CODE ohne Musterlösung besteht die Prüfung nicht mehr. |
| Editor | Feld „Angehängter Code“ je Testfall und „Ausgaben aus Musterlösung erzeugen“ (läuft im Runner). Der Probelauf vor dem Veröffentlichen führt die Musterlösung gegen alle Tests aus. |
| FILL_CODE | Wie bisher nach Lücken bewertet; mit Testfällen und Runner zählen auch andere Lösungen, die dieselbe Ausgabe erzeugen. |

## Einschalten

In `.env`:

```bash
COMPOSE_PROFILES=runner
CODEPATH_RUNNER_URL=http://runner:8000
RUNNER_TOKEN=$(openssl rand -hex 32)   # den erzeugten Wert eintragen
```

Danach `docker compose up -d`. In **Admin → Inhalte** zeigt eine Code-Aufgabe dann „Abgeben“ statt „Speichern“.

bubblewrap braucht *user namespaces*. Dockers Standard-Seccomp- und AppArmor-Profile verbieten sie, deshalb läuft der Runner mit `seccomp=unconfined` und `apparmor=unconfined`; die Grenze bildet bubblewrap selbst, dazu fehlen dem Container alle anderen Capabilities und jedes Netz nach draußen. **Ubuntu ab 24.04** sperrt user namespaces zusätzlich für unprivilegierte Prozesse:

```bash
echo 'kernel.apparmor_restrict_unprivileged_userns=0' | sudo tee /etc/sysctl.d/60-codepath-runner.conf
sudo sysctl --system
```

Startet der Runner nicht, steht der Grund im Log (`docker compose logs runner`); die App läuft dann ohne Bewertung von Code weiter.

## Tests

- `runner/test_runner.py`: die Angriffe oben; in der CI im Container mit denselben Sicherheitsoptionen wie in Compose (Job „Code-Runner“).
- `HttpCodeRunnerTest`: Protokoll, Token, Fehler → UNAVAILABLE; mit `CODEPATH_RUNNER_URL` auch gegen den echten Runner.
- `SecurityCatalogTest`: fest einprogrammierte Ausgabe ergibt höchstens PARTIAL, Netzzugriff scheitert, Musterlösung gibt Punkte; nur Lehrkräfte dürfen Code im Editor ausführen.
- `ContentPackagesTest`: mit Runner laufen alle Musterlösungen gegen ihre Tests. Die CI startet dafür den Runner direkt auf dem Build-Rechner.
- E2E-Job: Stack mit Profil `runner`.

## Offen

- Das Runner-Image ist in dieser Umgebung nicht gebaut worden (kein Paketzugriff im Docker-Build); getestet wurde derselbe Code in `python:3.13-slim` mit bubblewrap aus dem Host. Der CI-Job baut das echte Image.
- Ein eigenes Seccomp-Profil statt `unconfined` würde die Angriffsfläche des Kernels weiter verkleinern.

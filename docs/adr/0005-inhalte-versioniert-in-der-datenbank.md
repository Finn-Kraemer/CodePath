# ADR-5: Inhalte versioniert in der Datenbank, Content-Pakete für Import und Export

**Status:** Akzeptiert, umgesetzt in Phase 2 und 3

**Kontext:** Inhalte sollen ohne Code-Änderung gepflegt werden; heute liegen sie als JSON im Backend-JAR.

**Entscheidung:** Die Datenbank ist die Quelle der Wahrheit; jede Veröffentlichung erzeugt eine unveränderliche Version. Content-Pakete (JSON + Markdown, mit Schema) dienen als Startinhalt, Backup und Austausch.

**Alternativen:** Nur Dateien im Git-Repo (gute Historie, aber jede Änderung braucht ein Deployment); nur Datenbank ohne Pakete (kein Austausch, keine Prüfung in CI).

**Konsequenzen:** Ein Editor muss gebaut werden; dafür können Inhalte während eines Kurses korrigiert werden, ohne alte Ergebnisse zu verfälschen.

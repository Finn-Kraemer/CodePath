# Sicherheit und Datenschutz

CodePath verarbeitet Daten von Minderjährigen. Die Plattform ist deshalb so gebaut, dass möglichst wenig anfällt, alles auf dem eigenen Server bleibt und am Kursende vollständig gelöscht wird. Diese Seite fasst zusammen, was gespeichert wird und wie es geschützt ist – als Grundlage für die Abstimmung mit Schulleitung und Datenschutzbeauftragten.

## ◼ Welche Daten anfallen

| Daten | Zweck | Gelöscht |
|---|---|---|
| Benutzername, Anzeigename, Passwort (nur als BCrypt-Hash) | Anmeldung | beim Beenden des Kurses |
| Kurszugehörigkeit | Freigaben, Rangliste je Kurs | beim Beenden des Kurses |
| Fortschritt, Versuche mit Antworten, Punkte | Bewertung, Rückmeldung, Lernanalyse | beim Beenden des Kurses |
| Praxisabgaben, Rubrik-Bewertung, Kommentar der Lehrkraft | Bewertung offener Aufgaben | beim Beenden des Kurses |
| Feedback der Klasse | Rückmeldung zum Kurs | beim Beenden des Kurses |
| Quiz-Antworten (gewählte Option je Frage) | Anonyme Verteilung am Beamer | beim Beenden des Kurses (mit dem Konto) |
| Peer-Bewertungen (Punkte je Kriterium, Kommentar, wer wen bewertet hat) | Rückmeldung unter Mitschülern, Hilfe für die Lehrkraft | beim Beenden des Kurses (mit dem Konto) |
| Hilfe-Rufe „Ich hänge fest“: Aufgabe, Zeitpunkte, Kategorie, Notiz, Nachricht der Lehrkraft, mitgeschickter Entwurf und letzte Ausgabe (je höchstens 20 KB) | Lehrkraft weiß, wer wobei Hilfe braucht | Entwurf und Ausgabe 24 Stunden nach dem Erledigen, alles beim Beenden des Kurses |
| Sitzung (Session) | angemeldet bleiben | nach 12 Stunden Inaktivität oder beim Abmelden |
| Audit-Log (wer hat was wann getan, ohne Inhalte) | Nachvollziehbarkeit von Lehrkraft-Aktionen | bleibt bestehen |

**Hilfe-Rufe** sehen nur Lehrkräfte – nicht Mitschüler, nicht die Rangliste, nicht der Beamer. Live-Updates dazu gehen nur an Verbindungen von Lehrkräften und an die betroffene Person (die nur ihren eigenen Ruf und die Zahl der Rufe vor ihr sieht). Gerufen werden kann nur für sichtbare Aufgaben des eigenen Kurses, höchstens alle 30 Sekunden. Der Entwurf wird nur beim Klick auf „Hilfe rufen“ bzw. „Stand senden“ übertragen, nie automatisch, und zählt nicht als Versuch.

**Quiz-Runden** zeigen am Beamer und den Schülern nur Zahlen je Antwort, nie Namen; Lösung und Verteilung bekommen Schüler erst bei der Auflösung. **Peer-Review** ist anonym: Wer bewertet und wer bewertet wird, sieht nur die Lehrkraft. Zugeteilt werden nur Abgaben aus dem eigenen Kurs, und erst nach der eigenen Abgabe.

Der **Code-Visualizer** („Schritt für Schritt“) läuft vollständig im Browser; Aufzeichnungen verlassen das Gerät nicht.

**Nicht** erhoben werden: echte Namen (der Benutzername ist frei wählbar oder wird erzeugt), E-Mail-Adressen, Geräte- oder Standortdaten, Tracking. CodePath lädt nach der Installation nichts von fremden Servern nach – auch keine Schriften, Bilder oder Skripte. Markdown in Aufgaben zeigt nur Bilder aus der eigenen Ablage.

**Kurs beenden** löscht alle Schülerkonten des Kurses mit sämtlichen zugehörigen Daten in einem Schritt. Teilnahmebestätigungen vorher herunterladen. Ein **archivierter** Kurs ist für Schülerinnen und Schüler gesperrt (keine Anmeldung, laufende Sitzungen enden); nach der beim Archivieren gewählten Frist (1 bis 24 Monate, Standard 6) löscht ein stündlicher Job die Daten automatisch wie beim Beenden. Beim **Übernehmen** in einen neuen Kurs bleiben die Konten bestehen; die Löschung erfolgt dann mit dem Ende des neuen Kurses.

Wird CodePath für andere Schulen betrieben, ist ein Vertrag zur Auftragsverarbeitung (Art. 28 DSGVO) nötig; für mehrere Schulen empfiehlt sich je Schule eine eigene Instanz (siehe [Betriebshandbuch](betrieb.md#-mehrere-schulen-und-skalierung)).

## ◼ Sicherheitsmodell

### Anmeldung und Sitzungen

- Serverseitige Sitzungen in PostgreSQL, Cookie `CODEPATH_SESSION` mit `HttpOnly`, `SameSite=Lax` und – hinter HTTPS – `Secure` ([ADR-3](adr/0003-serverseitige-sessions-statt-jwt.md)).
- CSRF-Schutz für alle ändernden Anfragen (Token im Cookie `XSRF-TOKEN`, Header `X-XSRF-TOKEN`). Einzige Ausnahme: die Übungsseiten des Browser-Labors, die keine echten Daten ändern.
- Login-Bremse: höchstens 10 Fehlversuche pro Minute je Benutzername und 100 je IP-Adresse (Schulnetze hinter einer gemeinsamen Adresse).
- Registrierung nur mit gültigem Beitrittscode eines aktiven Kurses.
- Keine Standardpasswörter: Datenbankpasswort ist Pflicht, das Admin-Passwort wird gesetzt oder zufällig erzeugt.

### Rollen

| Rolle | Darf |
|---|---|
| Schülerin, Schüler | freigeschaltete Module bearbeiten, eigene Ergebnisse sehen, Rangliste des eigenen Kurses |
| Lehrkraft (`admin`) | alles unter `/api/admin`: Kurse, Konten, Freigaben, Bewertung, Inhalte, Bilder |

### Faire Bewertung

- Bewertet wird nur auf dem Server. Der Browser bekommt keine Lösungen, keine Testfälle mit Erwartungswerten und keine Hinweise, bevor sie angefordert werden; ein Testkatalog prüft das für jede mitgelieferte Aufgabe.
- Zufällige Werte (Zahlensysteme) und Codes (Browser-Labor) sind je Person verschieden; Abschreiben hilft nicht.
- Punkte gibt es pro Aufgabe genau einmal – abgesichert durch die Datenbank, auch bei gleichzeitigen Abgaben.
- Versuchsgrenzen verhindern Durchprobieren.

### Ausführen von Code

| Wo | Schutz |
|---|---|
| **Python im Browser** (Pyodide) | läuft in einem Web Worker mit Zeitlimit; dient nur zum Ausprobieren |
| **Python auf dem Server** (Code-Runner, optional) | bubblewrap-Sandbox ohne Netz und ohne Dateisystem, Grenzen für CPU, Speicher, Prozesse und Ausgabe, eigener Container ohne Capabilities in einem internen Netz ([ADR-4](adr/0004-python-sandbox.md), Angriffstests in `runner/test_runner.py`) |
| **SQL-Aufgaben** | eigene In-Memory-Datenbank je Abgabe, nur ein `SELECT`, Benutzer ohne Admin-Rechte, nur lesend, Zeit- und Zeilenlimit |
| **Terminal-Aufgaben** | reine Simulation – es wird kein echter Befehl ausgeführt |

### Inhalte und Uploads

- Texte sind Markdown und werden vor der Anzeige mit DOMPurify bereinigt; Skripte und Event-Handler werden entfernt.
- Bilder: nur PNG, JPEG, GIF und WebP bis 2 MB, erkannt am Inhalt; SVG ist ausgeschlossen. Ausgeliefert mit `Content-Security-Policy: default-src 'none'; sandbox` und `nosniff` ([ADR-7](adr/0007-medien-in-der-datenbank.md)).
- Die Übungsseiten des Browser-Labors kommen aus festen Vorlagen; über den Editor lässt sich kein HTML einschleusen. Sie laufen mit strenger CSP und sind nicht einbettbar.

### Nachvollziehbarkeit

Das Audit-Log hält fest, wer Inhalte veröffentlicht, archiviert oder gelöscht, Bilder hochgeladen, Passwörter überschrieben oder Kurse beendet hat.

## ◼ Sicherheitslücke melden

Bitte keine öffentlichen Issues für Sicherheitslücken. Stattdessen vertraulich an die Maintainer wenden – auf GitHub über *Report a vulnerability* im Reiter **Security**, sofern aktiviert.

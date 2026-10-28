# Änderungsprotokoll

Alle nennenswerten Änderungen an CodePath. Versionen folgen [Semantic Versioning](https://semver.org/lang/de/); Images gibt es unter `ghcr.io/finn-kraemer/codepath` und `ghcr.io/finn-kraemer/codepath-runner`.

Vor jedem Update: Backup anlegen (`./deploy/backup.sh`) und die Hinweise unter **Update** lesen.

## Unveröffentlicht

### Neu
- **Eigene Seiten** (Leitstand → Seiten): neue Seiten unter `/seiten/…` mit dem Abschnitts-Editor der Startseite, öffentlich oder nur für Angemeldete, im Aufklappmenü „Seiten“ und in der Übersicht `/seiten`, einzelne zusätzlich direkt in der Menüleiste.
- **Wissensdatenbank im Browser bearbeiten** (Leitstand → Wissensdatenbank): Artikel anlegen, in Markdown mit Vorschau und Bildern bearbeiten, sortieren, an ein Modul koppeln, als Entwurf zurückhalten oder löschen.
- **Startseite bearbeiten** (Leitstand → Startseite): Abschnitte im Browser hinzufügen, umsortieren, ausblenden und Texte ändern, mit Live-Vorschau, Entwurf und Veröffentlichen – ohne neuen Build.
- **Browser-Labor** (`DEVTOOLS`): Übungsseiten im eigenen Tab, die mit Konsole, Inspektor, Netzwerkanalyse und Web-Speicher untersucht werden; Code je Person verschieden. Szenarien *Wetterstation* und *Schul-Shop* in Web-Entwicklung.
- **Modul Abschlusstest** mit dem *Escape-Zimmer* – vier Schlösser, eines je Werkzeug, ohne Anleitung.
- **Aufgaben endgültig löschen**: nur archivierte Aufgaben, mit Anzeige der Folgen und Bestätigung per Slug. Gelöschte mitgelieferte Aufgaben kehren bei Updates nicht zurück.
- **Einzelne Aufgaben freischalten oder sperren** je Kurs, unabhängig vom Modul. Aufgabenzahl, Punkte, Fortschritt und Teilnahmebestätigung zählen nur die sichtbaren Aufgaben.
- **Aufgaben umsortieren** im Editor per Ziehen oder ▲▼; Importe überschreiben die Reihenfolge nicht mehr.
- **Python-Werkbank für Praxisaufgaben**: Editor mit Eingaben und Ausführen im Browser (10 s), Code wird mit abgegeben und lässt sich in der Bewertung ausführen. Aktiv in *Dein erstes vollständiges Programm*.
- Neue Dokumentation: Handbücher für Lehrkräfte, Betrieb, Sicherheit und Entwicklung, Übersicht der Aufgabentypen.

### Verbessert
- Live-Updates werden auf einem eigenen Thread gesendet; ein langsamer Client hält keine Abgabe mehr auf, gestaute Ranglisten-Updates werden zusammengefasst.

### Update
- Migration V18 ergänzt eine Kurzbeschreibung für eigene Seiten.
- Migration V17 legt Tabellen für eigene Seiten und die Wissensdatenbank an. Die bisher fest eingebauten Wissensartikel werden beim Start einmalig übernommen (`content/wissen/`); Sichtbarkeit wie bisher (Grundlagen immer, sonst mit dem gleichnamigen Modul).
- Migration V16 entfernt die nie genutzte Einstellung `frontpage_config`. Solange nichts veröffentlicht ist, zeigt die Startseite den bisherigen Inhalt.
- Migration V14 verschiebt den Abschlusstest (falls vorhanden) aus *Web-Entwicklung* in das neue Modul *Abschlusstest*, samt Versuchen und Punkten. Das Modul ist danach für alle Kurse gesperrt, bis die Lehrkraft es freischaltet.
- Neue mitgelieferte Aufgaben legt der Start automatisch an.
- Migration V15 legt die Tabelle für Freigaben einzelner Aufgaben an; bestehende Freigaben bleiben unverändert.

## 2.0.0

Komplette Überarbeitung. Berichte zu den einzelnen Phasen: [docs/historie/](docs/historie/).

### Neu
- **Kurse mit Beitrittscode**, Freigaben und Fristen je Kurs, gesammelte Kontoerstellung; *Kurs beenden* löscht alle Schülerdaten.
- **Aufgaben-Engine**: Bewertung ausschließlich auf dem Server, jeder Versuch gespeichert, einheitliche Regeln für Versuche, gestufte Hinweise und Bonus, Punkte-Journal mit „genau einmal Punkte“.
- **Editor** mit Vorschau, Probelauf, Versionen, Export und Import als Content-Paket; Inhalte als Content-Pakete mit JSON-Schema; alle 9 Module durchgesehen, jede Aufgabe mit Lernziel.
- **Code-Runner** (optional): Python-Abgaben in einer bubblewrap-Sandbox gegen sichtbare und versteckte Tests.
- **Neue Aufgabentypen**: Zuordnen, Code-Puzzle, Zahlensysteme, Markieren, Im Bild markieren, Logikgatter.
- **Bilder** für Aufgaben: Upload im Editor, Speicherung in der Datenbank, Bilder in Content-Paketen.
- **Rubrik-Bewertung** für Praxisaufgaben, Audit-Log, Lernanalyse je Aufgabe.

### Geändert
- Java 25 und Spring Boot 4.1; Frontend im JAR – **zwei Container** statt vier.
- Serverseitige Sessions statt JWT, Server-Sent Events statt MQTT, RabbitMQ entfernt.
- Monaco, Pyodide und Schriften lokal gebündelt: nach der Installation offline.

### Sicherheit
- Keine Standardpasswörter mehr; Lösungen und Hinweise gehen nicht mehr an den Browser; SQL-Sandbox abgesichert; Login-Bremse gegen Passwort-Raten.

### Update von 1.x
- Bestehende Konten, Fortschritte, Punkte und Praxisabgaben werden in einen Kurs *Bestandskurs* übernommen (Migration V12). Vorher unbedingt ein Backup anlegen.
- `POSTGRES_PASSWORD` in `.env` ist jetzt Pflicht; das alte Admin-Passwort bleibt erhalten.

## 1.x

Ursprüngliche Version mit vier Containern (Frontend mit nginx, Backend, PostgreSQL, RabbitMQ) und JWT-Anmeldung.

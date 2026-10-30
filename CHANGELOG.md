# Änderungsprotokoll

Alle nennenswerten Änderungen an CodePath. Versionen folgen [Semantic Versioning](https://semver.org/lang/de/); Images gibt es unter `ghcr.io/finn-kraemer/codepath` und `ghcr.io/finn-kraemer/codepath-runner`.

Vor jedem Update: Backup anlegen (`./deploy/backup.sh`) und die Hinweise unter **Update** lesen.

## Unveröffentlicht

### Neu
- **Verbindliche Lernpfade** (#21): Lernpfade lassen sich als *verbindlich* einstellen – Pflicht-Verbindungen (durchgezogen) sperren nachfolgende Stationen auf dem Server, bis ihre Vorgänger erledigt sind; Empfehlungs-Verbindungen (gestrichelt) bleiben sofort bearbeitbar. Übrige Module außerhalb des Pfads sind wahlweise frei, gesperrt oder erst ab einer bestimmten Station (z. B. Meilenstein „Freies Arbeiten“) automatisch freigeschaltet. Vorschau im Editor auch aus Sicht einzelner Schülerkonten.
- **Modul „Die Kommandozeile“** (14 Aufgaben): Als Azubi im IT-Team der Stadtwerke orientiert man sich im Ordnerbaum, räumt auf, liest und durchsucht Logs mit `grep`, `find`, `head`/`tail` und Pipes und behebt am Ende einen Server-Notfall ohne Anleitung.
- **Git zum Anfassen**: Das Git-Modul ist neu aufgebaut (16 Aufgaben) – Repository anlegen, committen, im Verlauf den Verursacher eines Fehlers finden, Änderungen zurückholen, `.gitignore`, Branches, Merge, Merge-Konflikt, `pull`/`push` gegen einen simulierten Server und eine Release-Mission. Ein Commit-Graph unter dem Terminal zeigt alles live.
- **Terminal-Aufgaben** können mehr: neue Befehle (`grep`, `find`, `head`, `tail`, `wc`, `sort`, `uniq`, `history`, `man`, `ls -a`), Pipes und `>`/`>>` für alle Befehle, simuliertes Git, Antwortfeld, Prüfungen auf den Zustand des Repositorys, „muss weg sein“, Varianten und Codes je Person. Im Browser: Verlauf mit ↑/↓, Tab-Vervollständigung, Spickzettel.
- **Mitnehm-Paket**: Schülerinnen und Schüler laden vor dem Kursende im Profil ein PDF mit ihren Ergebnissen, ihrem Code, ihrem Berufe-Kompass und Links zum Weiterlernen herunter. Es entsteht beim Abruf und wird auf dem Server nicht gespeichert.
- **Berufe-Kompass**: Jedes Modul hat ein Berufsfeld; der Kompass im Profil zeigt, in welche Richtung die eigenen Ergebnisse weisen. Berufsfeld und Weiterlern-Links lassen sich unter *Inhalte → Modul bearbeiten* pflegen.
- **Lernpfade und empfohlene nächste Aufgabe**: Die Lehrkraft zeichnet im Editor (Leitstand → Lernpfade) eine Karte aus Modulen, Aufgaben und Meilensteinen. Die Klasse sieht sie mit ihrem Stand in der Modulübersicht und bekommt dort und nach jeder gelösten Aufgabe die empfohlene nächste Aufgabe.
- **Lernpfade je Kurs mit Aufgabenauswahl**: Modul-Stationen nutzen alle oder nur bestimmte Aufgaben, ein Modul lässt sich auf mehrere Stationen verteilen (Programmierung 1, Web 1, Programmierung 2). Ein Lernpfad für einen Kurs übernimmt seine Module und Aufgaben beim Speichern in die Modul-Steuerung des Kurses.
- **Zentraler Import & Export** (Leitstand → Import & Export): Einstellungen, Startseite, Seiten, Wissensdatenbank, Lernpfade sowie Module und Aufgaben mit Bildern in einer Datei. Der Import erkennt die Datei selbst und berichtet je Eintrag: angelegt, überarbeitet, unverändert oder Fehler.
- **Eigene Buttons** in „Titelbereich“ und „Aufruf mit Buttons“ (Startseite und eigene Seiten): Beschriftung und Ziel frei wählbar – interne Seite aus einer Liste (Lernmodule, Wissensdatenbank, eigene Seiten …) oder externe Adresse, optional im neuen Tab. Die Standard-Buttons (Login/Registrieren bzw. „Zu den Lernmodulen“) lassen sich weiterhin zu- oder abschalten.
- **Suche in der Wissensdatenbank**: Volltext über Titel und Text, Treffer mit Ausschnitt, Fundstellen im Artikel markiert.
- **Artikel verlinken**: Aufgaben zeigen „Mehr dazu im Wiki“ (frei wählbarer Artikel, sonst der zum Modul); im Wiki-Editor lassen sich andere Artikel per Auswahl verlinken.
- **Eigene Seiten** (Leitstand → Seiten): neue Seiten unter `/seiten/…` mit dem Abschnitts-Editor der Startseite, öffentlich oder nur für Angemeldete, im Aufklappmenü „Seiten“ und in der Übersicht `/seiten`, einzelne zusätzlich direkt in der Menüleiste.
- **Wissensdatenbank im Browser bearbeiten** (Leitstand → Wissensdatenbank): Artikel anlegen, in Markdown mit Vorschau und Bildern bearbeiten, sortieren, an ein Modul koppeln, als Entwurf zurückhalten oder löschen.
- **Startseite bearbeiten** (Leitstand → Startseite): Abschnitte im Browser hinzufügen, umsortieren, ausblenden und Texte ändern, mit Live-Vorschau, Entwurf und Veröffentlichen – ohne neuen Build.
- **Browser-Labor** (`DEVTOOLS`): Übungsseiten im eigenen Tab, die mit Konsole, Inspektor, Netzwerkanalyse und Web-Speicher untersucht werden; Code je Person verschieden. Szenarien *Wetterstation* und *Schul-Shop* in Web-Entwicklung.
- **Modul Abschlusstest** mit dem *Escape-Zimmer* – vier Schlösser, eines je Werkzeug, ohne Anleitung.
- **Aufgaben endgültig löschen**: nur archivierte Aufgaben, mit Anzeige der Folgen und Bestätigung per Slug. Gelöschte mitgelieferte Aufgaben kehren bei Updates nicht zurück.
- **Einzelne Aufgaben freischalten oder sperren** je Kurs, unabhängig vom Modul. Aufgabenzahl, Punkte, Fortschritt und Teilnahmebestätigung zählen nur die sichtbaren Aufgaben.
- **Aufgaben umsortieren** im Editor per Ziehen oder ▲▼; Importe überschreiben die Reihenfolge nicht mehr.
- **Python-Werkbank für Praxisaufgaben**: Editor mit Eingaben und Ausführen im Browser (10 s), Code wird mit abgegeben und lässt sich in der Bewertung ausführen. Aktiv in *Dein erstes vollständiges Programm*.
- **Wissensdatenbank ausgebaut**: Alle Modulartikel erklären jetzt, was die Aufgaben voraussetzen (u. a. Subnetze, OSI, TCP/UDP, HTTP, Transaktionen, Beziehungen, Branches und Merge-Konflikte, Hashing, Firewall-Regeln, Konfusionsmatrix, neuronale Netze, CSS, JavaScript, REST-APIs, while-Schleifen, Dictionaries, Fehlermeldungen, Hexadezimal). Neu: *So funktioniert CodePath* (Punkte, Versuche, Hinweise, Aufgabentypen) und *Browser-Entwicklerwerkzeuge* für das Browser-Labor. Vollständige Lösungen zu Programmieraufgaben stehen nicht in den Artikeln.
- Neue Dokumentation: Handbücher für Lehrkräfte, Betrieb, Sicherheit und Entwicklung, Übersicht der Aufgabentypen.

### Verbessert
- Live-Updates werden auf einem eigenen Thread gesendet; ein langsamer Client hält keine Abgabe mehr auf, gestaute Ranglisten-Updates werden zusammengefasst.

### Update
- Migration V24 ergänzt Lernpfade um `enforce` (verbindlich) und `unlock_rest_after` (Freigabe übriger Module ab einer Station). Bestehende Lernpfade bleiben unverändert.
- Migration V23 ordnet Lernpfade optional einem Kurs zu.
- Migration V22 legt das Modul *Die Kommandozeile* an und verschiebt die Aufgabe *Projekt-Setup im Terminal* samt Versuchen und Punkten dorthin. Im Git-Modul werden die durch Terminal-Aufgaben ersetzten Fragen (g2, g4, g8–g11, g13, g14) archiviert – Versuche, Punkte und Praxisabgaben bleiben erhalten, im Editor lassen sie sich zurückholen. Die neuen Aufgaben legt der Start an; beide Module sind danach für alle Kurse gesperrt, bis die Lehrkraft sie freischaltet. Die Modulreihenfolge wird nur angepasst, wo sie noch der ausgelieferten entspricht.
- Migration V21 ergänzt Module um Berufsfeld und Weiterlern-Links (beim Start für die mitgelieferten Module nachgetragen, wenn noch leer) und legt die Tabelle `learning_paths` an.
- Migration V20 merkt sich, welcher mitgelieferte Text in einem Wissensartikel steht. Artikel, die niemand im Browser geändert hat, bekommen beim Start die neue Fassung; bearbeitete Artikel bleiben unverändert.
- Migration V19 ergänzt Aufgabenversionen um den passenden Wissensartikel (`knowledgeArticle`, auch im Content-Paket).
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

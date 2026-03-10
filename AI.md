# CodePath - Technische Dokumentation (AI.md)

CodePath ist eine moderne, interaktive Lernplattform zur IT-Berufsorientierung. Diese Dokumentation beschreibt den technischen Aufbau, die Architekturentscheidungen und die Implementierungsdetails des Projekts.

## 🏗️ Systemarchitektur

Das Projekt ist als **Monorepo** strukturiert und folgt einer klassischen Client-Server-Architektur mit einer persistenten Datenhaltung.

### Übersicht Tech-Stack
- **Backend:** Java 21, Spring Boot 3.5, Spring Security, Hibernate/JPA.
- **Frontend:** Svelte 5 (SvelteKit), TypeScript, Tailwind CSS 4.
- **Datenbank:** PostgreSQL 17 (Produktion) & H2 (In-Memory für SQL-Validierung).
- **Message Broker:** RabbitMQ (Spring AMQP) für asynchrone Lastabwicklung.
- **Infrastruktur:** Docker & Docker Compose.
- **Besonderheiten:** Pyodide (Python), TipTap, Monaco Editor, OpenPDF, Svelte-dnd-action.

---

## 🖥️ Backend-Implementierung

Das Backend ist in Java mit Spring Boot realisiert und nutzt eine **Package-by-Feature** Struktur.

### 1. Sicherheit & Authentifizierung (`/auth`)
- **JWT-Authentifizierung:** Stateless-Sitzungen via JWT. Das Token wird im Body gesendet und im Frontend verwaltet (HttpOnly Cookies sind für die API-Kommunikation vorbereitet).
- **CSRF-Schutz:** Aktiviert via `CookieCsrfTokenRepository`. Das Frontend sendet den Token im `X-XSRF-TOKEN` Header bei allen schreibenden Anfragen (POST, PUT, DELETE) mit.
- **Rollen:** `STUDENT` und `ADMIN`.
- **Anmerkung:** Spring Security Warnungen bezüglich `AuthenticationManager` wurden explizit unterdrückt (via Logging-Config), da das manuelle Provider-Setup gewollt ist.

### 2. SQL-Aufgaben Validierung (`TaskSubmissionService`)
- **Sandbox:** Studentische SQL-Abfragen werden in einer isolierten In-Memory H2-Datenbank validiert.
- **RCE-Schutz:** 
    - **Whitelist-Validierung:** Nur Abfragen, die mit `SELECT`, `WITH` oder `VALUES` beginnen, sind erlaubt.
    - **Keyword-Blocking:** Gefährliche administrative Befehle (`CREATE`, `DROP`, `ALTER`, `CALL`, etc.) werden vor der Ausführung blockiert.
    - **Read-Only Modus:** Die Datenbankverbindung wird vor der Ausführung des studentischen Codes explizit auf `setReadOnly(true)` gesetzt.
- **DoS-Schutz:** Ein Query-Timeout von 3 Sekunden verhindert Ressourcen-Erschöpfung durch komplexe Abfragen.

### 2. Datenmodell & Migrationen
- **Flyway:** Verwaltet versionierte SQL-Migrationen.
- **Wichtige Kernentitäten:**
    - `User`: Nutzerdaten, Rollen und Gesamtpunkte.
    - `Module`: Enthält nun `available_until` für zeitgesteuerte Sperrungen (Deadlines).
    - `UserTaskCompletion`: Speichert Fortschritt, Bearbeitungszeit, Fehlversuche, Sperr-Status (`is_locked`) und `support_used`.
    - `PracticeSubmission`: Freitext-Lösungen inkl. Statusverlauf und Korrekturkommentaren.

### 3. Zeugnis-Generierung (`ReportService`)
- **PDF-Export:** Nutzt `OpenPDF` zur Erstellung offizieller Leistungsnachweise (inkl. Wasserzeichen und offiziellem Header).
- **Noten-Logik:** Berechnet automatisch eine Note (1-6) basierend auf erreichten Prozentpunkten (IHK-Spiegel).
- **Validierung:** Nur vom Admin freigeschaltete Module werden bewertet. Durch Fehlversuche gesperrte Aufgaben zählen als 0 Punkte.

### 4. Skalierung & Asynchronität (RabbitMQ)
Um das System vor Überlastung zu schützen (z.B. wenn eine ganze Klasse gleichzeitig Aufgaben abgibt), ist **RabbitMQ** integriert:
- **Event-Driven:** Speicherintensive Prozesse wie die Freitext-Praxisabgaben (`PracticeSubmissionEvent`) werden nicht synchron in die Datenbank geschrieben, sondern als Event an RabbitMQ (`codepath.practice.submissions`) übergeben.
- **Consumer:** Der `SubmissionConsumer` arbeitet die Queue asynchron und idempotent im Hintergrund ab.
- **User Experience:** Das Backend blockiert nicht; der Schüler erhält sofort eine Rückmeldung, dass die Abgabe erfolgreich in Prüfung ist.

### 5. Dateninitialisierung & Kommunikation
- **Flyway Seeding:** Neue Module und Aufgaben werden beim Systemstart automatisch über SQL-Migrationen (`V7__seed_tasks.sql`) in die Datenbank geladen. Dies ersetzt das Laden aus einer `content.json` und stellt sicher, dass die Daten bei jedem Start (auch mit `docker compose down -v`) konsistent vorhanden sind.
- **Live-Editor (Ankündigungen):** Admins können globale Mitteilungen veröffentlichen. Diese unterstützen Zeilenumbrüche (`\n`) und verschiedene Anzeige-Modi (Header, Info-Seite).
- **Globales Feedback:** Admins können eine Feedback-Runde starten/stoppen. Schüler können einmalig nach Bestätigung eine Rückmeldung abgeben.

---

## 🎨 Frontend-Implementierung

Das Frontend nutzt **Svelte 5** und moderne Browser-APIs für Stabilität und Performance.

### 1. Stabilität & Sandbox (Web Worker)
- **Code-Execution:** Python-Code (Pyodide) wird in einem dedizierten **Web Worker** (`pyodide-worker.js`) ausgeführt.
- **Timeout-Mechanismus:** Ein 5-Sekunden-Timeout verhindert, dass Endlosschleifen im Schüler-Code den Browser einfrieren lassen. Nach Ablauf wird der Worker automatisch terminiert.

### 2. Offline-Fähigkeit (Service Worker)
- **PWA-Features:** Ein Service Worker (`service-worker.js`) implementiert eine "Network First"-Strategie.
- **Caching:** Wichtige Bibliotheken (Monaco, Pyodide) und bereits geladene Aufgaben-Daten werden lokal gespeichert, um bei instabilem Schul-WLAN einen unterbrechungsfreien Betrieb zu ermöglichen.

### 3. UI & Design ("Institutional Navy")
- **Dashboard:** Modernes Control-Center mit Schnellzugriffen und Teilnehmer-Ranking.
- **Teilnehmer-Akte:** Module sind standardmäßig eingeklappt für bessere Übersicht. Bietet granulare Steuerung (100%, 50%, Sperren).
- **Frontpage:** Öffentliche Landingpage mit Agenda, Zeitplan und Lehrer-Profil (Informatikstudent, 22 Jahre).
- **Beamer-Ansicht (`/projector`):** Spezielles, abgedunkeltes Full-Screen-Layout ohne Navigation für die Präsentation im Raum. Zeigt das Live-Leaderboard, eine Echtzeituhr und globale Mitteilungen an (Updates via MQTT + 30s Fallback-Polling).

---

## 🚀 Key Features & Aufgaben-Formate

### Vielfältige Aufgaben-Typen (`TaskType`)
Die Plattform unterstützt interaktive Aufgaben zur IT-Berufsorientierung:
1. **Multiple Choice / Fill Blank:** Automatisierte Text- und Logikprüfungen.
2. **Python Code (`CODE`, `FILL_CODE`):** Browserbasierte Python-Ausführung via Pyodide.
3. **SQL-Abfragen (`SQL`):** Schüler schreiben `SELECT`-Queries im Monaco Editor. Das Backend validiert das Result-Set dynamisch gegen eine isolierte In-Memory H2-Datenbank.
4. **Terminal Simulator (`TERMINAL`):** Virtuelle Kommandozeile. Das Backend prüft den finalen Dateisystem-Status (z.B. nach `mkdir`, `touch`, `rm`).
5. **Drag & Drop (`SORTING`):** Sortieraufgaben (z.B. für Prozessabläufe) über flüssige Animationen mit `svelte-dnd-action`.
6. **Praxis (`PRACTICE`):** Freitext-Einreichungen mit TipTap-Rich-Text-Editor für manuelle Dozenten-Korrekturen.

### Granulare Validierung
Admins können im Korrektur-Workflow differenziert entscheiden:
- **Volle Punkte:** Standard-Freigabe.
- **Halbe Punkte:** Freigabe mit Punktabzug (überschreibt `support_used`).
- **Endgültig Sperren:** Lehnt ab und verhindert weitere Einreichungen für diese Aufgabe.

### Korrektur-Kultur
- Bei einer Ablehnung zur Korrektur bleibt die ursprüngliche Lösung des Schülers im Editor erhalten (`submissionContent`), um eine iterative Verbesserung zu ermöglichen.

### Zeit- und Sperrmanagement
- **Modul-Deadlines:** Admins können End-Uhrzeiten für Module festlegen. Schüler sehen einen Live-Countdown; nach Ablauf wird das Modul optisch ("Zeit abgelaufen") und technisch gesperrt.
- **Automatisches Sperren:** Multiple-Choice-Aufgaben werden nach **3 Fehlversuchen** gesperrt.

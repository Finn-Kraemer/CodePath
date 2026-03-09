# CodePath - Technische Dokumentation (AI.md)

CodePath ist eine moderne, interaktive Lernplattform zur IT-Berufsorientierung. Diese Dokumentation beschreibt den technischen Aufbau, die Architekturentscheidungen und die Implementierungsdetails des Projekts.

## 🏗️ Systemarchitektur

Das Projekt ist als **Monorepo** strukturiert und folgt einer klassischen Client-Server-Architektur mit einer persistenten Datenhaltung.

### Übersicht Tech-Stack
- **Backend:** Java 21, Spring Boot 3.5, Spring Security, Hibernate/JPA.
- **Frontend:** Svelte 5 (SvelteKit), TypeScript, Tailwind CSS 4.
- **Datenbank:** PostgreSQL 17.
- **Message Broker:** RabbitMQ (Spring AMQP) für asynchrone Lastabwicklung.
- **Infrastruktur:** Docker & Docker Compose.
- **Besonderheiten:** Pyodide (Python), TipTap, Monaco Editor, OpenPDF.

---

## 🖥️ Backend-Implementierung

Das Backend ist in Java mit Spring Boot realisiert und nutzt eine **Package-by-Feature** Struktur.

### 1. Sicherheit & Authentifizierung (`/auth`)
- **JWT-Authentifizierung:** Stateless-Sitzungen via JWT.
- **Rollen:** `STUDENT` und `ADMIN`.
- **Anmerkung:** Spring Security Warnungen bezüglich `AuthenticationManager` wurden explizit unterdrückt (via Logging-Config), da das manuelle Provider-Setup gewollt ist.

### 2. Datenmodell & Migrationen
- **Flyway:** Verwaltet versionierte SQL-Migrationen.
- **Wichtige Kernentitäten:**
    - `User`: Nutzerdaten, Rollen und Gesamtpunkte.
    - `UserTaskCompletion`: Speichert Fortschritt, Bearbeitungszeit, Fehlversuche, Sperr-Status (`is_locked`) und `support_used`.
    - `PracticeSubmission`: Freitext-Lösungen inkl. Statusverlauf und Korrekturkommentaren.

### 3. Zeugnis-Generierung (`ReportService`)
- **PDF-Export:** Nutzt `OpenPDF` zur Erstellung offizieller Leistungsnachweise.
- **Noten-Logik:** Berechnet automatisch eine Note (1-6) basierend auf erreichten Prozentpunkten (IHK-Spiegel).
- **Validierung:** Nur vom Admin freigeschaltete Module werden bewertet. Durch Fehlversuche gesperrte Aufgaben zählen als 0 Punkte.

### 4. Skalierung & Message Queues (RabbitMQ)
- **Praxis-Abgaben:** `codepath.practice.submissions` entkoppelt die Speicherung großer Textinhalte.
- **Aufgaben-Fertigstellung:** `codepath.task.completions` verarbeitet Punktegutschriften und Leaderboard-Updates im Hintergrund.

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

---

## 🚀 Key Features & Funktionen

### Granulare Validierung
Admins können im Korrektur-Workflow differenziert entscheiden:
- **Volle Punkte:** Standard-Freigabe.
- **Halbe Punkte:** Freigabe mit Punktabzug (überschreibt `support_used`).
- **Endgültig Sperren:** Lehnt ab und verhindert weitere Einreichungen für diese Aufgabe.

### Korrektur-Kultur
- Bei einer Ablehnung zur Korrektur bleibt die ursprüngliche Lösung des Schülers im Editor erhalten (`submissionContent`), um eine iterative Verbesserung zu ermöglichen.

### Automatisches Sperrsystem
- Multiple-Choice-Aufgaben werden nach **3 Fehlversuchen** automatisch gesperrt, um bloßes Raten zu verhindern.

# CodePath - Technische Dokumentation (AI.md)

CodePath ist eine moderne, interaktive Lernplattform zur IT-Berufsorientierung. Diese Dokumentation beschreibt den technischen Aufbau, die Architekturentscheidungen und die Implementierungsdetails des Projekts.

## 🏗️ Systemarchitektur

Das Projekt ist als **Monorepo** strukturiert und folgt einer klassischen Client-Server-Architektur mit einer persistenten Datenhaltung.

### Übersicht Tech-Stack
- **Backend:** Java 21, Spring Boot 3.5, Spring Security, JWT, Hibernate/JPA.
- **Frontend:** Svelte 5 (SvelteKit), TypeScript, Tailwind CSS 4.
- **Datenbank:** PostgreSQL 17.
- **Message Broker:** RabbitMQ (Spring AMQP) für asynchrone Lastabwicklung.
- **Infrastruktur:** Docker & Docker Compose.
- **Besonderheiten:** Pyodide (Python im Browser), TipTap (Rich-Text), Monaco Editor.

---

## 🖥️ Backend-Implementierung

Das Backend ist in Java mit Spring Boot realisiert und nutzt eine **Package-by-Feature** Struktur für hohe Modularität.

### 1. Sicherheit & Authentifizierung (`/auth`)
- **JWT-Authentifizierung:** Stateless-Sitzungen via JWT.
- **Rollen:** `STUDENT` und `ADMIN`.
- **Anmerkung:** Spring Security Warnungen bezüglich `AuthenticationManager` wurden explizit unterdrückt, da das Setup mit einem manuell konfigurierten `DaoAuthenticationProvider` gewollt ist.

### 2. Datenmodell & Migrationen
- **Flyway:** Verwaltet versionierte SQL-Migrationen.
- **Wichtige Erweiterungen:**
    - `UserTaskCompletion`: Speichert Fortschritt, Bearbeitungszeit, Fehlversuche, Sperr-Status (`is_locked`) und ob Unterstützung genutzt wurde (`support_used`).
    - `PracticeSubmission`: Speichert Freitext-Lösungen inkl. Statusverlauf und Korrekturkommentaren.
    - `SystemSetting`: Globale Key-Value Konfigurationen (z.B. Feedback-Status).

### 3. Aufgaben-Validierung & Sperrsystem (`TaskSubmissionService`)
- **Automatisches Sperren:** Multiple-Choice-Aufgaben werden nach **3 Fehlversuchen** automatisch gesperrt.
- **Support-Modus:** Schüler können Hilfestellungen aufklappen. Dies setzt das Flag `support_used`, wodurch die erreichbare Punktzahl für diese Aufgabe halbiert wird.
- **Korrektur-Workflow:** Bei einer Ablehnung zur Korrektur bleibt die ursprüngliche Lösung des Schülers im Editor erhalten (`submissionContent`), um eine einfache Überarbeitung zu ermöglichen.

### 4. Skalierung & Asynchronität (RabbitMQ)
Alle leistungskritischen Schreibvorgänge laufen asynchron über Message Queues:
- **Praxis-Abgaben:** `codepath.practice.submissions` entkoppelt die Speicherung großer Textinhalte.
- **Aufgaben-Fertigstellung:** `codepath.task.completions` verarbeitet Punktegutschriften und Leaderboard-Updates im Hintergrund.
- **Consumer:** Der `SubmissionConsumer` garantiert Idempotenz und sorgt für eine reibungslose Punktevergabe ohne Blockierung des Web-Threads.

---

## 🎨 Frontend-Implementierung

Das Frontend nutzt **Svelte 5** für ein hochreaktives Nutzererlebnis.

### 1. Svelte 5 Integration
- Nutzung von Runes (`$state`, `$derived`, `$effect`) für konsistentes State-Management.
- **Tiefen-Reaktivität:** Komplexe Zustände wie eingeklappte Module werden über reaktive Records verwaltet.

### 2. UI & Design ("Institutional Navy")
- **Dashboard:** Aufgeräumtes Admin-Control-Center mit 12-Spalten-Grid, Schnellzugriffs-Karten und Live-Leaderboard.
- **Teilnehmer-Akte:** Module sind standardmäßig eingeklappt. Bietet granulare Steuerung pro Aufgabe (100% Punkte, 50% Punkte, Sperren/Entsperren).
- **Status-Visualisierung:** 
    - Grün: Erledigt (volle Punkte).
    - Gelb: Erledigt mit Unterstützung (halbe Punkte).
    - Rot: Gesperrt (zu viele Fehlversuche oder manuell).

### 3. Interaktive Editoren
- **PracticeEditor:** Basierend auf TipTap, unterstützt Rich-Text und Bild-Uploads.
- **Code-Umgebung:** Monaco Editor mit Pyodide für serverseitig unabhängige Python-Ausführung im Client.

---

## 🚀 Key Features & Funktionen

### Granulare Validierung
Admins können im Korrektur-Workflow differenziert entscheiden:
- **Volle Punkte:** Standard-Freigabe.
- **Halbe Punkte:** Freigabe mit Punktabzug (überschreibt `support_used`).
- **Endgültig Sperren:** Lehnt ab und verhindert weitere Einreichungen.
- **Ablehnen (Korrektur):** Standard-Ablehnung für eine Nachbesserung.

### Öffentliche Frontpage
Die Startseite (`/`) ist öffentlich zugänglich und dient als Infoportal für den Kurs mit:
- Einführung in den IT-Projekttag.
- Kurzprofil der Lehrperson (Informatikstudent).
- Veranstaltungs-Agenda und geplanter Ablauf (Timeline-Design).

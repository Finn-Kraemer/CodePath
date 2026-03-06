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
- **Mechanismus:** JWT-basierte Authentifizierung (Stateless).
- **Filter-Chain:** Ein `JwtAuthenticationFilter` extrahiert den Token aus dem `Authorization`-Header und validiert diesen gegen den `JwtService`.
- **Rollen:** Unterscheidung zwischen `STUDENT` und `ADMIN` via Spring Security `@PreAuthorize`.

### 2. Datenmodell & Migrationen
- **Flyway:** Verwaltet versionierte SQL-Migrationen (`src/main/resources/db/migration/`).
- **Kernentitäten:**
    - `User`: Nutzerdaten, Rollen und Gesamtpunkte.
    - `Module` & `Task`: Struktur der Lerninhalte.
    - `UserTaskCompletion`: Speichert Fortschritt, Bearbeitungszeit (`timeSpentSeconds`) und Fehlversuche (`failedAttempts`).
    - `PracticeSubmission`: Speichert die Freitext-Abgaben für manuelle Korrekturen (Status: `PENDING`, `APPROVED`, `REJECTED`).
    - `GlobalAnnouncement`: Systemweite Mitteilungen der Lehrkräfte (unterstützt verschiedene Anzeige-Modi).
    - `GeneralFeedback`: Ermöglicht Schülern die Abgabe von allgemeinem Kurs-Feedback.
    - `SystemSetting`: Speichert globale Konfigurationen wie den Status der Feedback-Runde.

### 3. Aufgaben-Validierung & Fortschritt (`TaskSubmissionService`)
Der zentrale Dienst zur Prüfung von Aufgaben wurde um Fortschritts-Tracking erweitert:
- **Timer:** Erfasst die Bearbeitungszeit pro Aufgabe. Kann für Bonus-Punkte bei schneller Lösung genutzt werden.
- **Fehlversuche:** Trackt `failedAttempts`. Nach 3 Fehlversuchen liefert das Backend die korrekte Lösung als Hilfestellung mit.
- **Punkte-Logik:** Bonus-Punkte für Schnelligkeit und Abzug bei Nutzung der Lösungshilfe.
- **PRACTICE:** Unterstützt nun einen Feedback-Zyklus. Bei `REJECTED` kann ein Admin-Kommentar hinterlegt werden, woraufhin der Schüler seine Lösung überarbeiten kann.

### 4. Skalierung & Asynchronität (RabbitMQ)
Um das System vor Überlastung zu schützen (z.B. wenn eine ganze Klasse gleichzeitig Aufgaben abgibt), ist **RabbitMQ** integriert:
- **Event-Driven:** Speicherintensive Prozesse wie die Freitext-Praxisabgaben (`PracticeSubmissionEvent`) werden nicht synchron in die Datenbank geschrieben, sondern als Event an RabbitMQ (`codepath.practice.submissions`) übergeben.
- **Consumer:** Der `SubmissionConsumer` arbeitet die Queue asynchron und idempotent im Hintergrund ab.
- **User Experience:** Das Backend blockiert nicht; der Schüler erhält sofort eine Rückmeldung, dass die Abgabe erfolgreich in Prüfung ist.

### 5. Dateninitialisierung & Kommunikation
- Beim Systemstart lädt der `DataInitializer` die Datei `content.json`.
- **Live-Editor (Ankündigungen):** Admins können globale Mitteilungen veröffentlichen. Diese unterstützen Zeilenumbrüche (`\n`) und verschiedene Anzeige-Modi (Header, Info-Seite).
- **Globales Feedback:** Admins können eine Feedback-Runde starten/stoppen. Schüler können einmalig nach Bestätigung eine Rückmeldung abgeben.

---

## 🎨 Frontend-Implementierung

Das Frontend nutzt die neuesten Features von **Svelte 5** für ein reaktives und performantes Nutzererlebnis.

### 1. Reaktivität mit Svelte 5 Runes
- Nutzung von `$state`, `$derived` und `$effect` für ein effizientes State-Management.
- Zentraler `auth.svelte.ts` Store zur Verwaltung des Login-Status und API-Abfragen.

### 2. Interaktive Komponenten
- **TaskTimer:** Eine reaktive Komponente, die die Bearbeitungszeit misst und beim Submit mitsendet.
- **GlobalAnnouncement Banner:** Integriert in das Hauptlayout zur Echtzeit-Kommunikation.
- **Feedback-System:** Dedizierte Seiten für Schüler (`/feedback`) mit Einmalsperre und Bestätigungsdialog sowie für Admins (`/admin/feedback`) zur Steuerung und Einsicht.
- **Monaco Editor:** Bietet eine vollwertige IDE-Erfahrung für Python-Aufgaben inklusive Syntax-Highlighting.
- **Pyodide Integration:** Python-Code wird **direkt im Browser** des Nutzers ausgeführt.

### 3. Routing & Layout
- SvelteKit-basiertes Dateisystem-Routing.
- **Admin-Bereich:** Zentrale Dashboard-Übersicht mit Echtzeit-Feed von Schüler-Kommentaren, Verwaltung der Ankündigungen, Feedback-Zentrum und Prüfung von Abgaben.

---

## 🚀 Key Features & Implementierung

### Gamification (Leaderboard)
- Die `LeaderboardService` berechnet im Backend ein Ranking basierend auf den `totalPoints` der User.

### Manueller Korrektur-Workflow (Feedback-Kultur)
- Vollständiger Zyklus aus Einreichung, Prüfung, Freigabe oder Ablehnung mit Kommentar zur Revision.
- Ermöglicht eine enge pädagogische Begleitung der Schüler durch die Lehrkräfte.

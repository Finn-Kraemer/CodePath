# CodePath

CodePath ist eine interaktive Lernplattform zur IT-Berufsorientierung, die sich speziell an Schülerinnen und Schüler der Klassen 7–9 richtet. Die Plattform ermöglicht es, verschiedene IT-Berufsfelder durch praxisnahe Aufgaben zu erkunden.

## 🚀 Schnelleinstieg

Kopieren Sie diesen Inhalt in eine `docker-compose.yml` und führen Sie `docker compose up` aus:

```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: codepath
      POSTGRES_USER: codepath
      POSTGRES_PASSWORD: codepath
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U codepath"]
      interval: 5s
      timeout: 5s
      retries: 5

  backend:
    image: ghcr.io/finn-kraemer/codepath-backend:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/codepath
      SPRING_DATASOURCE_USERNAME: codepath
      SPRING_DATASOURCE_PASSWORD: codepath
      JWT_SECRET: change-me-in-production-min-32-chars!!
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
      interval: 10s
      timeout: 5s
      retries: 5
    depends_on:
      postgres:
        condition: service_healthy

  frontend:
    image: ghcr.io/finn-kraemer/codepath-frontend:latest
    ports:
      - "80:80"
    depends_on:
      backend:
        condition: service_healthy

volumes:
  postgres_data:
```

- **Frontend**: [http://localhost](http://localhost)
- **Standard-Admin**: `admin` / `admin123`

---

## 🏗️ Architektur & Tech Stack

CodePath ist als Monorepo organisiert und nutzt moderne Technologien für eine robuste und wartbare Codebasis.

### Backend
- **Framework:** Spring Boot 3.5.x (Java 21)
- **Datenbank:** PostgreSQL 17 & Flyway für Migrationen
- **Security:** Spring Security mit JWT-basierten Tokens
- **Testing:** JUnit 5, MockMvc, H2 (für Integrationstests)
- **Key-Features:** Package-by-Feature Struktur, Lombok, Jakarta Validation

### Frontend
- **Framework:** Svelte 5 (SvelteKit) mit Runes (`$state`, `$derived`, etc.)
- **Styling:** Tailwind CSS 4
- **Editor:** TipTap für Rich-Text-Abgaben (Practice Tasks)
- **Runtime:** Pyodide für die Ausführung von Python-Code im Browser

---

## 📚 Lernmodule & Aufgaben

Die Plattform ist in verschiedene Module unterteilt, die jeweils einen IT-Bereich abdecken:

1.  🐍 **Programmierung**: Variablen, Schleifen, Funktionen in Python.
2.  🌐 **Web-Entwicklung**: HTML, HTTP, Client-Server-Modell.
3.  🔒 **IT-Sicherheit**: Passwörter, Verschlüsselung, Phishing-Erkennung.
4.  🗄️ **Datenbanken**: SQL-Grundlagen und Datenmodellierung.
5.  🤖 **Künstliche Intelligenz**: Wie KIs lernen und Bias-Erkennung.
6.  ☁️ **Cloud & Netzwerke**: IP, DNS und Cloud-Infrastruktur.
7.  🔧 **Digitaltechnik**: Binärsystem und Hardware-Grundlagen.

### Aufgabentypen
- `MULTIPLE_CHOICE`: Automatisch bewertet.
- `FILL_BLANK`: Begriffe in Lücken eintragen.
- `FILL_CODE`: Code-Lücken ergänzen.
- `CODE`: Freies Coding in Python (bewertet via Pyodide).
- `PRACTICE`: Komplexe Aufgaben mit Rich-Text-Abgabe (manuelle Bewertung durch Lehrkraft).

---

## 💻 Entwicklung

### Voraussetzungen
- Java 21+
- Node.js 22+ & pnpm
- Docker

### Lokaler Start (Entwicklung)
1.  **Infrastruktur**: `docker compose up postgres`
2.  **Backend**: `cd backend && ./mvnw spring-boot:run`
3.  **Frontend**: `cd frontend && pnpm install && pnpm dev`

### Konventionen
- **Backend**: Migrationen in `src/main/resources/db/migration/`. REST-Endpoints unter `/api`.
- **Frontend**: Formatierung mit `pnpm format` vor jedem Commit. Nutzung von Svelte 5 Runes.
- **Testing**: Backend-Tests mit `./mvnw test`, Frontend-Checks mit `pnpm check`.

---

## 🗃️ Datenmodell (Kernentitäten)

- **User**: Speichert Rollen (`STUDENT`, `ADMIN`) und Gesamtpunkte.
- **Module & Task**: Definieren die Lerninhalte (hardcoded Initialisierung via `DataInitializer`).
- **UserTaskCompletion**: Trackt bestandene Aufgaben und vergebene Punkte.
- **PracticeSubmission**: Speichert HTML-Inhalte der TipTap-Abgaben zur manuellen Korrektur.

---

## 🛠️ TipTap Editor (PRACTICE-Aufgaben)

Für komplexe Abgaben nutzt CodePath einen konfigurierten TipTap Editor im Frontend. Er unterstützt:
- Überschriften, Listen, Fettdruck.
- Code-Blöcke mit Syntax-Highlighting (Lowlight).
- Bild-Upload (Base64-Inlining für einfache Handhabung).
- Automatische Speicherung und Einreichung an das Backend.

# CodePath

CodePath ist eine interaktive Lernplattform zur IT-Berufsorientierung, die sich speziell an Schülerinnen und Schüler der Klassen 7–9 richtet. Die Plattform ermöglicht es, verschiedene IT-Berufsfelder durch praxisnahe Aufgaben zu erkunden.

## 🚀 Schnelleinstieg

Kopieren Sie diesen Inhalt in eine `docker-compose.yml` und führen Sie `docker compose up -d` aus:

```yaml
services:
  postgres:
    image: postgres:17
    restart: on-failure
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

  pgadmin:
    image: dpage/pgadmin4:latest
    restart: on-failure
    ports:
      - "8082:80"
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@local.com
      PGADMIN_DEFAULT_PASSWORD: secret
      PGADMIN_CONFIG_ENHANCED_COOKIE_PROTECTION: "False"
      PGADMIN_CONFIG_CONSOLE_LOG_LEVEL: "10"
      PGADMIN_CONFIG_SERVER_MODE: "False"
      PGADMIN_CONFIG_DISABLE_LOGIN_BANNER: "True"
    depends_on:
      - postgres

  rabbitmq:
    image: rabbitmq:3-management
    restart: on-failure
    ports:
      - "5672:5672"
      - "15672:15672"
      - "15675:15675"
    command: >
      bash -c "rabbitmq-plugins enable --offline rabbitmq_mqtt rabbitmq_web_mqtt && rabbitmq-server"
    environment:
      RABBITMQ_DEFAULT_USER: codepath
      RABBITMQ_DEFAULT_PASS: codepath

  backend:
    image: ghcr.io/finn-kraemer/codepath-backend:latest
    restart: on-failure
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/codepath
      SPRING_DATASOURCE_USERNAME: codepath
      SPRING_DATASOURCE_PASSWORD: codepath
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: 5672
      SPRING_RABBITMQ_USERNAME: codepath
      SPRING_RABBITMQ_PASSWORD: codepath
      JWT_SECRET: change-me-in-production-min-32-chars!!
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
      interval: 10s
      timeout: 5s
      retries: 5
    depends_on:
      - postgres
      - rabbitmq

  frontend:
    image: ghcr.io/finn-kraemer/codepath-frontend:latest
    restart: on-failure
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

## 🛠️ Infrastruktur-Zugang

Für Verwaltung und Debugging stehen folgende Oberflächen zur Verfügung:

### Datenbank (pgAdmin 4)
- **URL**: [http://localhost:8082](http://localhost:8082)
- **Login**: `admin@local.com` / `secret`
- **Verbindung zur DB**: Host: `postgres`, User/Pass/DB: `codepath`

### Message Broker (RabbitMQ)
- **Management UI**: [http://localhost:15672](http://localhost:15672)
- **Login**: `codepath` / `codepath`
- **Features**: MQTT over WebSockets ist auf Port `15675` aktiv.

---

## 🏗️ Architektur & Tech Stack

CodePath ist als Monorepo organisiert und nutzt moderne Technologien für eine robuste und wartbare Codebasis.

### Backend
- **Framework:** Spring Boot 3.5.x (Java 21)
- **Datenbank:** PostgreSQL 17 & Flyway für Migrationen
- **Messaging:** RabbitMQ (AMQP) für asynchrone Lastabwicklung
- **Echtzeit:** MQTT für Live-Updates (Leaderboard & Ankündigungen)
- **Security:** Spring Security mit JWT-basierten Tokens

### Frontend
- **Framework:** Svelte 5 (SvelteKit) mit Runes (`$state`, `$derived`, etc.)
- **Echtzeit:** Paho MQTT Client für Push-Benachrichtigungen
- **Styling:** Tailwind CSS 4
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
1.  **Infrastruktur**: 
    ```bash
    cd backend/dev-stack
    ./start.sh
    ```
2.  **Backend**: `cd backend && ./mvnw spring-boot:run`
3.  **Frontend**: `cd frontend && pnpm install && pnpm dev`

---

## 🗃️ Datenmodell (Kernentitäten)

- **User**: Speichert Rollen (`STUDENT`, `ADMIN`) und Gesamtpunkte.
- **Module & Task**: Definieren die Lerninhalte.
- **UserTaskCompletion**: Trackt Fortschritt, Zeit und Fehlversuche.
- **PracticeSubmission**: Speichert TipTap-Abgaben (asynchron via RabbitMQ).
- **GeneralFeedback**: Einmalige Kurs-Rückmeldung der Schüler.
- **GlobalAnnouncement**: Echtzeit-Mitteilungen der Lehrkräfte.

# CodePath

CodePath ist eine interaktive Lernplattform zur IT-Berufsorientierung, die sich speziell an Schülerinnen und Schüler der Klassen 7–9 richtet. Die Plattform ermöglicht es, verschiedene IT-Berufsfelder durch praxisnahe Aufgaben zu erkunden.

## 🚀 Schnelleinstieg

```bash
curl -O https://raw.githubusercontent.com/finn-kraemer/codepath/main/docker-compose.yml
curl -o .env https://raw.githubusercontent.com/finn-kraemer/codepath/main/.env.example
# .env öffnen und alle Werte setzen, z. B. mit: openssl rand -base64 48
docker compose up -d
```

Ohne gesetzte Secrets in `.env` startet der Stack nicht – es gibt bewusst keine Standardpasswörter.

- **Frontend**: [http://localhost:8081](http://localhost:8081)
- **Admin-Konto**: Benutzername `admin`. Das Passwort ist `ADMIN_SEED_PASSWORD` aus `.env`; ist es leer, wird beim ersten Start eines erzeugt und ins Log geschrieben: `docker compose logs backend | grep "Erzeugtes Passwort"`

---

## 🛠️ Infrastruktur-Zugang

Verwaltungsoberflächen sind nur auf dem Rechner selbst erreichbar (`127.0.0.1`), nicht im Schulnetz.

### Datenbank (pgAdmin 4)
- **Start bei Bedarf**: `docker compose --profile tools up -d pgadmin`
- **URL**: [http://localhost:8082](http://localhost:8082)
- **Verbindung zur DB**: Host `postgres`, User/DB `codepath`, Passwort `POSTGRES_PASSWORD` aus `.env`

### Message Broker (RabbitMQ)
- **Management UI**: [http://localhost:15672](http://localhost:15672), Login `codepath` / `RABBITMQ_PASSWORD` aus `.env`
- **Live-Updates**: MQTT over WebSockets auf Port `15675`. Der Browser nutzt den Nur-Lese-Nutzer `codepath-viewer`, der Topics abonnieren, aber nichts veröffentlichen darf.

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
2.  **Backend**: `cd backend && JWT_SECRET=$(openssl rand -base64 48) SPRING_DATASOURCE_PASSWORD=codepath SPRING_RABBITMQ_PASSWORD=codepath ./mvnw spring-boot:run` (die Passwörter passen zum lokalen Dev-Stack; alle drei Variablen sind Pflicht)
3.  **Frontend**: `cd frontend && pnpm install && pnpm dev`

---

## 🗃️ Datenmodell (Kernentitäten)

- **User**: Speichert Rollen (`STUDENT`, `ADMIN`) und Gesamtpunkte.
- **Module & Task**: Definieren die Lerninhalte.
- **UserTaskCompletion**: Trackt Fortschritt, Zeit und Fehlversuche.
- **PracticeSubmission**: Speichert TipTap-Abgaben (asynchron via RabbitMQ).
- **GeneralFeedback**: Einmalige Kurs-Rückmeldung der Schüler.
- **GlobalAnnouncement**: Echtzeit-Mitteilungen der Lehrkräfte.

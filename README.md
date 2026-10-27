# CodePath

CodePath ist eine interaktive Lernplattform zur IT-Berufsorientierung für Schülerinnen und Schüler der Klassen 7–9. Sie erkunden IT-Berufsfelder über praxisnahe Aufgaben; die Lehrkraft schaltet Module frei, bewertet Praxisabgaben und stellt Teilnahmebestätigungen aus.

## 🚀 Schnelleinstieg

```bash
curl -O https://raw.githubusercontent.com/finn-kraemer/codepath/main/docker-compose.yml
curl -o .env https://raw.githubusercontent.com/finn-kraemer/codepath/main/.env.example
# .env öffnen und POSTGRES_PASSWORD setzen, z. B. mit: openssl rand -base64 32
docker compose up -d
```

CodePath besteht aus zwei Containern: der Anwendung (API und Oberfläche) und PostgreSQL. Ohne `POSTGRES_PASSWORD` in `.env` startet der Stack nicht – es gibt bewusst keine Standardpasswörter.

- **Oberfläche**: [http://localhost:8080](http://localhost:8080)
- **Admin-Konto**: Benutzername `admin`. Das Passwort ist `ADMIN_SEED_PASSWORD` aus `.env`; ist es leer, wird beim ersten Start eines erzeugt und ins Log geschrieben: `docker compose logs app | grep "Erzeugtes Passwort"`
- **Update**: `docker compose pull && docker compose up -d`
- **Backup**: `./deploy/backup.sh` (schreibt `backups/codepath-<datum>.sql.gz`)
- **TLS**: `docker compose -f docker-compose.yml -f deploy/docker-compose.tls.yml up -d` mit Caddy davor; Domain in `deploy/Caddyfile` eintragen und `COOKIE_SECURE=true` setzen.

Nach der Installation läuft CodePath vollständig offline: Monaco-Editor, Pyodide und Schriften sind im Image enthalten.

### Datenbank ansehen (optional)

`docker compose --profile tools up -d pgadmin` startet pgAdmin auf [http://localhost:8082](http://localhost:8082) (nur auf diesem Rechner erreichbar). Verbindung: Host `postgres`, User/DB `codepath`, Passwort aus `.env`.

---

## 🏗️ Architektur

Ein modularer Monolith (siehe `docs/adr/`): Eine Spring-Boot-Anwendung liefert API, Live-Updates und das statisch gebaute Frontend aus, dazu PostgreSQL.

- **Backend:** Java 25, Spring Boot 4.1, Spring Security mit serverseitigen Sessions (Spring Session JDBC), Flyway, PostgreSQL 17
- **Live-Updates:** Server-Sent Events unter `/api/events` (Leaderboard, Ankündigungen)
- **Frontend:** SvelteKit 2 mit Svelte 5 (adapter-static), Tailwind CSS 4, Monaco, TipTap, Pyodide – alles lokal gebündelt
- **Tests:** JUnit 5 mit Testcontainers (PostgreSQL), Vitest; CI über GitHub Actions auf jedem Pull Request

---

## 💻 Entwicklung

Voraussetzungen: Java 25, Node.js 22 mit pnpm, Docker.

```bash
# Datenbank
cd backend/dev-stack && ./start.sh

# Backend (http://localhost:8080)
cd backend && SPRING_DATASOURCE_PASSWORD=codepath ./mvnw spring-boot:run

# Frontend mit Hot Reload (http://localhost:5173, leitet /api an das Backend weiter)
cd frontend && pnpm install && pnpm dev
```

Prüfen wie in der CI:

```bash
cd backend && ./mvnw verify                    # Tests gegen PostgreSQL (Testcontainers, braucht Docker)
cd frontend && pnpm lint && pnpm check && pnpm test && pnpm build
```

Das Docker-Image baut `docker build -t codepath .` im Wurzelverzeichnis.

---

## 📚 Dokumentation

- `docs/adr/` – Architekturentscheidungen
- `docs/phase-0.md` … – was in welcher Phase der Überarbeitung umgesetzt wurde
- `AI.md` – technische Details zu Sicherheit und Implementierung

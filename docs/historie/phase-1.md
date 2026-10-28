# Phase 1 – Fundament

Gate laut Plan: läuft mit zwei Containern, alle heutigen Funktionen, CI grün.

## Umgesetzt

| Thema | Umsetzung |
|---|---|
| Laufzeit | Java 25, Spring Boot 4.1 (Jackson 3, Spring Security 7, Hibernate 7) |
| Ein Container für die App | Mehrstufiges `Dockerfile` im Wurzelverzeichnis: pnpm-Build → in die Spring-Ressourcen kopiert → ein JAR. `FrontendConfig` liefert die statischen Dateien aus und fällt für Client-Routen auf `index.html` zurück. nginx entfällt. |
| Sessions statt JWT (ADR-3) | Spring Session JDBC, Cookie `CODEPATH_SESSION` (HttpOnly, SameSite=Lax, `COOKIE_SECURE` hinter TLS). Neue Session-ID beim Login. `/api/auth/me` prüft die Session beim Laden der Seite. Die Lehrkraft kann alle Schülersitzungen beenden (`POST /api/admin/sessions/logout-students`, Button im Dashboard). `JWT_SECRET` entfällt. |
| SSE statt MQTT (ADR-2) | `GET /api/events` liefert `announcement`- und `leaderboard`-Events, gesendet erst nach dem Commit. Heartbeat alle 25 s. Der Service Worker lässt den Datenstrom unangetastet. |
| RabbitMQ raus (ADR-1) | Punktevergabe und Praxisabgaben laufen synchron in einer Transaktion; eine Zeilensperre auf dem Nutzer verhindert doppelte Punkte bei gleichzeitigen Abgaben. |
| Offline (Annahme A3) | Monaco, Pyodide und die Schrift Inter liegen lokal (`scripts/copy-vendor.mjs` kopiert sie vor jedem Build nach `static/vendor`). Keine CDN-Aufrufe mehr. |
| Tests | Testcontainers mit PostgreSQL 17 und allen Flyway-Migrationen statt H2 mit `create-drop`. |
| CI | `.github/workflows/ci.yml`: Backend `mvnw verify`, Frontend Lint + Typcheck + Vitest + Build, Docker-Build. Release-Workflow baut ein Image `ghcr.io/finn-kraemer/codepath`. |
| Betrieb | `docker-compose.yml` mit `app` und `postgres`; `deploy/backup.sh`; optionaler TLS-Proxy (`deploy/docker-compose.tls.yml`, Caddy); Actuator-Health für den Healthcheck; JSON-Logs über `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`. |

Nebenbei behoben: Die Zähler „erledigte Aufgaben“ zählten auch gesperrte oder nur angefangene Aufgaben; anonyme Anfragen legten Sessions an; fehlende statische Dateien lieferten 500 statt 404; alle 11 Typfehler und 10 ESLint-Fehler im Frontend.

## Umstieg von Phase 0

- `.env`: `RABBITMQ_PASSWORD` und `JWT_SECRET` entfallen; neu sind `CODEPATH_PORT` und `COOKIE_SECURE`.
- Die Oberfläche läuft jetzt auf Port 8080 (vorher 8081).
- Alle Nutzer melden sich einmal neu an.

# Betriebshandbuch

Für die IT-Betreuung: CodePath installieren, absichern, sichern und aktuell halten. CodePath besteht aus zwei Containern – der Anwendung und PostgreSQL – und optional einem dritten, dem Code-Runner.

## ◼ Überblick

```
          Internet / Schulnetz
                   │ 443
            ┌──────┴──────┐
            │    caddy    │  optional: TLS (deploy/docker-compose.tls.yml)
            └──────┬──────┘
                   │ 8080
            ┌──────┴──────┐        Netz „runner“ (intern, ohne Internet)
            │     app     │ ─────────────────────────────┐
            └──────┬──────┘                       ┌──────┴──────┐
                   │ 5432                         │   runner    │  optional (Profil „runner“)
            ┌──────┴──────┐                       └─────────────┘
            │  postgres   │  Volume postgres_data
            └─────────────┘
```

| Container | Image | Aufgabe |
|---|---|---|
| `app` | `ghcr.io/finn-kraemer/codepath` | API, Oberfläche, Bewertung, Live-Updates |
| `postgres` | `postgres:17` | alle Daten inklusive Sessions und Bilder |
| `runner` | `ghcr.io/finn-kraemer/codepath-runner` | führt Python-Abgaben in einer Sandbox aus (optional) |
| `caddy` | `caddy:2` | HTTPS mit automatischem Zertifikat (optional) |
| `pgadmin` | `dpage/pgadmin4` | Datenbank ansehen, nur lokal erreichbar (optional, Profil `tools`) |

Die Images gibt es für `amd64` und `arm64` (z. B. Raspberry Pi 5, Apple Silicon).

## ◼ Voraussetzungen

- Linux-Server, Windows oder macOS mit **Docker** und **Docker Compose v2**
- Richtwert für einen Kurs mit etwa 30 Personen: 2 CPU-Kerne, 2 GB RAM, 5 GB Speicher (mit Code-Runner: 1 GB RAM zusätzlich)
- Für HTTPS mit öffentlichem Zertifikat: eine Domain, die auf den Server zeigt, und die Ports 80 und 443

Nach der Installation braucht CodePath **kein Internet**: Editor, Python im Browser und Schriften sind im Image enthalten.

## ◼ Installation

```bash
mkdir codepath && cd codepath
curl -O https://raw.githubusercontent.com/finn-kraemer/codepath/main/docker-compose.yml
curl -o .env https://raw.githubusercontent.com/finn-kraemer/codepath/main/.env.example
openssl rand -base64 32        # Ergebnis als POSTGRES_PASSWORD in .env eintragen
docker compose up -d
docker compose ps              # app sollte nach etwa einer Minute „healthy“ sein
```

Beim ersten Start legt CodePath das Datenbankschema an, importiert die mitgelieferten Inhalte (11 Module, 147 Aufgaben) und das Konto `admin`. Ist `ADMIN_SEED_PASSWORD` leer, steht das erzeugte Passwort im Log:

```bash
docker compose logs app | grep "Erzeugtes Passwort"
```

Die Lehrkraft sollte es nach der ersten Anmeldung unter **Profil** ändern.

## ◼ Konfiguration

Alle Einstellungen stehen in `.env` neben der `docker-compose.yml`.

| Variable | Pflicht | Bedeutung |
|---|---|---|
| `POSTGRES_PASSWORD` | ja | Passwort der Datenbank. Ohne diesen Wert startet der Stack nicht. |
| `ADMIN_SEED_PASSWORD` | – | Passwort des Kontos `admin` beim **ersten** Start. Leer: wird erzeugt und ins Log geschrieben. Später ohne Wirkung. |
| `CODEPATH_PORT` | – | Port auf dem Host, Standard `8080` |
| `CODEPATH_VERSION` | – | Image-Version, Standard `latest`; für planbare Updates eine feste Version wie `v2.2.0` setzen |
| `COOKIE_SECURE` | – | `true`, sobald CodePath nur über HTTPS erreichbar ist – das Session-Cookie geht dann nie unverschlüsselt |
| `COMPOSE_PROFILES` | – | `runner` schaltet den Code-Runner ein; mehrere Profile mit Komma |
| `CODEPATH_RUNNER_URL` | mit Runner | `http://runner:8000` |
| `RUNNER_TOKEN` | mit Runner | gemeinsames Geheimnis von App und Runner, mindestens 16 Zeichen: `openssl rand -hex 32` |

Nach einer Änderung: `docker compose up -d`.

## ◼ HTTPS und Erreichbarkeit

Ohne TLS ist CodePath nur im eigenen Netz sinnvoll. Für HTTPS liegt ein Caddy-Proxy bei, der Zertifikate automatisch holt:

1. `deploy/Caddyfile` und `deploy/docker-compose.tls.yml` aus dem Repository neben die `docker-compose.yml` legen.
2. Im `Caddyfile` die Domain eintragen. Im Schulnetz ohne öffentliche Domain stattdessen `tls internal` verwenden (Zertifikat der eigenen Caddy-CA).
3. In `.env` `COOKIE_SECURE=true` setzen.
4. Starten:

```bash
docker compose -f docker-compose.yml -f deploy/docker-compose.tls.yml up -d
```

Die App ist dann nur noch über Caddy erreichbar. Wer einen eigenen Reverse-Proxy (nginx, Traefik) nutzt, muss für `/api/events` die **Pufferung abschalten** und das Lese-Timeout auf mehrere Minuten setzen – sonst kommen Live-Updates verspätet oder die Verbindung bricht ab.

## ◼ Backup und Wiederherstellung

Alle Daten – Konten, Fortschritte, Inhalte, Bilder – liegen in PostgreSQL. Ein Backup ist ein Datenbank-Dump:

```bash
./deploy/backup.sh       # schreibt backups/codepath-JJJJ-MM-TT_HHMM.sql.gz
```

Empfohlen: täglich per Cron und **vor jedem Update**, Backups außerhalb des Servers aufbewahren.

```cron
30 18 * * *  cd /opt/codepath && ./deploy/backup.sh && find backups -mtime +30 -delete
```

Wiederherstellen (überschreibt den aktuellen Stand):

```bash
docker compose stop app
gunzip -c backups/<datei>.sql.gz | docker compose exec -T postgres psql -U codepath codepath
docker compose start app
```

Zusätzlich lassen sich die **Inhalte** unabhängig sichern: Leitstand → Inhalte → *Export (ZIP)*.

## ◼ Update

```bash
./deploy/backup.sh
docker compose pull
docker compose up -d
```

Datenbank-Änderungen spielt CodePath beim Start selbst ein (Flyway). Vorher das [Änderungsprotokoll](../CHANGELOG.md) lesen. Überarbeitete mitgelieferte Aufgaben übernimmt die Lehrkraft danach mit *Mitgelieferte Inhalte übernehmen* – eigene Änderungen im Editor bleiben dabei erhalten, geänderte Aufgaben werden zu neuen Versionen.

## ◼ Code-Runner

Ohne Runner sind Code-Aufgaben Übungen ohne Punkte: Schülerinnen und Schüler probieren ihren Code im Browser aus, bewertet wird er nicht. Mit Runner prüft der Server jede Abgabe gegen sichtbare und versteckte Testfälle.

**Einschalten** – in `.env`:

```bash
COMPOSE_PROFILES=runner
CODEPATH_RUNNER_URL=http://runner:8000
RUNNER_TOKEN=<Ausgabe von: openssl rand -hex 32>
```

```bash
docker compose up -d
docker compose logs runner     # „Code-Runner bereit auf Port 8000“
```

**Ubuntu ab 24.04** sperrt die nötigen *user namespaces* für unprivilegierte Prozesse. Einmalig auf dem Host:

```bash
echo 'kernel.apparmor_restrict_unprivileged_userns=0' | sudo tee /etc/sysctl.d/60-codepath-runner.conf
sudo sysctl --system
```

**So ist er abgesichert.** Jede Abgabe läuft in einer [bubblewrap](https://github.com/containers/bubblewrap)-Sandbox ohne Netzwerk, ohne Dateisystem außer `/usr` (nur lesend) und einem leeren `/tmp`, mit Grenzen für CPU (2 s), Speicher (256 MB), Prozesse, Dateigröße, Laufzeit (5 s) und Ausgabe (64 KB), unter einer eigenen Benutzer-ID. Der Container hat keine Capabilities außer `SETUID`, `SETGID` und `KILL`, ein schreibgeschütztes Dateisystem und hängt in einem internen Netz ohne Weg nach draußen. Weil bubblewrap user namespaces braucht, laufen Seccomp und AppArmor des Containers im Modus `unconfined` – die Grenze bildet bubblewrap selbst. Beim Start prüft der Runner die Sandbox; greift sie nicht, startet er nicht. Details: [ADR-4](adr/0004-python-sandbox.md).

## ◼ Überwachung

| Endpunkt | Zweck |
|---|---|
| `/actuator/health` | Gesamtzustand (Docker-Healthcheck) |
| `/actuator/health/liveness`, `/actuator/health/readiness` | für Orchestrierer wie Kubernetes |
| `docker compose logs -f app` | Anwendungslog; Fehler mit Zeitstempel und Ursache |

Sicherheitsrelevante Aktionen (Inhalte veröffentlicht oder gelöscht, Kurs beendet, Passwort überschrieben, Bilder hochgeladen) stehen im Audit-Log der Datenbank (Tabelle `audit_events`).

**Datenbank ansehen:** `docker compose --profile tools up -d pgadmin` startet pgAdmin auf [http://localhost:8082](http://localhost:8082), nur vom Server selbst erreichbar. Verbindung: Host `postgres`, Benutzer und Datenbank `codepath`, Passwort aus `.env`.

## ◼ Fehlersuche

| Problem | Ursache und Lösung |
|---|---|
| `POSTGRES_PASSWORD in .env setzen` beim Start | `.env` fehlt oder der Wert ist leer |
| Anmeldung klappt, nach dem Neuladen wieder abgemeldet | `COOKIE_SECURE=true` ohne HTTPS – auf `false` setzen oder TLS einrichten |
| Rangliste und Mitteilungen aktualisieren sich nicht | Reverse-Proxy puffert `/api/events` (siehe [HTTPS](#-https-und-erreichbarkeit)) |
| Code-Aufgaben: „Übung ohne Punkte“ | Runner nicht eingeschaltet oder nicht erreichbar: `docker compose logs runner` |
| Runner startet nicht: „Die Sandbox funktioniert nicht“ | user namespaces gesperrt – auf Ubuntu die sysctl-Einstellung oben setzen; im Container Seccomp/AppArmor wie in `docker-compose.yml` |
| Admin-Passwort vergessen | Siehe [unten](#admin-passwort-zurücksetzen). `ADMIN_SEED_PASSWORD` wirkt nur beim allerersten Start. |
| Mitgelieferte Aufgabe fehlt nach Update | Wurde sie im Editor endgültig gelöscht, legt CodePath sie bewusst nicht wieder an; ein ZIP-Import mit der Aufgabe holt sie zurück |

### Admin-Passwort zurücksetzen

Passwörter liegen als BCrypt-Hash in der Datenbank. Ein neues lässt sich so setzen (Passwort anpassen, danach in CodePath unter *Profil* erneut ändern):

```bash
HASH=$(docker run --rm httpd:2.4-alpine htpasswd -nbB -C 12 x 'Neues-Passwort-42' | cut -d: -f2)
docker compose exec -T postgres psql -U codepath codepath \
  -c "update users set password_hash = '$HASH' where username = 'admin';"
```

## ◼ Mehrere Schulen und Skalierung

Eine Instanz ist für eine Schule gedacht: Es gibt eine Lehrkräfte-Rolle, und sie sieht alle Kurse. Für mehrere Schulen empfiehlt sich je Schule eine eigene Instanz mit eigener Datenbank. Mehrere Anwendungs-Container hinter einem Load Balancer werden derzeit **nicht** unterstützt: Live-Updates, Login-Bremse und Start-Import gehen von einer Instanz aus.

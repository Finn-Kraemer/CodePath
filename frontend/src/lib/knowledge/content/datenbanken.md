---
title: Datenbanken (SQL)
icon: 🗄️
---

# Datenbanken (SQL)

## Warum Datenbanken?

Während Programme wie Excel gut für einfache Listen sind, stoßen sie bei Millionen von gleichzeitigen Nutzern (wie bei Netflix) an ihre Grenzen. Datenbanken sind darauf spezialisiert, riesige Datenmengen sicher, schnell und für viele Nutzer gleichzeitig zu verwalten.

## SQL Abfragen

SQL ist die Sprache, um Daten zu filtern und zu sortieren.

- **Filtern:** `SELECT * FROM bestellungen WHERE status = 'offen';`
- **Sortieren:** `ORDER BY betrag DESC` (DESC = absteigend, ASC = aufsteigend).
- **Verknüpfen (JOIN):** Da man Daten oft auf mehrere Tabellen aufteilt (z.B. `nutzer` und `bestellungen`), nutzt man einen `JOIN`, um sie über eine gemeinsame ID wieder zusammenzuführen.

## Datenbankdesign (Normalisierung)

Ein gutes Design ist entscheidend. Man sollte niemals Namen als Identifikator nutzen, da sich Namen ändern können.

- **IDs nutzen:** Jeder Datensatz bekommt eine eindeutige `id`.
- **Fremdschlüssel:** In der Tabelle `bestellungen` speichert man nicht den Namen des Kunden, sondern dessen `nutzer_id`. Dies nennt man einen Fremdschlüssel.

### Beispiel-Struktur (Schema)
- **nutzer:** `id`, `name`, `email`
- **bestellungen:** `id`, `nutzer_id`, `produkt`, `betrag`

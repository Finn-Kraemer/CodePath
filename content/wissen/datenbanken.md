---
title: Datenbanken (SQL)
icon: 🗄️
module: datenbanken
---

# Datenbanken (SQL)

Eine **Datenbank** speichert Daten dauerhaft, geordnet und sicher. Mit der Sprache **SQL** (Structured Query Language) fragst du Daten ab, änderst sie oder legst neue an.

---

## 1. Warum Datenbanken?

Eine Tabellenkalkulation wie Excel reicht für eine Klassenliste. Für eine App mit Millionen Nutzern nicht:

| Problem bei Tabellendateien                         | Lösung der Datenbank                                  |
| --------------------------------------------------- | ----------------------------------------------------- |
| Nur eine Person kann gleichzeitig sinnvoll schreiben | **Viele Nutzer gleichzeitig**, ohne sich zu stören    |
| Bei Millionen Zeilen wird alles langsam              | **Indizes** machen Suchen schnell                     |
| Tippfehler und falsche Werte schleichen sich ein     | **Datentypen und Regeln** (z. B. „Preis muss Zahl sein“) |
| Absturz mitten im Speichern → Datei kaputt           | **Transaktionen**: alles oder nichts                  |
| Jeder sieht alles                                    | **Rechte** pro Nutzer und Tabelle                     |

---

## 2. Tabellen, Zeilen, Spalten

Eine relationale Datenbank besteht aus **Tabellen**. Jede **Zeile** ist ein Datensatz, jede **Spalte** ein Attribut mit festem Datentyp.

**Tabelle `nutzer`:**

| id | name  | email            | jahrgang |
| -- | ----- | ---------------- | -------- |
| 1  | Lena  | lena@mail.de     | 2009     |
| 2  | Max   | max@mail.de      | 2008     |
| 3  | Aylin | aylin@mail.de    | NULL     |

- **Primärschlüssel (Primary Key):** eine Spalte, die jede Zeile **eindeutig** kennzeichnet – meist `id`. Namen eignen sich nicht: Sie können doppelt vorkommen und sich ändern.
- **Fremdschlüssel (Foreign Key):** eine Spalte, die auf den Primärschlüssel einer **anderen Tabelle** verweist.

---

## 3. Daten abfragen: SELECT

```sql
SELECT spalten
FROM tabelle
WHERE bedingung
ORDER BY spalte
LIMIT anzahl;
```

| Beispiel                                                    | Bedeutung                                  |
| ----------------------------------------------------------- | ------------------------------------------ |
| `SELECT * FROM nutzer;`                                     | alle Spalten (`*`) aller Zeilen            |
| `SELECT name, email FROM nutzer;`                           | nur zwei Spalten                           |
| `SELECT * FROM nutzer WHERE jahrgang >= 2009;`               | nur Zeilen, die die Bedingung erfüllen     |
| `SELECT * FROM nutzer ORDER BY name ASC;`                   | sortiert, `ASC` = aufsteigend (Standard)   |
| `SELECT * FROM produkte ORDER BY preis DESC LIMIT 3;`       | die drei teuersten Produkte                |

### Bedingungen in WHERE

| Operator                | Beispiel                                     |
| ----------------------- | -------------------------------------------- |
| `=`, `<>` (ungleich)    | `status = 'offen'`                           |
| `<`, `>`, `<=`, `>=`    | `preis > 10`                                 |
| `AND`, `OR`, `NOT`      | `stadt = 'Berlin' AND jahrgang < 2008`       |
| `BETWEEN … AND …`       | `preis BETWEEN 5 AND 20`                     |
| `IN (…)`                | `stadt IN ('Berlin', 'Hamburg')`             |
| `LIKE` mit `%`          | `name LIKE 'M%'` (beginnt mit M)             |
| `IS NULL`, `IS NOT NULL` | `jahrgang IS NULL`                           |

> ⚠️ Texte stehen in **einfachen Anführungszeichen** `'…'`, Zahlen ohne. Jede Anweisung endet mit `;`.

> ⚠️ Bei `AND` und `OR` gemischt: `AND` bindet stärker. Setze Klammern: `WHERE abteilung = 'IT' AND (gehalt > 4000 OR eintritt > 2020)`.

---

## 4. NULL – der unbekannte Wert

`NULL` bedeutet **„unbekannt“ oder „nicht vorhanden“**. Es ist **nicht 0** und **nicht ein leerer Text**.

- Vergleiche mit `NULL` ergeben nie „wahr“: `jahrgang = NULL` findet **nichts**.
- Richtig ist: `WHERE jahrgang IS NULL` bzw. `IS NOT NULL`.
- `COUNT(jahrgang)` zählt nur Zeilen, in denen `jahrgang` nicht `NULL` ist; `COUNT(*)` zählt alle Zeilen.

---

## 5. Zählen und Gruppieren

**Aggregatfunktionen** fassen viele Zeilen zu einem Wert zusammen:

| Funktion     | Ergebnis                |
| ------------ | ----------------------- |
| `COUNT(*)`   | Anzahl der Zeilen       |
| `SUM(spalte)` | Summe                  |
| `AVG(spalte)` | Durchschnitt           |
| `MIN(spalte)`, `MAX(spalte)` | kleinster / größter Wert |

Mit **`GROUP BY`** rechnest du pro Gruppe:

```sql
-- Wie viele Bestellungen hat jeder Status?
SELECT status, COUNT(*) AS anzahl
FROM bestellungen
GROUP BY status;
```

| status     | anzahl |
| ---------- | ------ |
| offen      | 12     |
| versendet  | 30     |

`HAVING` filtert **nach** dem Gruppieren (`WHERE` filtert davor):

```sql
SELECT stadt, COUNT(*) FROM nutzer GROUP BY stadt HAVING COUNT(*) > 10;
```

---

## 6. Tabellen verbinden: JOIN

Daten werden auf mehrere Tabellen verteilt, damit nichts doppelt gespeichert wird. Ein **JOIN** setzt sie über den Schlüssel wieder zusammen.

**Tabelle `bestellungen`:**

| id  | nutzer_id | produkt   | betrag |
| --- | --------- | --------- | ------ |
| 101 | 1         | Kopfhörer | 49.90  |
| 102 | 2         | Maus      | 19.90  |
| 103 | 1         | Kabel     | 5.00   |

```sql
SELECT nutzer.name, bestellungen.produkt, bestellungen.betrag
FROM bestellungen
JOIN nutzer ON bestellungen.nutzer_id = nutzer.id;
```

| name | produkt   | betrag |
| ---- | --------- | ------ |
| Lena | Kopfhörer | 49.90  |
| Max  | Maus      | 19.90  |
| Lena | Kabel     | 5.00   |

- **`JOIN` / `INNER JOIN`:** nur Zeilen mit passendem Partner in beiden Tabellen.
- **`LEFT JOIN`:** alle Zeilen der linken Tabelle, auch ohne Partner (fehlende Werte werden `NULL`). So findest du z. B. Nutzer ohne Bestellung.

**Das Prinzip in Python:** Für jede Bestellung den passenden Nutzer suchen.

```python
nutzer = {1: "Lena", 2: "Max"}                       # id → name
bestellungen = [(101, 1, "Kopfhörer"), (102, 2, "Maus")]

for bestell_id, nutzer_id, produkt in bestellungen:
    print(nutzer[nutzer_id], "kaufte", produkt)
```

---

## 7. Daten ändern: INSERT, UPDATE, DELETE

```sql
-- Neue Zeile anlegen
INSERT INTO nutzer (name, email, jahrgang) VALUES ('Tom', 'tom@mail.de', 2010);

-- Werte ändern
UPDATE nutzer SET jahrgang = 2008 WHERE id = 1;

-- Zeilen löschen
DELETE FROM nutzer WHERE id = 3;
```

> ⚠️ **Nie das `WHERE` vergessen!** `UPDATE nutzer SET jahrgang = 2008;` ändert **alle** Zeilen, `DELETE FROM nutzer;` löscht **alles**. Tipp: Erst mit `SELECT … WHERE …` prüfen, welche Zeilen getroffen werden.

---

## 8. Transaktionen – alles oder nichts

Eine Überweisung besteht aus zwei Schritten: Geld bei A abziehen, Geld bei B gutschreiben. Stürzt der Server dazwischen ab, wäre das Geld verschwunden. Deshalb fasst man beide Schritte zu einer **Transaktion** zusammen:

```sql
BEGIN;
UPDATE konten SET stand = stand - 100 WHERE id = 1;
UPDATE konten SET stand = stand + 100 WHERE id = 2;
COMMIT;      -- beide Änderungen werden gemeinsam gültig
```

Geht etwas schief, macht **`ROLLBACK`** alle Schritte der Transaktion rückgängig. Die Eigenschaften einer Transaktion heißen **ACID**:

| Buchstabe | Bedeutung       | Kurz erklärt                                    |
| --------- | --------------- | ----------------------------------------------- |
| A         | Atomarität      | alles oder nichts                               |
| C         | Konsistenz      | danach sind alle Regeln wieder erfüllt          |
| I         | Isolation       | gleichzeitige Transaktionen stören sich nicht   |
| D         | Dauerhaftigkeit | nach `COMMIT` bleibt die Änderung auch bei Absturz |

---

## 9. Datenbankdesign

### Redundanz vermeiden (Normalisierung)

**Schlecht** – Kundendaten stehen in jeder Bestellung erneut:

| bestell_id | kunde_name | kunde_email  | produkt |
| ---------- | ---------- | ------------ | ------- |
| 1          | Lena       | lena@mail.de | Maus    |
| 2          | Lena       | lena@mail.de | Kabel   |

Ändert Lena ihre E-Mail, muss man jede Zeile anpassen – vergisst man eine, sind die Daten **widersprüchlich** (Anomalie).

**Gut** – jede Information steht genau einmal, verbunden über Schlüssel:

- **kunden:** `id`, `name`, `email`
- **bestellungen:** `id`, `kunde_id` → kunden.id, `produkt`

### Beziehungen

| Art     | Beispiel                                     | Umsetzung                                                                 |
| ------- | -------------------------------------------- | ------------------------------------------------------------------------- |
| **1:1** | Person – Ausweis                             | Fremdschlüssel in einer der beiden Tabellen                               |
| **1:n** | Ein Nutzer hat viele Posts                   | Fremdschlüssel auf der „n“-Seite: `posts.nutzer_id`                        |
| **n:m** | Nutzer liken viele Posts, Posts haben viele Likes | eigene **Zwischentabelle** `likes(nutzer_id, post_id)`               |

**Beispiel Mini-Instagram:**

```
nutzer(id, name, email)
posts(id, nutzer_id → nutzer.id, bild, text, erstellt_am)
likes(nutzer_id → nutzer.id, post_id → posts.id)
follower(folgt_id → nutzer.id, gefolgt_id → nutzer.id)
```

### Tabelle anlegen

```sql
CREATE TABLE posts (
    id INTEGER PRIMARY KEY,
    nutzer_id INTEGER NOT NULL REFERENCES nutzer(id),
    text VARCHAR(500),
    erstellt_am DATE
);
```

Häufige Datentypen: `INTEGER` (ganze Zahl), `DECIMAL`/`REAL` (Kommazahl), `VARCHAR(n)`/`TEXT` (Text), `DATE` (Datum), `BOOLEAN` (wahr/falsch).

---

## 10. Daten in Python filtern

Datensätze kommen in Programmen oft als Liste von Dictionaries an. Filtern funktioniert wie ein `WHERE`:

```python
bestellungen = [
    {"id": 1, "status": "offen", "betrag": 20},
    {"id": 2, "status": "versendet", "betrag": 35},
    {"id": 3, "status": "offen", "betrag": 12},
]

for b in bestellungen:
    if b["status"] == "offen":
        print(b["id"], b["betrag"])
```

Das entspricht `SELECT id, betrag FROM bestellungen WHERE status = 'offen';`

> ⚠️ Baue SQL nie aus Benutzereingaben per Text zusammen – das ermöglicht **SQL-Injection** (siehe Artikel IT-Sicherheit). Nutze Platzhalter: `cursor.execute("SELECT * FROM nutzer WHERE name = ?", (name,))`.

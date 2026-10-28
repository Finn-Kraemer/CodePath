---
title: Web-Entwicklung
icon: 🌐
module: web-entwicklung
---

# Web-Entwicklung

Jede Webseite besteht aus drei Bausteinen, die zusammenarbeiten:

| Sprache        | Aufgabe       | Vergleich mit einem Haus        |
| -------------- | ------------- | ------------------------------- |
| **HTML**       | Inhalt und Struktur | Mauern und Räume          |
| **CSS**        | Aussehen und Layout | Farbe, Tapete, Möbel      |
| **JavaScript** | Verhalten und Interaktion | Strom, Lichtschalter, Türklingel |

---

## 1. Client und Server

Das Web funktioniert nach dem **Request-Response-Prinzip** (Anfrage und Antwort):

- Der **Client** (dein Browser) **fragt**: „Gib mir die Seite `/shop`.“
- Der **Server** (ein Rechner im Internet) **antwortet** mit HTML, Bildern oder Daten.

Der Server meldet sich nie von selbst – er wartet immer auf Anfragen.

### Was passiert, wenn du eine Adresse eingibst?

1. Du gibst die URL ein, der Browser fragt per **DNS** nach der IP-Adresse des Servers.
2. Der Browser baut eine Verbindung auf und sendet eine **HTTP-Anfrage**.
3. Der Server **verarbeitet** die Anfrage (liest z. B. Daten aus einer Datenbank).
4. Der Server **erzeugt die Antwort** (HTML) und schickt sie mit einem Statuscode zurück.
5. Der Browser **empfängt** die Daten und lädt weitere Dateien nach (CSS, JavaScript, Bilder).
6. Der Browser **rendert** die Seite: Er baut sie auf und zeigt sie an.

---

## 2. Aufbau einer URL

```
https://www.schule.de:443/klassen/8a/stundenplan.html?woche=12#montag

zerlegt:
https://  www.schule.de :443 /klassen/8a/stundenplan.html ?woche=12 #montag
Protokoll Domain        Port Pfad                         Parameter Anker
```

| Teil          | Bedeutung                                                            |
| ------------- | -------------------------------------------------------------------- |
| **Protokoll** | wie kommuniziert wird: `https://` (verschlüsselt) oder `http://`      |
| **Domain**    | Name des Servers; `www` ist eine Subdomain, `.de` die Top-Level-Domain |
| **Port**      | meist weggelassen (Standard: 80 für HTTP, 443 für HTTPS)              |
| **Pfad**      | welche Seite oder Datei auf dem Server                                |
| **Parameter** | zusätzliche Werte nach `?`, mehrere mit `&` getrennt: `?suche=hose&seite=2` |
| **Anker**     | springt zu einer Stelle auf der Seite (wird nicht an den Server geschickt) |

**Typische Fehler:** fehlendes Protokoll, wenn die Adresse z. B. in einem Link oder Programm verwendet wird; Leerzeichen in der URL (werden als `%20` codiert); Tippfehler in der Domain; `\` statt `/`.

---

## 3. HTML – die Struktur

HTML besteht aus **Tags** in spitzen Klammern. Die meisten haben ein Start- und ein End-Tag: `<p>Text</p>`. Tags können **Attribute** haben: `<a href="kontakt.html">`.

### Grundgerüst

```html
<!DOCTYPE html>
<html lang="de">
  <head>
    <meta charset="UTF-8">
    <title>Titel im Browser-Tab</title>
    <link rel="stylesheet" href="style.css">
  </head>
  <body>
    <h1>Hauptüberschrift</h1>
    <p>Ein Absatz mit Text.</p>
  </body>
</html>
```

- **`<head>`**: Infos über die Seite (Titel, Zeichensatz, CSS-Dateien) – nicht direkt sichtbar.
- **`<body>`**: alles, was auf der Seite angezeigt wird.

### Wichtige Tags

| Tag                           | Bedeutung                                         |
| ----------------------------- | ------------------------------------------------- |
| `<h1>` … `<h6>`               | Überschriften, `<h1>` am größten (nur eine pro Seite) |
| `<p>`                         | Absatz                                            |
| `<a href="url">Text</a>`      | Link                                              |
| `<img src="bild.jpg" alt="Beschreibung">` | Bild (kein End-Tag); `alt` für Screenreader und falls das Bild fehlt |
| `<ul>` / `<ol>` mit `<li>`    | Aufzählung / nummerierte Liste                    |
| `<strong>`, `<em>`            | wichtig (fett) / betont (kursiv)                  |
| `<br>`                        | Zeilenumbruch                                     |
| `<div>`, `<span>`             | neutrale Container für Blöcke / Text im Fließtext |
| `<header>`, `<nav>`, `<main>`, `<footer>` | Seitenbereiche mit Bedeutung          |
| `<table>`, `<tr>`, `<th>`, `<td>` | Tabelle, Zeile, Kopfzelle, Zelle              |

> **Wichtig:** Der Browser ignoriert zusätzliche Leerzeichen und Zeilenumbrüche im HTML-Code. Für einen neuen Absatz brauchst du `<p>`, für einen Umbruch `<br>`. Falsch verschachtelte oder vergessene End-Tags führen oft zu seltsamer Darstellung.

### Formulare

```html
<form action="/anmelden" method="post">
  <label for="name">Name:</label>
  <input type="text" id="name" name="name" required>

  <label for="mail">E-Mail:</label>
  <input type="email" id="mail" name="mail">

  <label for="pw">Passwort:</label>
  <input type="password" id="pw" name="pw">

  <label><input type="checkbox" name="agb"> AGB gelesen</label>

  <select name="klasse">
    <option>8a</option>
    <option>8b</option>
  </select>

  <textarea name="nachricht"></textarea>
  <button type="submit">Absenden</button>
</form>
```

| `type`      | Eingabe                                    |
| ----------- | ------------------------------------------ |
| `text`      | einzeiliger Text                           |
| `email`     | E-Mail-Adresse (Browser prüft das Format)  |
| `password`  | Eingabe wird als Punkte angezeigt          |
| `number`    | Zahl                                       |
| `date`      | Datum mit Kalender                         |
| `checkbox`  | Häkchen (mehrere wählbar)                  |
| `radio`     | Auswahl (nur eine aus einer Gruppe mit gleichem `name`) |

Das Attribut `name` bestimmt, unter welchem Namen der Wert an den Server geht. `label` mit `for` verbindet Beschriftung und Feld.

---

## 4. CSS – das Aussehen

CSS-Regeln bestehen aus einem **Selektor** (wen betrifft es?) und **Eigenschaften** (was ändert sich?):

```css
p {
  color: navy;          /* Textfarbe */
  font-size: 18px;      /* Schriftgröße */
}
```

Eine Regel mit `p` gilt für **alle** `<p>`-Elemente der Seite.

### Selektoren

| Selektor         | Trifft …                                    | HTML                      |
| ---------------- | ------------------------------------------- | ------------------------- |
| `h1`             | alle `<h1>`                                 | `<h1>`                    |
| `.wichtig`       | alle Elemente mit dieser **Klasse**         | `<p class="wichtig">`     |
| `#kopf`          | das eine Element mit dieser **ID**          | `<div id="kopf">`         |
| `nav a`          | alle Links **innerhalb** von `<nav>`        |                           |
| `a:hover`        | Links, über denen die Maus ist              |                           |

Treffen mehrere Regeln zu, gewinnt die **spezifischere** (ID vor Klasse vor Tag); bei gleicher Spezifität die **spätere**.

### Häufige Eigenschaften

| Eigenschaft                          | Wirkung                                    |
| ------------------------------------ | ------------------------------------------ |
| `color`, `background-color`          | Text- und Hintergrundfarbe                 |
| `font-size`, `font-family`, `font-weight` | Schriftgröße, -art, -stärke           |
| `text-align: center`                 | Text zentrieren                            |
| `width`, `height`                    | Breite, Höhe                               |
| `margin`                             | Außenabstand                               |
| `padding`                            | Innenabstand                               |
| `border: 1px solid black`            | Rahmen                                     |
| `display: none`                      | Element ausblenden                         |
| `display: flex`                      | Kinder nebeneinander anordnen (Flexbox)    |

### Das Box-Modell

Jedes Element ist ein Kasten aus Inhalt, Innenabstand, Rahmen und Außenabstand:

```
┌─────────── margin ───────────┐
│ ┌───────── border ─────────┐ │
│ │ ┌─────── padding ──────┐ │ │
│ │ │       Inhalt         │ │ │
│ │ └──────────────────────┘ │ │
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

### CSS einbinden

Am besten als eigene Datei im `<head>`: `<link rel="stylesheet" href="style.css">`. Möglich, aber unübersichtlicher: `<style>` im `<head>` oder `style="…"` direkt am Element.

---

## 5. Responsive Design

Eine Webseite soll auf Handy, Tablet und PC gut aussehen. Dafür:

1. **Viewport** im `<head>` setzen, sonst zeigt das Handy eine verkleinerte Desktop-Seite:
   ```html
   <meta name="viewport" content="width=device-width, initial-scale=1">
   ```
2. **Flexible Größen** statt fester Pixel: `width: 100%`, `max-width: 800px`, Bilder mit `max-width: 100%`.
3. **Media Queries**: CSS-Regeln, die nur ab bzw. bis zu einer Bildschirmbreite gelten.

```css
.spalten { display: flex; }

/* Auf schmalen Bildschirmen untereinander statt nebeneinander */
@media (max-width: 600px) {
  .spalten { flex-direction: column; }
}
```

**Mobile First:** zuerst für kleine Bildschirme gestalten, dann für große erweitern.

---

## 6. JavaScript – das Verhalten

JavaScript läuft **im Browser** und macht Seiten interaktiv: auf Klicks reagieren, Inhalte ändern, Daten nachladen.

```html
<p id="ausgabe">Noch nicht geklickt</p>
<button id="knopf">Klick mich</button>

<script>
  const knopf = document.getElementById("knopf");
  const ausgabe = document.getElementById("ausgabe");
  let zaehler = 0;

  knopf.addEventListener("click", () => {
    zaehler = zaehler + 1;
    ausgabe.textContent = "Geklickt: " + zaehler + "-mal";
  });
</script>
```

| Baustein                          | Bedeutung                                              |
| --------------------------------- | ------------------------------------------------------ |
| `document.getElementById("id")`   | holt ein HTML-Element über seine ID                    |
| `document.querySelector(".x")`    | holt das erste Element zu einem CSS-Selektor           |
| `addEventListener("click", …)`    | führt Code aus, **wenn** das Ereignis eintritt         |
| `element.textContent = "…"`       | ändert den Text eines Elements                         |
| `element.style.color = "red"`     | ändert CSS direkt                                      |
| `element.classList.add("aktiv")`  | fügt eine CSS-Klasse hinzu                             |
| `let` / `const`                   | veränderbare / unveränderbare Variable                 |
| `console.log(wert)`               | Ausgabe in der Browser-Konsole (zum Testen)            |

> Der Code in der Funktion läuft **nicht sofort**, sondern erst bei jedem Klick. Die Seite wird dabei nicht neu geladen – nur das Element ändert sich (über das **DOM**, die Baumstruktur der Seite im Browser).

---

## 7. HTTP: Methoden und Statuscodes

### Methoden

| Methode    | Zweck                       | Beispiel                       |
| ---------- | --------------------------- | ------------------------------ |
| **GET**    | Daten **abrufen**           | Seite laden, Suche             |
| **POST**   | Daten **senden** / anlegen  | Formular, Login, Bestellung    |
| **PUT** / **PATCH** | Daten ändern       | Profil bearbeiten              |
| **DELETE** | Daten löschen               | Beitrag entfernen              |

**GET oder POST?** Bei GET stehen die Daten **in der URL** (`/suche?q=hose`). URLs landen im Browserverlauf, in Lesezeichen, in Server-Logs und sind über die Schulter lesbar. Bei POST stehen die Daten im **Body** der Anfrage. Deshalb gehören **Passwörter und persönliche Daten nie in GET**, sondern per POST – und zwar über **HTTPS**, damit sie auch verschlüsselt sind.

### Statuscodes

Die erste Ziffer verrät die Art der Antwort:

| Bereich | Bedeutung              | Wichtige Codes                                                        |
| ------- | ---------------------- | --------------------------------------------------------------------- |
| **2xx** | Erfolg                 | **200** OK, **201** Created (angelegt), **204** No Content            |
| **3xx** | Weiterleitung          | **301** dauerhaft umgezogen, **302** vorübergehend umgezogen, **304** nicht geändert (Cache nutzen) |
| **4xx** | Fehler beim **Client** | **400** Bad Request (fehlerhafte Anfrage), **401** nicht angemeldet, **403** Forbidden (keine Berechtigung), **404** Not Found, **429** zu viele Anfragen |
| **5xx** | Fehler beim **Server** | **500** Internal Server Error, **502** Bad Gateway, **503** Service Unavailable (überlastet/Wartung) |

> **Merksatz:** 4xx – **du** hast etwas falsch gemacht. 5xx – **der Server** hat ein Problem.

---

## 8. APIs und JSON

Eine **API** (Programmierschnittstelle) ist eine Adresse, über die Programme Daten austauschen – ohne HTML, meist im Format **JSON**:

```json
{
  "stadt": "Hamburg",
  "aktuell": {
    "temperatur": 14,
    "wetter": "bewölkt"
  },
  "vorhersage": [
    { "tag": "Mo", "max": 16 },
    { "tag": "Di", "max": 19 }
  ]
}
```

- `{ }` = Objekt mit **Schlüssel-Wert-Paaren** (in Python: Dictionary)
- `[ ]` = Liste (in Python: Liste)
- Schlüssel stehen immer in **doppelten Anführungszeichen**.
- Werte: Text, Zahl, `true`/`false`, `null`, Objekt oder Liste

### JSON in Python

```python
import json

antwort = '{"stadt": "Hamburg", "aktuell": {"temperatur": 14}, "vorhersage": [{"tag": "Mo", "max": 16}]}'
daten = json.loads(antwort)              # Text → Dictionary

print(daten["stadt"])                    # → Hamburg
print(daten["aktuell"]["temperatur"])    # → 14   (verschachtelt: Schritt für Schritt hinein)
print(daten["vorhersage"][0]["max"])     # → 16   (erstes Listenelement, dann Schlüssel)

text = json.dumps(daten)                 # Dictionary → Text
```

**Verschachtelte Werte finden:** Lies von außen nach innen. Bei `{ }` folgt ein Schlüssel in `["…"]`, bei `[ ]` ein Index in `[0]`.

In JavaScript holt man Daten mit `fetch`:

```javascript
const antwort = await fetch("/api/wetter?stadt=Hamburg");
const daten = await antwort.json();
console.log(daten.aktuell.temperatur);
```

### Eine REST-API entwerfen

REST-APIs nutzen **Ressourcen** (Substantive im Plural) als Pfade und die **HTTP-Methode** als Verb:

| Methode + Pfad         | Bedeutung                       | Antwort bei Erfolg |
| ---------------------- | ------------------------------- | ------------------ |
| `GET /buecher`         | alle Bücher abrufen             | 200                |
| `GET /buecher/42`      | Buch Nr. 42 abrufen             | 200 (oder 404)     |
| `POST /buecher`        | neues Buch anlegen              | 201                |
| `PUT /buecher/42`      | Buch 42 ändern                  | 200                |
| `DELETE /buecher/42`   | Buch 42 löschen                 | 204                |

Fehlerhafte Eingaben beantwortet die API mit **400**, fehlende Anmeldung mit **401**, fehlende Rechte mit **403**.

---

## 9. Die Entwicklerwerkzeuge des Browsers

Mit **F12** (oder Rechtsklick → *Untersuchen*) öffnest du die Entwicklerwerkzeuge (DevTools). Sie zeigen, was „unter der Haube“ einer Webseite passiert. Ausführlich erklärt im Artikel **Browser-Entwicklerwerkzeuge**.

| Werkzeug                   | Wofür                                                        |
| -------------------------- | ------------------------------------------------------------ |
| **Elemente / Inspektor**   | HTML und CSS ansehen und live ändern                         |
| **Konsole**                | Fehlermeldungen lesen, JavaScript ausführen                  |
| **Netzwerk**               | alle Anfragen mit Methode, Statuscode, Headern und Antwort   |
| **Anwendung / Speicher**   | Cookies, localStorage, sessionStorage, IndexedDB             |

---

## 10. Eine eigene Webseite planen

1. **Ziel und Zielgruppe:** Wer besucht die Seite, was soll sie dort finden?
2. **Struktur:** Welche Seiten und Bereiche gibt es? Navigation skizzieren.
3. **HTML:** Inhalte mit passenden Tags auszeichnen (`header`, `nav`, `main`, `footer`, Überschriften der Reihe nach).
4. **CSS:** einheitliche Farben und Schriften, Abstände, responsives Layout.
5. **Barrierefreiheit:** `alt`-Texte für Bilder, ausreichender Kontrast, beschriftete Formularfelder, sinnvolle Überschriftenreihenfolge.
6. **Testen:** auf verschiedenen Geräten und Browsern, alle Links prüfen.

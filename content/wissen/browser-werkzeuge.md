---
title: Browser-Entwicklerwerkzeuge
icon: 🛠️
---

# Browser-Entwicklerwerkzeuge (DevTools)

Jeder moderne Browser hat eingebaute **Entwicklerwerkzeuge**. Mit ihnen schaust du einer Webseite unter die Haube: Wie ist sie aufgebaut? Welche Daten lädt sie? Warum funktioniert etwas nicht? Für die Aufgaben im **Browser-Labor** brauchst du genau diese Werkzeuge.

> 💻 Die Werkzeuge gibt es nur am **PC oder Laptop** (Chrome, Edge oder Firefox), nicht auf Tablet oder Handy.

---

## 1. Öffnen

| Weg                                 | Wirkung                                        |
| ----------------------------------- | ---------------------------------------------- |
| **F12**                             | öffnet und schließt die Werkzeuge              |
| **Strg + Umschalt + I** (Mac: ⌥ + ⌘ + I) | ebenfalls                                  |
| **Rechtsklick → Untersuchen**       | öffnet den Inspektor direkt beim angeklickten Element |
| **Strg + Umschalt + J** (Mac: ⌥ + ⌘ + J) | öffnet direkt die Konsole (Chrome/Edge)    |
| **Strg + U**                        | zeigt den Seitenquelltext in einem neuen Tab   |

Oben in den Werkzeugen wählst du die Bereiche über Reiter. Die Namen unterscheiden sich je nach Browser leicht:

| Chrome / Edge   | Firefox            |
| --------------- | ------------------ |
| Elements        | Inspektor          |
| Console         | Konsole            |
| Sources         | Debugger           |
| Network         | Netzwerkanalyse    |
| Application     | Web-Speicher       |

---

## 2. Inspektor (Elements): HTML und CSS

Hier siehst du die Seite als **DOM-Baum** – das HTML, so wie der Browser es gerade verwendet (inklusive allem, was JavaScript nachträglich verändert hat).

- **Element auswählen:** Mit dem Pfeil-Symbol oben links (oder Rechtsklick → *Untersuchen*) auf ein Element der Seite klicken.
- **Aufklappen:** Mit den kleinen Dreiecken öffnest du verschachtelte Elemente.
- **Attribute lesen:** `id`, `class`, `data-…`-Attribute, `hidden`, `disabled` – oft stecken hier Informationen, die man auf der Seite nicht sieht.
- **Live bearbeiten:** Doppelklick auf Text oder Attribute ändert sie sofort (nur bei dir, bis zum Neuladen). Ein Attribut wie `disabled` oder `hidden` kannst du so auch entfernen.
- **Suchen:** **Strg + F** im Inspektor sucht im HTML.

### Die CSS-Seite

Rechts (bzw. unten) siehst du die **CSS-Regeln** des ausgewählten Elements:

- **Styles / Regeln:** welche Regeln greifen, durchgestrichene werden von anderen überschrieben. Häkchen schalten Eigenschaften an und aus.
- **Computed / Berechnet:** die endgültigen Werte nach allen Regeln.
- **Pseudo-Elemente** wie `::before` und `::after` erzeugen Inhalt über CSS (`content: "…"`). Sie erscheinen im Baum als eigene Einträge. Ihr Text kann unsichtbar sein (z. B. gleiche Farbe wie der Hintergrund, `font-size: 0`, `display: none`) und steht dann trotzdem in der Regel.

---

## 3. Konsole (Console): Meldungen und JavaScript

Die Konsole hat zwei Aufgaben:

1. **Meldungen lesen:** Fehler (rot), Warnungen (gelb) und Ausgaben, die die Seite mit `console.log()` schreibt. Wenn eine Seite „kaputt“ ist, steht hier oft schon, warum.
2. **JavaScript ausführen:** Tippe einen Befehl ein und drücke **Enter**.

```javascript
2 + 3                                  // → 5
document.title                         // Titel der Seite
document.querySelector("h1").textContent   // Text der ersten Überschrift
document.querySelectorAll("li").length     // Anzahl der Listeneinträge
```

**Funktionen der Seite aufrufen:** Hat die Seite eine Funktion definiert, kannst du sie direkt aufrufen, z. B. `pruefen("abc")`. Tippst du nur den Namen ohne Klammern ein, zeigt die Konsole die Funktion an. Mit **Tab** oder den Vorschlägen beim Tippen findest du verfügbare Namen.

**Tipps:**

- **Pfeil nach oben** holt den letzten Befehl zurück.
- Mit **Umschalt + Enter** schreibst du mehrere Zeilen.
- Chrome fragt beim ersten Einfügen von Code manchmal nach einer Bestätigung („allow pasting“) – das ist ein Schutz vor Betrügern, die Leute dazu bringen wollen, fremden Code einzufügen.

> ⚠️ Füge **niemals** Code in die Konsole einer echten Seite ein, den dir jemand schickt („Kopier das, dann bekommst du Gratis-Coins“) – damit kann er dein Konto übernehmen.

### Anfragen von Hand senden: `fetch`

Mit `fetch` schickst du aus der Konsole selbst HTTP-Anfragen an den Server:

```javascript
// GET – Daten abrufen
const antwort = await fetch("/api/daten");
antwort.status                 // Statuscode, z. B. 200
await antwort.json()           // Antwort als JavaScript-Objekt
antwort.headers.get("X-Info")  // einen Header der Antwort lesen

// POST – Daten senden (als JSON)
const r = await fetch("/api/bestellung", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ artikel: 3, menge: 2 })
});
await r.json()
```

Achte darauf, dass Zahlen **ohne** Anführungszeichen geschrieben werden: `{"menge": 2}` ist eine Zahl, `{"menge": "2"}` ein Text.

---

## 4. Netzwerkanalyse (Network): Was lädt die Seite?

Hier erscheint **jede Anfrage**, die die Seite stellt – HTML, CSS, Bilder, Skripte und Daten von APIs.

> Öffne den Reiter **vor** dem Laden bzw. lade die Seite danach neu (**F5**), sonst fehlen die ersten Anfragen.

| Spalte / Bereich      | Was du siehst                                           |
| --------------------- | ------------------------------------------------------- |
| **Name / Datei**      | angefragte Adresse                                      |
| **Status**            | Statuscode: 200 OK, 404 nicht gefunden, 400 fehlerhafte Anfrage, 410 nicht mehr verfügbar … |
| **Methode**           | GET, POST …                                             |
| **Typ**               | document, script, fetch/xhr (Daten), img …              |

Klickst du auf eine Anfrage, siehst du Details:

- **Headers / Kopfzeilen:** Anfrage- und **Antwort-Header**. Server schicken hier manchmal zusätzliche Informationen mit (eigene Header beginnen oft mit `X-`).
- **Payload / Anfrage:** die gesendeten Daten (bei POST).
- **Preview / Response / Antwort:** was der Server zurückgeschickt hat – auch bei Fehlern wie 404 kann die Antwort Inhalt haben!

Über den Filter **Fetch/XHR** blendest du alles außer Datenanfragen aus. Rechtsklick auf eine Anfrage → *Copy as fetch* kopiert sie als `fetch`-Befehl für die Konsole.

---

## 5. Web-Speicher (Application / Storage)

Webseiten können Daten **im Browser** speichern. Im Reiter *Application* (Chrome/Edge) bzw. *Web-Speicher* (Firefox) siehst du links die Speicherarten:

| Speicher           | Lebensdauer                                  | Typischer Inhalt                            |
| ------------------ | -------------------------------------------- | ------------------------------------------- |
| **Cookies**        | bis zum Ablaufdatum; werden bei jeder Anfrage an den Server mitgeschickt | Anmeldung (Session), Einstellungen |
| **localStorage**   | dauerhaft, bis man es löscht                 | Einstellungen, Spielstände                  |
| **sessionStorage** | nur solange der Tab offen ist                | Warenkorb, Formulareingaben                 |
| **IndexedDB**      | dauerhaft, kleine Datenbank im Browser       | größere oder strukturierte Daten, offline   |

Einträge lassen sich dort ansehen, ändern, hinzufügen (Doppelklick in eine leere Zeile) und löschen. Oder per Konsole:

```javascript
localStorage.getItem("modus")          // Wert lesen
localStorage.setItem("modus", "an")    // Wert setzen
localStorage.removeItem("modus")       // Wert löschen
sessionStorage.getItem("warenkorb")
```

Viele Seiten lesen den Speicher nur beim Laden – nach einer Änderung also **neu laden**.

---

## 6. Daten entschlüsseln: Kodierungen erkennen

Daten im Speicher oder in Antworten sind oft **kodiert** (nicht verschlüsselt – jeder kann sie zurückwandeln):

| Kodierung        | Erkennungsmerkmal                                       | Zurückwandeln in der Konsole                |
| ---------------- | ------------------------------------------------------- | ------------------------------------------- |
| **Base64**       | Buchstaben, Ziffern, `+`, `/`, oft `=` am Ende, z. B. `SGFsbG8=` | `atob("SGFsbG8=")` → `"Hallo"` (umgekehrt: `btoa`) |
| **JSON-Text**    | beginnt mit `{` oder `[`                                | `JSON.parse(text)`                          |
| **URL-Kodierung** | `%20`, `%C3%A4` …                                      | `decodeURIComponent(text)`                  |
| **Rückwärts**    | Text ergibt rückwärts gelesen Sinn                      | `text.split("").reverse().join("")`         |

---

## 7. Quelltext und Debugger (Sources)

Im Reiter *Sources* (Firefox: *Debugger*) findest du alle **JavaScript-Dateien** der Seite. Lies sie, um zu verstehen, was die Seite tut: Welche Adressen ruft sie ab? Welche Werte im Speicher prüft sie? Welche Funktionen gibt es?

- **Strg + P** sucht nach Dateinamen, **Strg + Umschalt + F** durchsucht alle Dateien.
- `{ }` unten links formatiert zusammengequetschten Code lesbar („Pretty print“).
- Ein Klick auf eine Zeilennummer setzt einen **Haltepunkt**: Das Programm stoppt dort, und du kannst Variablen ansehen.

---

## 8. Vorgehen bei der Fehlersuche

1. **Konsole:** Gibt es Fehlermeldungen oder Hinweise?
2. **Netzwerk:** Welche Anfragen schlagen fehl (rot, 4xx/5xx)? Was steht in Antwort und Headern?
3. **Quelltext:** Was erwartet das Skript? Gibt es versteckte Funktionen oder Schalter?
4. **Inspektor:** Gibt es ausgeblendete oder deaktivierte Elemente, besondere Attribute oder CSS-Regeln?
5. **Speicher:** Was hat die Seite gespeichert, und was passiert, wenn man es ändert?
6. **Konsole:** Mit `fetch` und den Funktionen der Seite das Gefundene ausprobieren.

Oft führt eine Spur zur nächsten – notiere dir unterwegs, was du gefunden hast.

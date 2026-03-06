---
title: Web-Entwicklung
icon: 🌐
---

# Web-Entwicklung

## Das Web-Prinzip

Die Kommunikation im Web folgt dem **Request-Response** (Anfrage-Antwort) Schema:
1. **Request:** Dein Browser (Client) fragt einen Server nach einer Seite (z.B. "Gib mir die Startseite von YouTube").
2. **Response:** Der Server sendet die gewünschten Daten zurück.

### URLs
Eine URL muss vollständig sein, um zu funktionieren. Das **Protokoll** (meist `https://`) ist zwingend erforderlich, damit der Browser weiß, wie er verschlüsselt kommunizieren soll.

## HTTP-Statuscodes

Der Server sendet mit jeder Antwort eine Nummer, die den Status angibt:
- **200 (OK):** Alles hat geklappt.
- **301 (Moved):** Die Seite ist dauerhaft umgezogen.
- **404 (Not Found):** Die Seite wurde nicht gefunden.
- **500 (Internal Server Error):** Ein Fehler auf dem Server ist aufgetreten.

## HTML Struktur

Jede Webseite folgt einem Grundgerüst:
```html
<!DOCTYPE html>
<html>
  <head>
    <title>Titel der Seite</title>
  </head>
  <body>
    <h1>Hauptüberschrift</h1>
    <p>Ein Absatz mit Text.</p>
    <a href="https://google.de">Ein Link</a>
  </body>
</html>
```

## APIs & JSON

Web-Apps tauschen Daten oft im **JSON-Format** aus. In Python greift man darauf wie auf ein Dictionary zu:
```python
# Die Variable 'data' enthält die Antwort vom Server
print(data["stadt"]) # Zugriff auf den Wert des Schlüssels "stadt"
```

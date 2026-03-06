---
title: Cloud & Netzwerke
icon: ☁️
---

# Cloud & Netzwerke

## Datenübertragung (Pakete)

Wenn du eine große Datei (z.B. ein Video) verschickst, wird diese nicht am Stück übertragen. Sie wird in viele kleine **Pakete** aufgeteilt. Jedes Paket reist einzeln durch das Internet und wird beim Empfänger wieder in der richtigen Reihenfolge zusammengesetzt.

## Cloud vs. Lokal

Ein lokaler Server steht direkt bei dir (z.B. in der Schule). Du musst dich um alles selbst kümmern.
Die **Cloud** bietet Vorteile:
- **Wartung:** Der Anbieter spielt Sicherheitsupdates ein.
- **Backups:** Deine Daten werden automatisch gesichert.
- **Kosten:** Du zahlst nur das, was du wirklich nutzt.

## Netzwerkanalyse in Python

Man kann Netzwerke mit kleinen Programmen überwachen, zum Beispiel um die durchschnittliche Antwortzeit (Ping) zu berechnen:

```python
ping_zeiten = [12, 45, 8, 23]
gesamt = 0
for zeit in ping_zeiten:
    gesamt += zeit

durchschnitt = gesamt / len(ping_zeiten)
print(f"Durchschnitt: {durchschnitt} ms")
```

## DNS (Domain Name System)

DNS ist das Telefonbuch des Internets. Da sich Computer nur Zahlen (IP-Adressen) merken können, wir Menschen aber Namen bevorzugen, übersetzt das DNS Namen wie `google.de` in die passende IP wie `142.250.185.163`.

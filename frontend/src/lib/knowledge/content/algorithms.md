---
title: Algorithmen
icon: 🧩
---

# Algorithmen & Datenstrukturen

Ein Algorithmus ist einfach nur eine Schritt-für-Schritt-Anleitung zur Lösung eines Problems. Wie ein Kochrezept, das garantiert immer zum richtigen Ergebnis führt.

## Bekannte Algorithmen

### Suchen: Linear vs. Binär
Stell dir vor, du suchst ein Wort in einem Wörterbuch:
- **Lineare Suche:** Du fängst auf Seite 1 an und liest jedes Wort, bis du es findest. Sehr langsam.
- **Binäre Suche:** Du schlägst das Buch in der Mitte auf. Ist dein Wort weiter vorne im Alphabet? Dann ignorierst du die rechte Hälfte komplett. Du halbierst den Suchbereich immer wieder. Extrem schnell, funktioniert aber nur bei *sortierten* Listen.

### Sortieren
Wie ordnet man eine Liste von Zahlen? Ein einfacher (aber langsamer) Algorithmus ist **Bubble Sort**: Er vergleicht immer zwei benachbarte Zahlen und tauscht sie, wenn die linke größer ist. Das macht er so lange, bis die Liste sortiert ist.

## Datenstrukturen

Wie speichern wir Daten effizient?
- **Arrays (Listen):** Elemente liegen direkt nebeneinander. Super, um schnell das 5. Element abzurufen. Schlecht, wenn man in der Mitte etwas Neues einfügen will (alle anderen müssen nach rechts rutschen).
- **Dictionaries / Hash Maps:** Speichern Daten als Schlüssel-Wert-Paare (z. B. `"Name" -> "Max"`). Extrem schnell beim Nachschlagen.
- **Bäume (Trees):** Wie ein Stammbaum. Perfekt für Hierarchien oder das Dateisystem deines Computers.

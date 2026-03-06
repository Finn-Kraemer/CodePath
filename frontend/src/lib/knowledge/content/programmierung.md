---
title: Programmierung
icon: 🐍
---

# Python Programmierung

Variablen sind wie Boxen, in denen man Daten speichern kann. Man gibt ihnen einen Namen und weist ihnen mit `=` einen Wert zu.

```python
name = "Max"
alter = 15
ist_bereit = True
```

**Wichtig:** Python achtet streng auf Groß- und Kleinschreibung (Case Sensitivity). Eine Variable `Zahl` ist etwas anderes als `zahl`. Das gilt auch für Funktionen: `print()` funktioniert, `Print()` hingegen führt zu einem Fehler.

## Datentypen

Es gibt verschiedene Arten von Daten:
- **String (Text):** `"Hallo"` (immer in Anführungszeichen)
- **Integer (Ganzzahl):** `42`
- **Float (Kommazahl):** `3.14`
- **Boolean (Wahrheitswert):** `True` oder `False`

## Funktionen

Funktionen sind Code-Blöcke, die eine bestimmte Aufgabe erledigen. Sie starten mit `def` und können Werte mit `return` zurückgeben.

```python
def flaeche(breite, hoehe):
    return breite * hoehe

print(flaeche(4, 5)) # Gibt 20 aus
```

## Schleifen (Loops)

Mit einer `for`-Schleife kannst du Code mehrmals ausführen. Die `range()`-Funktion hilft dabei, eine Zahlenfolge zu erzeugen.

```python
# range(stop) -> 0 bis stop-1
for i in range(5):
    print("*") # Druckt 5 Sternchen

# range(start, stop, step)
for i in range(10, 0, -1):
    print(i) # Zählt von 10 bis 1 rückwärts
```

## Logik & Modulo

Um zu prüfen, ob eine Zahl durch eine andere teilbar ist, nutzt man den Modulo-Operator `%`. Er gibt den Rest einer Division zurück.
- `10 % 2` ist `0` (10 ist durch 2 teilbar)
- `10 % 3` ist `1` (Rest 1)

Dies ist die Basis für Aufgaben wie **FizzBuzz**, bei denen man Zahlen auf ihre Teilbarkeit prüft.

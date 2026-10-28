---
title: Programmierung
icon: 🐍
module: programmierung
---

# Python Programmierung

## 1. Algorithmen & Darstellungsformen

Bevor man Code schreibt, plant man – mit einem **Algorithmus**: einer eindeutigen, endlichen Schritt-für-Schritt-Anleitung zur Lösung eines Problems. Einen Algorithmus kann man auf drei Arten beschreiben:

### Umgangssprachliche Beschreibung

Die einfachste Form – aber oft ungenau und missverständlich:

> Öffne die Kartonverpackung und entnehme die Pizza. Falls die Pizza in Folie eingepackt ist, entferne die Folie. Schalte den Ofen ein, lege die Pizza in den Ofen und sobald die Pizza fertig ist, hole sie aus dem Ofen.

### Pseudocode

**Besser strukturiert als reine Prosa, aber weniger detailliert als eine Programmiersprache.** Pseudocode ist sprachunabhängig – er beschreibt die Logik ohne gültige Syntax einhalten zu müssen. Schlüsselwörter wie `WENN` und `SOLANGE` machen die Struktur klar.

```
oeffne Verpackung
WENN Pizza in Folie eingepackt
    entferne Folie
schalte Ofen ein
lege die Pizza auf das Blech im Ofen
SOLANGE Pizza noch nicht fertig
    warte
hole die Pizza aus dem Ofen
```

### Programmablaufplan (Flussdiagramm)

Eine **grafische Darstellung** eines Algorithmus mit genormten Symbolen:

| Symbol         | Form                         | Bedeutung                      |
| -------------- | ---------------------------- | ------------------------------ |
| Start / Ende   | Abgerundetes Rechteck (Oval) | Beginn oder Ende des Programms |
| Anweisung      | Rechteck                     | Eine Aktion ausführen          |
| Verzweigung    | Raute                        | Eine Ja/Nein-Entscheidung      |
| Ein- / Ausgabe | Parallelogramm               | Daten lesen oder ausgeben      |

**Flussdiagramm – Tiefkühlpizza backen:**

```
        ╭───────────╮
        │   START   │
        ╰─────┬─────╯
              ↓
   ┌──────────────────────┐
   │  Verpackung öffnen   │
   └──────────┬───────────┘
              ↓
        ◇─────────────◇
       ╱  In Folie     ╲
      ╱   eingepackt?   ╲
    JA                  NEIN
     ↓                    │
┌───────────┐             │
│  Folie    │             │
│ entfernen │             │
└─────┬─────┘             │
      └──────────┬─────────┘
                 ↓
   ┌──────────────────────┐
   │   Ofen einschalten   │
   └──────────┬───────────┘
              ↓
   ┌──────────────────────┐
   │  Pizza in den Ofen   │
   └──────────┬───────────┘
              ↓
        ◇─────────────◇
       ╱  Pizza         ╲
      ╱   fertig?         ╲
    NEIN                 JA
     ↓                    ↓
     └──(warte)──→◇  ┌─────────────────┐
                     │ Pizza rausnehmen │
                     └────────┬────────┘
                               ↓
                        ╭─────────────╮
                        │    ENDE     │
                        ╰─────────────╯
```

---

## 2. Die Sprache Python

Python ist eine **interpretierte, höhere Programmiersprache**, entwickelt 1991 von Guido van Rossum. Sie ist ideal für Einsteiger weil:

- die Syntax **lesbar und klar** ist (kein Semikolon am Zeilenende nötig, keine geschweiften Klammern)
- **Einrückungen** (Leerzeichen am Zeilenanfang) die Struktur definieren – das ist Pflicht, kein Stil
- sie in vielen Bereichen eingesetzt wird: Webentwicklung, Künstliche Intelligenz, Datenanalyse, Automatisierung

```python
# Kommentare beginnen mit #  – sie werden nicht ausgeführt
print("Hallo Welt")   # Gibt Text auf dem Bildschirm aus
```

---

## 3. Operatoren

### Arithmetische Operatoren

| Operator | Bedeutung              | Beispiel  | Ergebnis |
| -------- | ---------------------- | --------- | -------- |
| `+`      | Addition               | `5 + 3`   | `8`      |
| `-`      | Subtraktion            | `10 - 4`  | `6`      |
| `*`      | Multiplikation         | `3 * 7`   | `21`     |
| `/`      | Division               | `10 / 4`  | `2.5`    |
| `//`     | Ganzzahldivision       | `10 // 4` | `2`      |
| `%`      | Modulo (Divisionsrest) | `10 % 3`  | `1`      |
| `**`     | Potenz                 | `2 ** 8`  | `256`    |

### Vergleichsoperatoren

Vergleiche geben immer `True` oder `False` zurück.

| Operator | Bedeutung           | Beispiel | Ergebnis |
| -------- | ------------------- | -------- | -------- |
| `==`     | Gleichheit          | `5 == 5` | `True`   |
| `!=`     | Ungleichheit        | `5 != 3` | `True`   |
| `>`      | Größer als          | `7 > 3`  | `True`   |
| `>=`     | Größer oder gleich  | `5 >= 5` | `True`   |
| `<`      | Kleiner als         | `2 < 9`  | `True`   |
| `<=`     | Kleiner oder gleich | `4 <= 3` | `False`  |

> ⚠️ **Wichtig:** `=` ist die **Zuweisung** (Wert speichern). `==` ist der **Vergleich** (sind zwei Werte gleich?). Diese zu verwechseln ist ein häufiger Anfängerfehler!

### Logische Operatoren

| Operator | Bedeutung                                           | Beispiel         | Ergebnis |
| -------- | --------------------------------------------------- | ---------------- | -------- |
| `and`    | Konjunktion – **beide** müssen `True` sein          | `True and False` | `False`  |
| `or`     | Disjunktion – **mindestens einer** muss `True` sein | `True or False`  | `True`   |
| `not`    | Negation – kehrt den Wert um                        | `not True`       | `False`  |

---

## 4. Variablen

Eine Variable ist ein **benannter Speicherplatz** für einen Wert im Arbeitsspeicher. Variablen müssen vor ihrer Verwendung **deklariert** (eingeführt) und **initialisiert** (mit einem Startwert versehen) werden.

```python
# Drei Variablen werden deklariert und gleichzeitig initialisiert:
name      = "Max"    # String  – Textwert
alter     = 15       # Integer – Ganzzahl
ist_bereit = True    # Boolean – Wahrheitswert
```

Wird eine Variable **nicht initialisiert**, befindet sie sich in einem **undefinierten Zustand** – in anderen Sprachen wäre der Wert zufälliger „Speichermüll". Python löst das mit einem `NameError` wenn man eine unbekannte Variable benutzt.

```python
print(unbekannt)   # → NameError: name 'unbekannt' is not defined
```

**Benennungsregeln:**

- Nur Buchstaben, Zahlen und `_` (kein Leerzeichen, kein `-`)
- Darf **nicht** mit einer Zahl beginnen: `2name` ❌ → `name2` ✓
- Groß-/Kleinschreibung beachten: `zahl` ≠ `Zahl` ≠ `ZAHL`

### Aufbau einer Zuweisung

```
     variable = ausdruck
         ↑           ↑
    Name des    Wert oder
   Speicherplatzes  Berechnung
```

```python
x = 10          # x erhält den Wert 10
x = x + 5       # x nimmt seinen alten Wert + 5 → x ist jetzt 15
x += 3          # Kurzform für x = x + 3 → x ist jetzt 18
```

---

## 5. Einfaches Beispielprogramm

```python
# Programm: Begrüßung mit Alterscheck

name  = "Lisa"
alter = 16

print("Hallo", name)

if alter >= 18:
    print("Du bist volljährig.")
else:
    jahre_noch = 18 - alter
    print("Noch", jahre_noch, "Jahr(e) bis zur Volljährigkeit.")

print("Tschüss!")
```

**Ausgabe:**

```
Hallo Lisa
Noch 2 Jahr(e) bis zur Volljährigkeit.
Tschüss!
```

---

## 6. Datentypen

| Typ           | Python-Name | Beispiel           | Beschreibung                                         |
| ------------- | ----------- | ------------------ | ---------------------------------------------------- |
| Ganzzahl      | `int`       | `42`, `-7`, `0`    | Ganze Zahlen ohne Komma                              |
| Kommazahl     | `float`     | `3.14`, `-0.5`     | Zahlen mit Dezimalstelle                             |
| Text          | `str`       | `"Hallo"`, `'Max'` | Zeichenkette in Anführungszeichen                    |
| Wahrheitswert | `bool`      | `True`, `False`    | Nur zwei mögliche Werte                              |
| Zeichen       | `char`      | `"A"`, `"7"`       | Ein einzelnes Zeichen (in Python: `str` der Länge 1) |

```python
print(type(42))       # <class 'int'>
print(type("Hallo"))  # <class 'str'>
print(type(True))     # <class 'bool'>
```

### Boolean (Wahrheitswert)

Ein `bool` kann nur `True` (wahr) oder `False` (falsch) annehmen. Alle Vergleiche geben einen Boolean zurück.

```python
ist_gross  = 10 > 5    # True
ist_gleich = 3 == 7    # False
```

### Boolesche Ausdrücke – Wahrheitstabellen

**AND – Konjunktion** (beide müssen wahr sein):

| A     | B     | A `and` B |
| ----- | ----- | --------- |
| True  | True  | **True**  |
| True  | False | False     |
| False | True  | False     |
| False | False | False     |

**OR – Disjunktion** (mindestens einer muss wahr sein):

| A     | B     | A `or` B |
| ----- | ----- | -------- |
| True  | True  | **True** |
| True  | False | **True** |
| False | True  | **True** |
| False | False | False    |

**NOT – Negation** (kehrt den Wert um):

| A     | `not` A |
| ----- | ------- |
| True  | False   |
| False | True    |

```python
regen  = True
schirm = False

nass = regen and not schirm   # True and True → True
print("Werde ich nass?", nass)
```

### Char (Zeichen)

Ein einzelnes Zeichen – in Python ist das ein `str` der Länge 1. Jedes Zeichen hat einen **ASCII-Wert** (eine Zahl die das Zeichen eindeutig codiert).

```python
buchstabe = "A"
print(len(buchstabe))   # → 1
print(ord("A"))         # → 65  (ASCII-Wert von A)
print(chr(66))          # → B   (Zeichen mit ASCII-Wert 66)
print("A" < "B")        # → True  (A kommt vor B im Alphabet)
```

---

## 7. If-Else-Anweisung (Verzweigung)

Eine Verzweigung wählt je nach Bedingung einen unterschiedlichen Pfad – genau wie die Raute im Flussdiagramm.

```python
# Nur if – einzeilige Entscheidung
if bedingung:
    # wird ausgeführt wenn bedingung True ist

# if + else – zwei Wege
if bedingung:
    # True-Zweig
else:
    # False-Zweig (wenn bedingung False ist)

# if + elif + else – viele Fälle
note = 85

if note >= 90:
    print("Sehr gut")
elif note >= 75:
    print("Gut")
elif note >= 60:
    print("Befriedigend")
else:
    print("Nicht bestanden")
```

Bedingungen können mit logischen Operatoren kombiniert werden:

```python
alter     = 17
hat_ausweis = True

if alter >= 18 and hat_ausweis:
    print("Zutritt erlaubt")
elif alter >= 16 or hat_ausweis:
    print("Zutritt mit Einschränkungen")
else:
    print("Kein Zutritt")
```

---

## 8. Match-Case (Switch-Case)

Seit Python 3.10 gibt es `match-case` – ideal wenn eine Variable viele mögliche einzelne Werte hat. Übersichtlicher als viele `elif`-Ketten.

```python
tag = "Montag"

match tag:
    case "Samstag" | "Sonntag":
        print("Wochenende – ausschlafen!")
    case "Montag" | "Freitag":
        print("Wochentag")
    case _:                          # _ ist der Default-Fall (wie else)
        print("Normaler Wochentag")
```

```python
# Praktisch: Notentext per match-case
punkte = 78

match punkte // 10:   # 78 // 10 = 7
    case 10 | 9:
        print("Sehr gut (A)")
    case 8 | 7:
        print("Gut (B)")
    case 6:
        print("Befriedigend (C)")
    case _:
        print("Nicht bestanden")
```

---

## 9. Logische Aussagen

In der Logik nennt man die Verknüpfungen von Wahrheitswerten:

| Logik                   | Python    | Bedeutung             |
| ----------------------- | --------- | --------------------- |
| **Konjunktion** (A ∧ B) | `A and B` | Beide wahr            |
| **Disjunktion** (A ∨ B) | `A or B`  | Mindestens einer wahr |
| **Negation** (¬A)       | `not A`   | Gegenteil             |

```python
hat_ticket  = True
ist_volljährig = False

# Konjunktion: beide Bedingungen müssen erfüllt sein
zutritt = hat_ticket and ist_volljährig    # False

# Disjunktion: eine Bedingung reicht
sonderfall = hat_ticket or ist_volljährig  # True

# Negation
kein_ticket = not hat_ticket               # False
```

---

## 10. Arrays (Listen)

Eine **Liste** speichert mehrere Werte unter einem Namen. Jeder Wert hat einen **Index** – eine Positionsnummer, die bei **0** beginnt.

```
Index:    0        1        2        3        4
Wert:  "Apfel" "Banane"  "Mango"  "Kiwi"  "Orange"
```

```python
früchte = ["Apfel", "Banane", "Mango", "Kiwi", "Orange"]

print(früchte[0])    # → Apfel    (erstes Element)
print(früchte[2])    # → Mango    (drittes Element)
print(früchte[-1])   # → Orange   (letztes Element – negativer Index)
print(len(früchte))  # → 5        (Anzahl Elemente)
```

**Wichtige Listen-Operationen:**

```python
noten = [85, 72, 91]

noten.append(88)     # Hinzufügen am Ende    → [85, 72, 91, 88]
noten.remove(72)     # Löschen nach Wert     → [85, 91, 88]
del noten[0]         # Löschen nach Index    → [91, 88]
noten.sort()         # Sortieren aufsteigend → [88, 91]
```

> ⚠️ `remove(x)` löscht nach **Wert**. `del liste[i]` löscht nach **Index**. Das ist ein häufiger Fehler!

---

## 11. For-Schleife

Eine `for`-Schleife wiederholt Code eine bestimmte Anzahl von Malen oder für jedes Element einer Liste.

### Mit `range()`

```python
# range(stop): von 0 bis stop-1
for i in range(5):
    print(i)           # 0, 1, 2, 3, 4

# range(start, stop): von start bis stop-1
for i in range(1, 6):
    print(i)           # 1, 2, 3, 4, 5

# range(start, stop, step): mit Schrittweite
for i in range(10, 0, -1):
    print(i)           # Countdown: 10, 9, 8 ... 1
```

### Über eine Liste iterieren

```python
früchte = ["Apfel", "Banane", "Mango"]

for frucht in früchte:
    print(frucht)          # Apfel / Banane / Mango

# Mit Index: enumerate()
for i, frucht in enumerate(früchte):
    print(i, frucht)       # 0 Apfel / 1 Banane / 2 Mango
```

### Modulo in Schleifen

Der `%`-Operator liefert den **Rest** einer Division. Damit erkennst du, ob eine Zahl durch eine andere teilbar ist: `zahl % 3 == 0` heißt „ohne Rest durch 3 teilbar“.

```python
for i in range(1, 11):
    if i % 2 == 0:
        print(i, "ist gerade")
    else:
        print(i, "ist ungerade")
```

Bei mehreren Bedingungen kommt es auf die **Reihenfolge** an: `if`/`elif` prüft von oben nach unten und nimmt den **ersten** zutreffenden Fall. Die speziellste Bedingung (z. B. „durch 3 **und** durch 5 teilbar“) muss deshalb zuerst geprüft werden, sonst wird sie nie erreicht.

---

## 12. Funktionen

Funktionen kapseln Code der mehrfach gebraucht wird. Sie starten mit `def`, können **Parameter** empfangen und Werte mit `return` zurückgeben.

```python
def begruessung(name, alter):
    if alter >= 18:
        anrede = "Guten Tag"
    else:
        anrede = "Hallo"
    return f"{anrede}, {name}!"

print(begruessung("Max", 15))        # → Hallo, Max!
print(begruessung("Dr. Müller", 42)) # → Guten Tag, Dr. Müller!
```

Variablen, die **innerhalb** einer Funktion entstehen, gibt es nur dort (**lokale Variablen**). `return` beendet die Funktion sofort; ohne `return` gibt sie `None` zurück.

> ⚠️ `print` zeigt etwas an, `return` gibt einen Wert an den Aufrufer zurück. Wer das Ergebnis weiterverwenden will, braucht `return`.

---

## 13. While-Schleife

Die `while`-Schleife wiederholt Code, **solange** eine Bedingung wahr ist. Man nimmt sie, wenn vorher nicht feststeht, wie viele Durchläufe nötig sind.

```python
kontostand = 100
jahre = 0
while kontostand < 200:
    kontostand = kontostand * 1.1   # 10 % Zinsen
    jahre += 1
print(f"Nach {jahre} Jahren verdoppelt")   # → Nach 8 Jahren verdoppelt
```

> ⚠️ Irgendetwas in der Schleife muss die Bedingung irgendwann falsch machen – sonst läuft sie **endlos**.

| Schlüsselwort | Wirkung                                                  |
| ------------- | -------------------------------------------------------- |
| `break`       | Schleife sofort verlassen                                |
| `continue`    | Rest dieses Durchlaufs überspringen, mit dem nächsten weitermachen |

**`for` oder `while`?** Anzahl bekannt oder über eine Liste laufen → `for`. Wiederholen, bis etwas eintritt → `while`.

### Einrückung

Python erkennt an der **Einrückung** (4 Leerzeichen), welche Zeilen zu einer Schleife, Verzweigung oder Funktion gehören. Nach jeder Zeile mit `:` folgt ein eingerückter Block.

```python
zahl = 3
while zahl > 0:
    print(zahl)        # gehört zur Schleife
    zahl -= 1          # gehört zur Schleife
print("Start!")        # nicht eingerückt: erst nach der Schleife
```

---

## 14. Texte (Strings)

```python
name = "Lena"
print(len(name))           # → 4         Länge
print(name[0])             # → L         erstes Zeichen
print(name.upper())        # → LENA      Großbuchstaben
print(name.lower())        # → lena      Kleinbuchstaben
print("en" in name)        # → True      enthalten?
print("Hallo " + name)     # → Hallo Lena   Verketten mit +
print(name * 3)            # → LenaLenaLena
print("a,b,c".split(","))  # → ['a', 'b', 'c']   aufteilen
print("  hi  ".strip())    # → hi        Leerzeichen am Rand entfernen
```

### f-Strings

Mit einem `f` vor dem Text setzt du Werte direkt in `{ }` ein:

```python
punkte = 87.456
print(f"{name} hat {punkte} Punkte")      # → Lena hat 87.456 Punkte
print(f"{name} hat {punkte:.1f} Punkte")  # → Lena hat 87.5 Punkte (1 Nachkommastelle)
```

---

## 15. Eingabe und Typumwandlung

`input()` liest eine Eingabe – und liefert **immer einen Text** (`str`), auch wenn eine Zahl eingegeben wurde.

```python
alter = input("Wie alt bist du? ")   # z. B. "15" – ein Text!
alter = int(alter)                    # jetzt die Zahl 15
print(alter + 1)                      # → 16
```

| Funktion  | Umwandlung                     | Beispiel                 |
| --------- | ------------------------------ | ------------------------ |
| `int()`   | in eine ganze Zahl             | `int("42")` → `42`       |
| `float()` | in eine Kommazahl              | `float("3.5")` → `3.5`   |
| `str()`   | in einen Text                  | `str(42)` → `"42"`       |
| `bool()`  | in einen Wahrheitswert         | `bool(0)` → `False`      |

> ⚠️ `"5" + "3"` ergibt `"53"` (Texte werden aneinandergehängt), `5 + 3` ergibt `8`. Und `"5" + 3` ist ein Fehler.

---

## 16. Dictionaries

Ein **Dictionary** speichert **Schlüssel-Wert-Paare**. Statt über eine Position greifst du über einen Namen zu – wie in einem Wörterbuch.

```python
schueler = {"name": "Max", "klasse": "8a", "note": 2}

print(schueler["name"])         # → Max
schueler["note"] = 1            # Wert ändern
schueler["email"] = "max@x.de"  # neuen Eintrag hinzufügen
print("klasse" in schueler)     # → True  (Schlüssel vorhanden?)
print(schueler.get("alter", 0)) # → 0     (Standardwert, falls Schlüssel fehlt)
```

### Über ein Dictionary laufen

```python
lager = {"Äpfel": 12, "Birnen": 0, "Kiwis": 5}

for ware in lager:                     # nur die Schlüssel
    print(ware)

for ware, anzahl in lager.items():     # Schlüssel und Wert zusammen
    if anzahl == 0:
        print(f"{ware} ist ausverkauft")
```

| Methode       | Liefert                         |
| ------------- | ------------------------------- |
| `.keys()`     | alle Schlüssel                  |
| `.values()`   | alle Werte                      |
| `.items()`    | alle Paare `(schlüssel, wert)`  |

Oft hat man eine **Liste von Dictionaries** – wie Zeilen einer Tabelle:

```python
klasse = [
    {"name": "Lena", "punkte": 92},
    {"name": "Tom", "punkte": 78},
]
for s in klasse:
    print(s["name"], s["punkte"])
```

---

## 17. Fehlermeldungen verstehen

Python zeigt bei Fehlern einen **Traceback**. Lies ihn von **unten nach oben**: Die letzte Zeile nennt Art und Grund, darüber steht die Zeilennummer.

```
Traceback (most recent call last):
  File "main.py", line 3, in <module>
    print(summe / anzahl)
ZeroDivisionError: division by zero
```

| Fehler                | Bedeutung                                          | Typische Ursache                                  |
| --------------------- | -------------------------------------------------- | ------------------------------------------------- |
| `SyntaxError`         | Code ist kein gültiges Python                      | fehlender `:`, Klammer oder Anführungszeichen      |
| `IndentationError`    | Einrückung stimmt nicht                            | Block nach `:` nicht eingerückt, Tabs und Leerzeichen gemischt |
| `NameError`           | Name unbekannt                                     | Tippfehler, Variable noch nicht angelegt           |
| `TypeError`           | Datentypen passen nicht zusammen                   | `"Alter: " + 15` → besser `f"Alter: {15}"`         |
| `ValueError`          | Wert ungeeignet                                    | `int("zwölf")`                                     |
| `IndexError`          | Index außerhalb der Liste                          | `liste[5]` bei nur 5 Elementen (letzter Index ist 4) |
| `KeyError`            | Schlüssel nicht im Dictionary                      | Tippfehler beim Schlüssel, Groß-/Kleinschreibung   |
| `ZeroDivisionError`   | Division durch 0                                   | leere Liste beim Durchschnitt                      |

**Debugging-Tipps:**

1. Fehlermeldung genau lesen – Zeile und Art des Fehlers.
2. Mit `print()` Zwischenwerte ausgeben: Hat die Variable den Wert, den du erwartest?
3. Code in kleinen Schritten schreiben und jeden Schritt testen.
4. Gleich und zugewiesen nicht verwechseln: `=` weist zu, `==` vergleicht.

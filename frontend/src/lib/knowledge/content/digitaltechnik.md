---
title: Digitaltechnik
icon: 🔧
---

# Digitaltechnik

Alles was ein Computer tut – Texte schreiben, Videos abspielen, Spiele rechnen – passiert letztendlich mit nur zwei Zuständen: **Strom an (1)** oder **Strom aus (0)**. Wie das funktioniert lernst du hier.

---

## 1. Bit, Byte und Speichergrößen

Die kleinste Informationseinheit heißt **Bit**. Ein Bit kann genau einen von zwei Werten annehmen: `0` oder `1`.

```
1 Bit      = eine 0 oder eine 1
8 Bit      = 1 Byte  (kann Zahlen von 0 bis 255 darstellen)
1024 Byte  = 1 Kilobyte (KB)
1024 KB    = 1 Megabyte (MB)
1024 MB    = 1 Gigabyte (GB)
1024 GB    = 1 Terabyte (TB)
```

**Wie viele Symbole kann man mit N Bits darstellen?**

Mit **N Bits** kann man genau **2^N verschiedene** Muster speichern:

| Bits (N) | Mögliche Muster (2^N) | Beispiel |
|---|---|---|
| 1 | 2 | 0, 1 |
| 2 | 4 | 00, 01, 10, 11 |
| 3 | 8 | 000 bis 111 |
| 8 | 256 | 0 bis 255 (ein Byte) |

> **Merksatz:** Jedes zusätzliche Bit **verdoppelt** die Anzahl der möglichen Werte.

Umgekehrt: Wenn man **K verschiedene Symbole** darstellen möchte, braucht man mindestens **log₂(K) Bits**.
Beispiel: Für 26 Buchstaben braucht man mindestens log₂(26) ≈ 4,7 → also **5 Bits**.

---

## 2. Das Binärsystem

Im Alltag zählen wir im **Dezimalsystem** (Basis 10) – mit den Ziffern 0–9. Computer rechnen im **Binärsystem** (Basis 2) – nur mit 0 und 1.

**Stellenwerte im Binärsystem** (von rechts nach links, jede Stelle doppelt so groß):

```
Stelle:    7    6    5    4    3    2    1    0
Wert:    128   64   32   16    8    4    2    1
```

### Binär → Dezimal

Multipliziere jede Ziffer mit ihrem Stellenwert und addiere alles:

```
  1   0   1   1   0   1  (binär)
  ×   ×   ×   ×   ×   ×
 32  16   8   4   2   1   (Stellenwerte)
= 32 + 0 + 8 + 4 + 0 + 1 = 45 (dezimal)
```

**Weitere Beispiele:**

| Binär | Rechnung | Dezimal |
|---|---|---|
| `0001` | 1 | 1 |
| `0010` | 2 | 2 |
| `0100` | 4 | 4 |
| `1000` | 8 | 8 |
| `1010` | 8+2 | 10 |
| `1111` | 8+4+2+1 | 15 |

### Dezimal → Binär

Teile wiederholt durch 2 und notiere die Reste von unten nach oben:

```
13 ÷ 2 = 6  Rest 1  ↑ (LSB – ganz rechts)
 6 ÷ 2 = 3  Rest 0  ↑
 3 ÷ 2 = 1  Rest 1  ↑
 1 ÷ 2 = 0  Rest 1  ↑ (MSB – ganz links)

13 (dezimal) = 1101 (binär)
```

### In Python umrechnen

```python
# Binär → Dezimal
zahl = int("1101", 2)   # → 13

# Dezimal → Binär
binaer = bin(13)        # → '0b1101' (0b zeigt an: das ist binär)
binaer_clean = bin(13)[2:]  # → '1101' (ohne 0b)
```

---

## 3. Codes und Zeichencodierung

### Was ist ein Code?

Ein **Code** ist eine Vorschrift die jedem Symbol aus einer Menge eindeutig ein Bitmuster zuordnet.

```
Symbol  →  Code (Bitmuster)
  A     →     01000001
  B     →     01000010
  1     →     00110001
```

Die Menge der Symbole heißt **Ursprungsmenge** (z.B. alle Buchstaben), die Menge der Bitmuster heißt **Bildmenge**.

### ASCII – der Buchstaben-Code

Der **ASCII-Code** (American Standard Code for Information Interchange) ordnet jedem Zeichen eine Zahl von 0 bis 127 zu – diese Zahl wird dann binär gespeichert. ASCII nutzt 7 Bit → 2^7 = **128 verschiedene Zeichen**.

| Zeichen | Dezimal | Binär (7-bit) |
|---|---|---|
| `A` | 65 | 1000001 |
| `B` | 66 | 1000010 |
| `Z` | 90 | 1011010 |
| `a` | 97 | 1100001 |
| `0` | 48 | 0110000 |
| Leerzeichen | 32 | 0100000 |

```python
# Zeichen → ASCII-Zahl
print(ord("A"))     # → 65
print(ord("a"))     # → 97

# ASCII-Zahl → Zeichen
print(chr(65))      # → A
print(chr(72))      # → H
```

> **Beobachtung:** Großbuchstaben beginnen bei 65, Kleinbuchstaben bei 97. Der Unterschied ist immer 32 – das ist Bit 5!

### Code als Binärbaum

Man kann einen Code als Baum darstellen: Links = 0, Rechts = 1. Am Ende jedes Pfades steht ein Symbol.

**Beispiel: 4 Symbole mit festem 2-Bit-Code:**

```
         Wurzel
        /       \
       0         1
      / \       / \
     0   1     0   1
     A   B     C   D

Codewort:  A=00  B=01  C=10  D=11
```

---

## 4. Codes mit fester und variabler Länge

### Fester Code

Alle Symbole bekommen gleich lange Bitmuster. Einfach und gleichmäßig – aber nicht immer effizient.

**Beispiel:** 4 Symbole A, B, C, D → brauchen je 2 Bit.
Bei 1000 Symbolen: **1000 × 2 = 2000 Bit**.

### Variabler Code – Huffman-Codierung

Wenn manche Symbole **viel häufiger** vorkommen als andere, kann man Speicher sparen: Häufige Symbole bekommen **kurze** Bitmuster, seltene bekommen **längere**.

**Grundidee:**
> Hohe Wahrscheinlichkeit = wenig Information = kurzes Codewort  
> Geringe Wahrscheinlichkeit = viel Information = langes Codewort

**Beispiel:** 4 Symbole mit unterschiedlicher Häufigkeit:

| Symbol | Wahrscheinlichkeit | Fester Code | Huffman-Code |
|---|---|---|---|
| B | 50 % (1/2) | 00 | **0** |
| A | 33 % (1/3) | 01 | **11** |
| C | 8 % (1/12) | 10 | **100** |
| D | 8 % (1/12) | 11 | **101** |

**Erwartete Länge pro Symbol:**
- Fester Code: **2 Bit** (immer)
- Huffman: 0,5×1 + 0,33×2 + 0,08×3 + 0,08×3 ≈ **1,67 Bit**

Bei 1000 Symbolen spart Huffman: 2000 − 1667 = **333 Bit** (≈ 17% weniger Speicher!)

### Huffman-Baum konstruieren

Der Huffman-Code wird durch Aufbau eines Baums von den Blättern zur Wurzel gefunden:

**Schritt 1:** Alle Symbole als Knoten mit ihrer Häufigkeit darstellen.

```
B:0.5   A:0.33   C:0.08   D:0.08
```

**Schritt 2:** Die **zwei seltensten** Knoten zusammenfassen.

```
B:0.5   A:0.33   [C+D]:0.16
                  /       \
               C:0.08   D:0.08
```

**Schritt 3:** Wieder die zwei seltensten zusammenfassen.

```
B:0.5   [A + CD]:0.49
         /          \
       A:0.33    [C+D]:0.16
                  /       \
               C:0.08   D:0.08
```

**Schritt 4:** Letzten zwei zur Wurzel verbinden.

```
            [Wurzel]:1.0
            /           \
          B:0.5       [A+CD]:0.49
                      /          \
                    A:0.33     [C+D]:0.16
                                /       \
                             C:0.08   D:0.08
```

**Schritt 5:** Links = 0, Rechts = 1 vergeben → Codewörter ablesen.

```
B  → 0      (links von Wurzel)
A  → 11     (rechts → links)
C  → 100    (rechts → rechts → links)
D  → 101    (rechts → rechts → rechts)
```

---

## 5. Boolesche Algebra

Die **Boolesche Algebra** ist die Mathematik hinter Logikgattern. Sie arbeitet nur mit zwei Werten: **0 (falsch)** und **1 (wahr)**.

### Die drei Grundoperationen

**AND (UND) – Konjunktion:** Ergebnis ist 1 nur wenn **beide** Eingänge 1 sind.

| A | B | A AND B |
|:---:|:---:|:---:|
| 0 | 0 | **0** |
| 0 | 1 | **0** |
| 1 | 0 | **0** |
| 1 | 1 | **1** |

Schreibweise: `A · B` oder `A ∧ B` oder in Python: `A and B`

**OR (ODER) – Disjunktion:** Ergebnis ist 1 wenn **mindestens ein** Eingang 1 ist.

| A | B | A OR B |
|:---:|:---:|:---:|
| 0 | 0 | **0** |
| 0 | 1 | **1** |
| 1 | 0 | **1** |
| 1 | 1 | **1** |

Schreibweise: `A + B` oder `A ∨ B` oder in Python: `A or B`

**NOT (NICHT) – Negation / Invertierung:** Kehrt den Wert um – aus 0 wird 1 und umgekehrt.

| A | NOT A |
|:---:|:---:|
| 0 | **1** |
| 1 | **0** |

Schreibweise: `Ā` oder `¬A` oder in Python: `not A`

### XOR (EXKLUSIV-ODER)

Ergebnis ist 1 wenn die Eingänge **verschieden** sind.

| A | B | A XOR B |
|:---:|:---:|:---:|
| 0 | 0 | **0** |
| 0 | 1 | **1** |
| 1 | 0 | **1** |
| 1 | 1 | **0** |

Schreibweise: `A ⊕ B` oder in Python: `A != B`

### Wichtige Rechenregeln (Gesetze der Booleschen Algebra)

```
Identität:       A · 1 = A        A + 0 = A
Null/Eins:       A · 0 = 0        A + 1 = 1
Idempotenz:      A · A = A        A + A = A
Komplement:      A · Ā = 0        A + Ā = 1
Kommutativität:  A · B = B · A    A + B = B + A
De Morgan:       ¬(A · B) = Ā + B̄    ¬(A + B) = Ā · B̄
```

**De Morgans Gesetz** ist besonders wichtig: Eine negierte UND-Verknüpfung ist dasselbe wie ODER mit negierten Eingängen.

```python
A = True
B = False

# De Morgan überprüfen:
print(not (A and B))       # → True
print((not A) or (not B))  # → True  (identisch!)
```

### Boolesche Ausdrücke in Python

```python
# Einfache Gatter
a = True
b = False

print(a and b)   # AND  → False
print(a or b)    # OR   → True
print(not a)     # NOT  → False
print(a != b)    # XOR  → True

# Kombiniert: Tresor öffnet wenn beide Schlüssel UND kein Notstopp
key1     = True
key2     = True
notstopp = False

tresor = (key1 and key2) and (not notstopp)
print(tresor)   # → True
```

---

## 6. Schaltnetze

Ein **Schaltnetz** ist eine Schaltung aus Logikgattern die aus mehreren Eingängen einen oder mehrere Ausgänge berechnet – ohne Gedächtnis (das Ergebnis hängt nur von den aktuellen Eingängen ab).

### Gatter-Symbole

```
AND:   A ──┐
           ├─[&]── Ausgang     (Rechteck mit &)
       B ──┘

OR:    A ──┐
           ├─[≥1]── Ausgang    (Rechteck mit ≥1)
       B ──┘

NOT:   A ──[1]──○── Ausgang    (Kreis = Invertierung)

XOR:   A ──┐
           ├─[=1]── Ausgang    (Rechteck mit =1)
       B ──┘
```

### Schaltnetz entwerfen: Vom Problem zur Schaltung

**Vorgehen:**
1. Problem beschreiben
2. Wahrheitstabelle aufstellen (alle Eingangskombinationen)
3. Boolesche Formel ablesen
4. Gatter verschalten

**Beispiel: Mehrheitsentscheider (Majority Gate)**

Drei Personen (A, B, C) stimmen ab. Ausgang Y = 1 wenn **mindestens 2** mit Ja (1) stimmen.

**Wahrheitstabelle:**

| A | B | C | Y |
|:---:|:---:|:---:|:---:|
| 0 | 0 | 0 | 0 |
| 0 | 0 | 1 | 0 |
| 0 | 1 | 0 | 0 |
| 0 | 1 | 1 | 1 |
| 1 | 0 | 0 | 0 |
| 1 | 0 | 1 | 1 |
| 1 | 1 | 0 | 1 |
| 1 | 1 | 1 | 1 |

**Boolesche Formel:** Y = (A·B) + (A·C) + (B·C)

**In Python:**
```python
def mehrheit(A, B, C):
    return (A and B) or (A and C) or (B and C)

print(mehrheit(1, 0, 1))  # → True  (A und C stimmen Ja)
print(mehrheit(0, 0, 1))  # → False (nur C stimmt Ja)
```

---

## 7. KV-Diagramme: Schaltungen vereinfachen

Ein **Karnaugh-Veitch-Diagramm (KV-Diagramm)** ist eine grafische Methode um boolesche Ausdrücke zu vereinfachen – also mit weniger Gattern dasselbe Ergebnis zu erzielen.

### Aufbau

Für 2 Variablen (A, B): 4 Felder

```
      B=0   B=1
A=0  |  0  |  1  |
A=1  |  1  |  1  |
```

Für 3 Variablen (A, B, C): 8 Felder (Gray-Code-Reihenfolge!)

```
        C=0       C=1
     B=0  B=1  B=1  B=0      ← Achtung: Gray-Code (nicht 00,01,10,11 !)
A=0 | m0 | m1 | m3 | m2 |
A=1 | m4 | m5 | m7 | m6 |
```

> **Wichtig:** Die Spalten folgen dem **Gray-Code**: 00 → 01 → 11 → 10. Zwei benachbarte Felder unterscheiden sich immer nur in **einer** Variable!

### Gruppen bilden (Kofaktoren ablesen)

Markiere Gruppen von 1en mit der Größe **1, 2, 4, 8, ...** (immer Zweierpotenzen). Gruppen dürfen sich überlappen und über den Rand "wrappen".

**Regel:** Je größer die Gruppe, desto weniger Variablen braucht man im Term.

```
Gruppe mit 2 Feldern → 1 Variable fällt weg
Gruppe mit 4 Feldern → 2 Variablen fallen weg
Gruppe mit 8 Feldern → 3 Variablen fallen weg
```

**Beispiel:**

```
        C=0  C=1
A=0  |  0  |  1  |
A=1  |  1  |  1  |
```

Gruppen:
- Die beiden 1en in Spalte C=1: A ändert sich (0→1), C=1 bleibt → Term: **C**
- Die beiden 1en in Zeile A=1: C ändert sich, A=1 bleibt → Term: **A**

Minimiertes Ergebnis: **Y = A + C** (statt z.B. (¬A·C) + (A·¬C) + (A·C))

### Don't-Care-Zustände

Manchmal gibt es Eingangskombinationen die **nie auftreten** (z.B. ungültige Codes). Diese heißen **Don't-Care (X)** und können beliebig als 0 oder 1 verwendet werden – immer so wie es die Vereinfachung am meisten vereinfacht.

```
        C=0  C=1
A=0  |  0  |  1  |
A=1  |  X  |  1  |    ← X = Don't Care
```

Den X-Wert als 1 interpretieren erlaubt eine größere Gruppe → einfacherer Term.

---

## 8. Schaltwerke und Automaten

Anders als ein Schaltnetz hat ein **Schaltwerk** ein **Gedächtnis**: Der Ausgang hängt nicht nur von den aktuellen Eingängen ab, sondern auch vom **Zustand** – also von dem was bisher passiert ist.

**Vergleich:**

| | Schaltnetz | Schaltwerk |
|---|---|---|
| Gedächtnis | ❌ Nein | ✅ Ja |
| Ausgang hängt ab von | Aktuellen Eingängen | Eingängen + aktuellem Zustand |
| Beispiel | Addierer | Ampelsteuerung, Schloss |

### Endliche Automaten (Zustandsautomaten)

Ein **endlicher Automat** besteht aus:
- einer endlichen Menge von **Zuständen** (z.B. Z0, Z1, Z2)
- einem **Startzustand**
- **Übergängen**: Bei Eingabe X wechsle von Zustand A nach Zustand B
- ggf. **Ausgaben** je nach Zustand oder Übergang

**Darstellung als Zustandsdiagramm:**
- Kreise = Zustände
- Pfeile = Übergänge (beschriftet mit Eingabe)
- Doppelkreis = Endzustand (Akzeptierzustand)

**Beispiel: Einfaches Zahlen-Schloss**

Das Schloss öffnet wenn die Sequenz `1 → 0 → 1` eingegeben wird.

```
    ──1──▶  Z1  ──0──▶  Z2  ──1──▶  Z3 (OFFEN) ◎
   │         │                        │
  START      └──1──▶ Z1 (restart)     │
             └──0──▶ Z0 (reset)       └──(beliebig)──▶ Z0
```

Zustandstabelle:

| Zustand | Eingabe | Nächster Zustand | Ausgabe |
|---|---|---|---|
| Z0 (Start) | 1 | Z1 | geschlossen |
| Z0 | 0 | Z0 | geschlossen |
| Z1 | 0 | Z2 | geschlossen |
| Z1 | 1 | Z1 | geschlossen |
| Z2 | 1 | Z3 | **offen!** |
| Z2 | 0 | Z0 | geschlossen |

### Moore- vs. Mealy-Automat

| | Moore-Automat | Mealy-Automat |
|---|---|---|
| Ausgabe hängt ab von | Nur dem Zustand | Zustand **und** Eingabe |
| Ausgabe steht | Am Zustand (im Kreis) | Am Übergang (am Pfeil) |
| Vorteil | Einfacher zu verstehen | Weniger Zustände nötig |

**Moore-Beispiel:** Ampelsteuerung – die Ausgabe (Farbe) hängt nur vom aktuellen Zustand ab.

```python
# Automat in Python simulieren
zustand = "ROT"

def ampel_schritt(zustand):
    if zustand == "ROT":
        return "ROTGELB"
    elif zustand == "ROTGELB":
        return "GRÜN"
    elif zustand == "GRÜN":
        return "GELB"
    elif zustand == "GELB":
        return "ROT"

for _ in range(5):
    print(zustand)
    zustand = ampel_schritt(zustand)
```

**Ausgabe:**
```
ROT
ROTGELB
GRÜN
GELB
ROT
```
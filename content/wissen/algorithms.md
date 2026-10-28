---
title: Algorithmen
icon: 🧩
---

# Algorithmen & Datenstrukturen

Ein **Algorithmus** ist eine Schritt-für-Schritt-Anleitung zur Lösung eines Problems – wie ein Kochrezept, das immer zum richtigen Ergebnis führt. Eine **Datenstruktur** legt fest, wie Daten gespeichert und geordnet sind. Beides gehört zusammen: Der beste Algorithmus hilft wenig, wenn die Daten ungünstig abgelegt sind.

---

## 1. Was macht einen Algorithmus aus?

Nicht jede Anleitung ist ein Algorithmus. Er muss diese Eigenschaften haben:

| Eigenschaft        | Bedeutung                                                     | Gegenbeispiel                     |
| ------------------ | ------------------------------------------------------------- | --------------------------------- |
| **Eindeutig**      | Jeder Schritt ist klar, es gibt keinen Interpretationsspielraum | „Nimm etwas Salz“                 |
| **Endlich**        | Die Beschreibung hat endlich viele Schritte, und er hält an    | „Zähle bis zur größten Zahl“      |
| **Ausführbar**     | Jeder Schritt lässt sich tatsächlich durchführen               | „Rate die Lottozahlen von morgen“ |
| **Zielgerichtet**  | Am Ende steht ein Ergebnis, das die Aufgabe löst               | „Mische die Karten für immer“     |

> **Merksatz:** Ein Algorithmus ist eine **eindeutige**, **endliche** Folge von **ausführbaren** Schritten, die ein Problem löst.

### Wie schnell ist ein Algorithmus?

Man zählt nicht Sekunden, sondern **Schritte in Abhängigkeit von der Datenmenge n**. Interessant ist vor allem der **schlechteste Fall** (Worst Case).

| Aufwand        | Name          | Beispiel                     | n = 1000 → Schritte |
| -------------- | ------------- | ---------------------------- | ------------------- |
| O(1)           | konstant      | Listenelement per Index      | 1                   |
| O(log n)       | logarithmisch | Binäre Suche                 | ca. 10              |
| O(n)           | linear        | Lineare Suche                | 1000                |
| O(n²)          | quadratisch   | Bubble Sort                  | 1 000 000           |

---

## 2. Suchen

### Lineare Suche

Du gehst die Liste **von vorne nach hinten** durch und vergleichst jedes Element mit dem gesuchten Wert.

```python
def lineare_suche(liste, gesucht):
    for i in range(len(liste)):
        if liste[i] == gesucht:
            return i        # gefunden: Position zurückgeben
    return -1               # nicht gefunden

print(lineare_suche([4, 8, 15, 16, 23, 42], 16))  # → 3
```

- **Vorteil:** funktioniert mit **jeder** Liste, auch unsortiert.
- **Schlechtester Fall:** Das Element steht ganz hinten oder fehlt → bei **n** Elementen **n Vergleiche**.

### Binäre Suche

Wie beim Zahlenraten („Ist es größer oder kleiner?“): Du schaust **in die Mitte**. Ist der gesuchte Wert kleiner, suchst du nur noch in der linken Hälfte weiter, sonst in der rechten. Mit jedem Schritt **halbiert** sich der Suchbereich.

> ⚠️ Die binäre Suche funktioniert **nur bei sortierten Listen**!

**Ablauf in Pseudocode:**

```
links  ← 0
rechts ← Länge der Liste − 1
SOLANGE links ≤ rechts:
    mitte ← (links + rechts) ganzzahlig geteilt durch 2
    WENN liste[mitte] = gesucht:  gib mitte zurück
    WENN liste[mitte] < gesucht:  links  ← mitte + 1   (rechte Hälfte)
    SONST:                        rechts ← mitte − 1   (linke Hälfte)
gib −1 zurück   (nicht gefunden)
```

In Python hilft dir `//` für die ganzzahlige Division: `7 // 2` ergibt `3`.

**Beispiel:** Suche 23 in `[4, 8, 15, 16, 23, 42]`

```
Schritt 1: links=0, rechts=5 → mitte=2 → 15 < 23 → links=3
Schritt 2: links=3, rechts=5 → mitte=4 → 23 gefunden!
```

**Wie viele Schritte?** So oft, wie man n halbieren kann, bis 1 übrig bleibt: **log₂(n)**.

| Elemente       | Lineare Suche (max.) | Binäre Suche (max.) |
| -------------- | -------------------- | ------------------- |
| 100            | 100                  | 7                   |
| 1 000          | 1 000                | 10                  |
| 1 000 000      | 1 000 000            | 20                  |

---

## 3. Sortieren

### Bubble Sort

Bubble Sort vergleicht immer **zwei benachbarte** Elemente und **tauscht** sie, wenn das linke größer ist. Nach dem ersten Durchlauf ist das größte Element ganz rechts „aufgestiegen“ – wie eine Blase im Wasser. Das wiederholt man, bis nichts mehr getauscht werden muss.

```
Start:          [5, 3, 8, 1]
Durchlauf 1:    [3, 5, 8, 1] → [3, 5, 8, 1] → [3, 5, 1, 8]   (8 ist am Ziel)
Durchlauf 2:    [3, 5, 1, 8] → [3, 1, 5, 8]                  (5 ist am Ziel)
Durchlauf 3:    [1, 3, 5, 8]                                 fertig
```

**Ablauf in Pseudocode:**

```
WIEDERHOLE (Länge − 1) Durchläufe:
    FÜR jede Position i von vorne bis zum noch unsortierten Ende:
        WENN liste[i] > liste[i + 1]:
            tausche liste[i] und liste[i + 1]
```

Nach jedem Durchlauf steht ein weiteres Element hinten richtig, deshalb darf die innere Schleife jedes Mal ein Element früher aufhören. In Python tauscht man zwei Werte in einer Zeile: `a, b = b, a`.

- **Aufwand:** O(n²) – für große Listen zu langsam.
- In der Praxis nutzt man schnellere Verfahren (z. B. Merge Sort, Quicksort). In Python: `sorted(liste)` oder `liste.sort()`.

---

## 4. Datenstrukturen

### Liste (Array)

Elemente liegen **nebeneinander** und haben einen Index ab 0. Zugriff per Index ist sehr schnell, Einfügen in der Mitte langsam (alle folgenden Elemente müssen rutschen).

### Dictionary (Hash Map)

Speichert **Schlüssel-Wert-Paare**. Nachschlagen über den Schlüssel geht extrem schnell, egal wie groß das Dictionary ist.

```python
telefonbuch = {"Max": "0171-123", "Lena": "0160-987"}
print(telefonbuch["Lena"])   # → 0160-987
```

### Stack (Stapel) – LIFO

**Last In, First Out:** Was zuletzt hineinkommt, kommt zuerst wieder heraus – wie ein Tellerstapel oder der „Rückgängig“-Knopf.

| Operation | Bedeutung                     | Python mit Liste |
| --------- | ----------------------------- | ---------------- |
| push      | oben drauflegen               | `stack.append(x)` |
| pop       | oberstes Element herunternehmen | `stack.pop()`    |
| peek/top  | oberstes Element ansehen      | `stack[-1]`      |

```python
stack = []
stack.append("Seite 1")
stack.append("Seite 2")
stack.append("Seite 3")
print(stack.pop())   # → Seite 3 (zuletzt hinzugefügt)
print(stack[-1])     # → Seite 2 (liegt jetzt oben)
```

### Queue (Warteschlange) – FIFO

**First In, First Out:** Wer zuerst kommt, wird zuerst bedient – wie die Schlange an der Kasse oder ein Druckerauftrag. In Python nimmt man `deque`, weil das Entfernen vorne dort schnell ist.

```python
from collections import deque

queue = deque()
queue.append("Anna")      # hinten anstellen (enqueue)
queue.append("Ben")
queue.append("Cem")
print(queue.popleft())    # → Anna (vorne bedienen, dequeue)
print(len(queue))         # → 2
```

> **Stack oder Queue?** Stack = `append` + `pop` (beides hinten). Queue = `append` hinten + `popleft` vorne.

### Baum (Tree)

Ein Baum besteht aus **Knoten**. Ganz oben steht die **Wurzel**, jeder Knoten kann **Kinder** haben, Knoten ohne Kinder heißen **Blätter**. Beispiele: Dateisystem, Stammbaum, Menüs.

Im **Binärbaum** hat jeder Knoten höchstens zwei Kinder (links und rechts). Im **binären Suchbaum** gilt zusätzlich: links stehen kleinere, rechts größere Werte.

```
          8
        /   \
       3     10
      / \      \
     1   6      14
```

**Traversierung** = alle Knoten in einer festen Reihenfolge besuchen:

| Verfahren     | Reihenfolge                        | Ergebnis für den Baum oben |
| ------------- | ---------------------------------- | -------------------------- |
| **Inorder**   | links → Knoten → rechts            | 1, 3, 6, 8, 10, 14         |
| **Preorder**  | Knoten → links → rechts            | 8, 3, 1, 6, 10, 14         |
| **Postorder** | links → rechts → Knoten            | 1, 6, 3, 14, 10, 8         |

> **Merksatz:** Inorder auf einem Suchbaum liefert die Werte **sortiert**.

Einen Knoten baut man in Python als kleine **Klasse** mit einem Wert und zwei Verweisen auf die Kinder (`None` = kein Kind):

```python
class Knoten:
    def __init__(self, wert):
        self.wert = wert
        self.links = None
        self.rechts = None

wurzel = Knoten(8)
wurzel.links = Knoten(3)
wurzel.rechts = Knoten(10)
print(wurzel.links.wert)   # → 3
```

Die Inorder-Traversierung schreibt man am einfachsten **rekursiv**:

```
inorder(knoten):
    WENN knoten leer ist: fertig
    inorder(linkes Kind)
    gib knoten.wert aus
    inorder(rechtes Kind)
```

Eine Funktion, die sich selbst aufruft, heißt **rekursiv**. Sie braucht immer eine **Abbruchbedingung**, sonst läuft sie endlos.

### Graph

Ein Graph besteht aus **Knoten** (z. B. Orte, Personen) und **Kanten** (Verbindungen). Anders als beim Baum darf es Kreise geben, und es gibt keine Wurzel. Beispiele: Straßennetz, Freundschaften im sozialen Netzwerk, Internet.

Speichern als **Adjazenzliste** – ein Dictionary „Knoten → Liste seiner Nachbarn“:

```python
stadtplan = {
    "A": ["B", "C"],
    "B": ["A", "D"],
    "C": ["A"],
    "D": ["B"],
}
print(stadtplan["A"])   # Nachbarn von A → ['B', 'C']
```

**Breitensuche (BFS)** besucht zuerst alle direkten Nachbarn, dann deren Nachbarn usw. Sie nutzt eine **Queue** und merkt sich besuchte Knoten, damit niemand doppelt besucht wird:

```
besucht ← {start}
queue   ← Warteschlange mit start
SOLANGE die queue nicht leer ist:
    knoten ← vorderstes Element der queue entnehmen
    knoten ausgeben
    FÜR jeden nachbar von knoten:
        WENN nachbar noch nicht besucht:
            nachbar als besucht merken
            nachbar hinten an die queue anhängen
```

Für den Stadtplan oben und Start `A` ergibt das die Reihenfolge **A B C D**. In Python prüfst du mit `in` bzw. `not in`, ob etwas in einer Menge oder Liste enthalten ist: `"B" in besucht`.

Die **Tiefensuche (DFS)** geht dagegen erst so tief wie möglich und nutzt dafür einen **Stack** (oder Rekursion). BFS findet in ungewichteten Graphen den **kürzesten Weg** (wenigste Kanten).

---

## 5. Die richtige Wahl treffen

| Problem                                    | Passende Struktur / Verfahren      |
| ------------------------------------------ | ---------------------------------- |
| Schnell nach einem Namen nachschlagen      | Dictionary                         |
| „Rückgängig“-Funktion, Browser-Zurück      | Stack                              |
| Aufträge der Reihe nach abarbeiten         | Queue                              |
| Hierarchie (Ordner, Kategorien)            | Baum                               |
| Netzwerk aus Beziehungen, Wege finden      | Graph + Breitensuche               |
| Suche in großer, sortierter Liste          | Binäre Suche                       |
| Suche in kleiner oder unsortierter Liste   | Lineare Suche                      |

**Beispiel Empfehlungssystem:** Nutzer und ihre Freundschaften bilden einen **Graphen**. „Freunde von Freunden“ findet man per Breitensuche mit Tiefe 2. Wie oft ein Film gesehen wurde, zählt man schnell in einem **Dictionary** („Film → Anzahl“) und sortiert danach.

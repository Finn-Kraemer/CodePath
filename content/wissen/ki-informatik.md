---
title: KI & Informatik
icon: 🤖
module: ki-informatik
---

# Künstliche Intelligenz (KI)

**Künstliche Intelligenz** bezeichnet Systeme, die Aufgaben lösen, für die man sonst menschliche Intelligenz braucht – etwa Bilder erkennen, Sprache verstehen oder Vorhersagen treffen. Die meisten heutigen KI-Systeme beruhen auf **maschinellem Lernen**: Sie werden nicht Regel für Regel programmiert, sondern **lernen aus Beispielen**.

---

## 1. Regeln oder Lernen?

| Klassisches Programm                         | Lernendes System (KI)                               |
| -------------------------------------------- | --------------------------------------------------- |
| Mensch schreibt **feste Regeln**             | System **leitet Muster aus Daten ab**               |
| `if temperatur > 30: print("heiß")`          | erkennt Katzen auf Fotos nach 10 000 Beispielbildern |
| verhält sich immer exakt gleich              | wird mit mehr/besseren Daten besser                 |
| nachvollziehbar                              | oft schwer zu erklären („Black Box“)                |

**Ist das KI?** Ein Taschenrechner oder ein Wecker folgen festen Regeln → **keine** KI. Ein Spamfilter, der aus markierten Mails lernt, eine Gesichtserkennung oder ein Chatbot wie ChatGPT → **KI**.

---

## 2. Wie lernt eine KI?

1. **Daten sammeln:** viele Beispiele, z. B. Fotos von Äpfeln und Birnen.
2. **Beschriften (Labeln):** zu jedem Beispiel die richtige Antwort („Apfel“).
3. **Merkmale (Features):** messbare Eigenschaften, z. B. Farbe, Form, Gewicht.
4. **Training:** Das Modell macht Vorhersagen, vergleicht sie mit der richtigen Antwort und passt sich so an, dass der **Fehler kleiner** wird – sehr oft hintereinander.
5. **Testen:** Mit **neuen** Daten prüfen, wie gut es wirklich ist.
6. **Einsetzen:** Das fertige Modell macht Vorhersagen für unbekannte Fälle.

---

## 3. Trainings- und Testdaten

Die vorhandenen Daten werden **aufgeteilt**, typischerweise etwa **80 % Training** und **20 % Test**.

- **Trainingsdaten:** Daraus lernt das Modell die Muster.
- **Testdaten:** Diese hat das Modell **nie gesehen**. Nur so erfährt man, ob es das Gelernte auf Neues übertragen kann (**Generalisierung**).

> Vergleich: Wer für eine Klassenarbeit nur die Lösungen der Übungsblätter auswendig lernt, scheitert an neuen Aufgaben. Deshalb testet man mit Aufgaben, die man vorher nicht kannte.

**Overfitting (Überanpassung):** Das Modell lernt die Trainingsdaten auswendig – samt zufälliger Besonderheiten – und ist auf den Testdaten schlecht. Erkennbar an: Training fast 100 %, Test deutlich schlechter.
**Underfitting:** Das Modell ist zu einfach und ist schon auf den Trainingsdaten schlecht.

---

## 4. Lernarten

| Lernart                        | Daten                         | Ziel                                    | Beispiel                                  |
| ------------------------------ | ----------------------------- | --------------------------------------- | ----------------------------------------- |
| **Überwacht** (Supervised)     | **mit** Labels (richtige Antwort bekannt) | Vorhersage für neue Fälle    | Spam/kein Spam, Hauspreis vorhersagen     |
| **Unüberwacht** (Unsupervised) | **ohne** Labels               | selbst Gruppen und Strukturen finden    | Kunden in Gruppen mit ähnlichem Kaufverhalten einteilen |
| **Bestärkend** (Reinforcement) | Belohnung / Strafe für Aktionen | beste Strategie durch Ausprobieren    | KI lernt ein Spiel, Roboter lernt laufen  |

Beim überwachten Lernen unterscheidet man **Klassifikation** (Kategorie vorhersagen: „Katze“ oder „Hund“) und **Regression** (Zahl vorhersagen: Preis, Temperatur).

---

## 5. Nächster Nachbar (k-Nearest-Neighbors, kNN)

Einer der einfachsten Lernalgorithmen: Um einen neuen Punkt einzuordnen, schaut man, welche **bekannten Punkte am nächsten** liegen. Die Mehrheit der **k** nächsten Nachbarn bestimmt die Klasse.

```
Gewicht ▲         ● = Apfel   ▲ = Birne   ? = neu
        │   ▲  ▲
        │ ▲   ?  ●
        │       ●  ●
        └──────────────▶ Rundheit
```

Bei k = 3: Sind von den drei nächsten Nachbarn zwei Äpfel und eine Birne → **Apfel**.

```python
# Bekannte Früchte: (Gewicht in g, Sorte)
bekannt = [(120, "Apfel"), (150, "Apfel"), (180, "Birne"), (200, "Birne")]
neu = 160

naechster = bekannt[0]
for frucht in bekannt:
    # abs() = Betrag, also Abstand ohne Vorzeichen
    if abs(frucht[0] - neu) < abs(naechster[0] - neu):
        naechster = frucht

print(f"{neu} g ist vermutlich: {naechster[1]}")   # → Apfel (150 g ist am nächsten)
```

Bei mehreren Merkmalen nimmt man den Abstand im Raum (z. B. Satz des Pythagoras: √((x₁−x₂)² + (y₁−y₂)²)).

---

## 6. Entscheidungsbäume

Ein **Entscheidungsbaum** stellt nacheinander Ja/Nein-Fragen, bis eine Entscheidung feststeht. Das Modell lernt aus den Daten, welche Fragen am besten trennen. Programmiert ist das einfach eine Folge **verschachtelter Bedingungen**:

```
            Regnet es?
           /          \
         ja            nein
        /                \
  Drinnen spielen    Temperatur > 15 °C?
                      /          \
                    ja            nein
               Fußball         Spazieren
```

```python
def empfehlung(regen, temperatur):
    if regen:
        return "Drinnen spielen"
    else:
        if temperatur > 15:
            return "Fußball"
        else:
            return "Spazieren"

print(empfehlung(False, 20))   # → Fußball
```

**Vorteil:** gut nachvollziehbar – man kann jede Entscheidung erklären.

---

## 7. Neuronale Netze

Künstliche neuronale Netze sind lose dem Gehirn nachempfunden. Sie bestehen aus **Neuronen** in Schichten, die über **gewichtete Verbindungen** verbunden sind.

```
Input-Schicht      Hidden-Schicht(en)     Output-Schicht
   (Pixel)          (Muster: Kanten,        („Katze“ /
                     Formen, Ohren …)        „Hund“)
    ○ ─────┐          ○                        
    ○ ─────┼──────▶   ○   ──────────▶          ○ Katze
    ○ ─────┼──────▶   ○   ──────────▶          ○ Hund
    ○ ─────┘          ○                        
```

| Bestandteil         | Aufgabe                                                                                         |
| ------------------- | ----------------------------------------------------------------------------------------------- |
| **Input-Schicht**   | nimmt die Rohdaten auf (z. B. Helligkeit jedes Pixels)                                           |
| **Hidden-Schichten** | erkennen schrittweise immer komplexere Muster (Kanten → Formen → Objekte)                       |
| **Output-Schicht**  | gibt das Ergebnis aus, oft als Wahrscheinlichkeit („93 % Katze“)                                 |
| **Gewichte**        | Stärke jeder Verbindung – das ist das, was gelernt wird                                         |
| **Backpropagation** | Lernverfahren: Fehler am Ausgang berechnen und die Gewichte **rückwärts** durchs Netz anpassen |

Ein einzelnes Neuron rechnet: Eingaben × Gewichte aufsummieren, Ergebnis durch eine **Aktivierungsfunktion** schicken. Netze mit vielen Hidden-Schichten nennt man **Deep Learning**.

---

## 8. Wie gut ist ein Modell? Die Konfusionsmatrix

Bei einer Ja/Nein-Vorhersage (z. B. „Ist die Mail Spam?“) gibt es vier Fälle:

|                         | Vorhersage: Spam               | Vorhersage: kein Spam          |
| ----------------------- | ------------------------------ | ------------------------------ |
| **Wirklich Spam**       | ✅ richtig positiv (TP)         | ❌ falsch negativ (FN) – übersehen |
| **Wirklich kein Spam**  | ❌ falsch positiv (FP) – Fehlalarm | ✅ richtig negativ (TN)     |

**Genauigkeit (Accuracy)** = Anteil aller richtigen Vorhersagen:

```
Genauigkeit = (TP + TN) / (TP + TN + FP + FN)
```

**Beispiel:** 200 Mails, TP = 70, TN = 110, FP = 8, FN = 12 → (70 + 110) / 200 = 180 / 200 = **90 %**.

```python
vorhersagen = ["spam", "ok", "spam", "ok", "ok"]
wahrheit    = ["spam", "ok", "ok",   "ok", "spam"]

richtig = 0
for i in range(len(wahrheit)):
    if vorhersagen[i] == wahrheit[i]:
        richtig += 1

genauigkeit = richtig / len(wahrheit) * 100
print(f"Genauigkeit: {genauigkeit} %")   # → 60.0 %
```

> ⚠️ Genauigkeit allein kann täuschen: Sind nur 1 % der Mails Spam, erreicht ein Modell, das **immer** „kein Spam“ sagt, 99 % – und ist trotzdem nutzlos. Je nach Anwendung zählt mehr, wie viele echte Fälle gefunden werden (z. B. bei Krankheiten).

---

## 9. Bias – wenn KI Vorurteile lernt

Eine KI ist nur so gut wie ihre Daten. Sind die Trainingsdaten **einseitig**, übernimmt die KI die Verzerrung (**Bias**):

- Ein Bewerbungs-Filter lernt aus alten Einstellungen, in denen fast nur Männer genommen wurden → bevorzugt Männer.
- Eine Gesichtserkennung wurde vor allem mit hellhäutigen Gesichtern trainiert → erkennt andere schlechter.
- Ein Sprachmodell gibt Klischees aus dem Internet wieder.

**Gegenmaßnahmen:** vielfältige, ausgewogene Daten; Ergebnisse für verschiedene Gruppen getrennt prüfen; Menschen in wichtige Entscheidungen einbeziehen.

---

## 10. Ethik und Verantwortung

Wenn KI über Menschen mitentscheidet (Kredite, Bewerbungen, Polizei, Medizin), stellen sich Fragen:

| Frage                | Worum es geht                                                             |
| -------------------- | ------------------------------------------------------------------------- |
| **Fairness**         | Werden Gruppen benachteiligt?                                             |
| **Transparenz**      | Kann man nachvollziehen, warum die KI so entschieden hat?                 |
| **Verantwortung**    | Wer haftet für Fehler – Hersteller, Betreiber, Nutzer?                    |
| **Datenschutz**      | Woher stammen die Daten, und durften sie verwendet werden?                |
| **Menschliche Kontrolle** | Kann ein Mensch die Entscheidung prüfen und korrigieren?             |

Der **EU AI Act** teilt KI-Anwendungen nach Risiko ein und verbietet manche ganz (z. B. Social Scoring); für Hochrisiko-Anwendungen gelten strenge Auflagen.

---

## 11. Prompt Engineering

Sprachmodelle (Large Language Models, LLMs) wie ChatGPT oder Claude sagen Wort für Wort voraus, was am wahrscheinlichsten folgt. Je genauer deine Anfrage (**Prompt**), desto besser die Antwort.

**Bausteine eines guten Prompts:**

1. **Rolle / Kontext:** Wer bist du, worum geht es? („Ich bin Schüler der 9. Klasse …“)
2. **Aufgabe:** Was genau soll passieren? („Erkläre …“, „Finde den Fehler in …“)
3. **Format:** Wie soll die Antwort aussehen? („in 5 Stichpunkten“, „als Tabelle“)
4. **Einschränkungen:** Länge, Sprache, Schwierigkeitsgrad, was vermieden werden soll.
5. **Beispiele:** Zeig, was du dir vorstellst.

| ❌ Ungenau          | ✅ Präzise                                                                                              |
| ------------------ | ------------------------------------------------------------------------------------------------------ |
| „Hausaufgaben Hilfe“ | „Ich bin in der 8. Klasse. Erkläre mir den Satz des Pythagoras in einfachen Worten mit einem Beispiel aus dem Alltag. Höchstens 6 Sätze.“ |
| „Mach meinen Code besser“ | „Dieser Python-Code soll den Durchschnitt einer Liste berechnen, gibt aber einen Fehler aus. Erkläre den Fehler, ohne die komplette Lösung zu verraten.“ |

> ⚠️ Sprachmodelle können **überzeugend falsche** Antworten erfinden („Halluzinationen“). Prüfe wichtige Fakten nach und gib keine persönlichen Daten ein.

---

## 12. Ein KI-Projekt planen

1. **Problem:** Was soll vorhergesagt oder erkannt werden?
2. **Daten:** Welche Daten brauche ich, woher kommen sie, wie viele, wer beschriftet sie? Sind sie ausgewogen?
3. **Merkmale:** Welche Eigenschaften sind aussagekräftig?
4. **Modell:** Klassifikation oder Regression? Einfacher Ansatz (Entscheidungsbaum, kNN) oder neuronales Netz?
5. **Bewertung:** Mit Testdaten prüfen – welche Fehler sind besonders schlimm?
6. **Risiken:** Bias, Datenschutz, Missbrauch, was passiert bei Fehlentscheidungen?

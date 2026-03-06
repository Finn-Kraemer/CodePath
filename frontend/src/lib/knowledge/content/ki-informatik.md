---
title: KI & Informatik
icon: 🤖
---

# Künstliche Intelligenz (KI)

## Lernen und Testen

KIs lernen aus Daten. Dabei gibt es zwei wichtige Gruppen:
1. **Trainingsdaten:** Hier lernt die KI Muster (z.B. "Katzen haben spitze Ohren").
2. **Testdaten:** Diese Daten kennt die KI noch nicht. Hier prüfen wir, ob sie das Gelernte anwenden kann.

**Overfitting:** Wenn eine KI die Trainingsdaten einfach nur auswendig lernt, aber bei neuen Daten (Testdaten) versagt, nennt man das Overfitting.

## Bias (Vorurteile)

Eine KI ist nur so gut wie ihre Daten. Wenn historische Daten Vorurteile enthalten (z.B. wurden früher fast nur Männer für technische Jobs eingestellt), wird die KI dieses Muster übernehmen und männliche Bewerber bevorzugen. Dies nennt man **AI Bias**.

## k-Nearest Neighbor (kNN)

Dies ist einer der einfachsten KI-Algorithmen. Er schaut sich die "Nachbarn" eines Datenpunktes an. Wenn die meisten Nachbarn "Äpfel" sind, wird der neue Punkt wahrscheinlich auch ein Apfel sein.

### kNN Logik in Python:
```python
# Wir suchen den kleinsten Abstand (abs = Absolutwert)
naechster = punkte[0]
for p in punkte:
    if abs(p - gesucht) < abs(naechster - gesucht):
        naechster = p
```

## Prompt Engineering

Wie man mit einer KI spricht, bestimmt die Qualität der Antwort.
- **Schlecht:** "Hausaufgaben Hilfe"
- **Gut:** "Ich bin Schüler der 8. Klasse. Erkläre mir den Satz des Pythagoras in einfachen Worten mit einem konkreten Beispiel."
- **Tipp:** Gib der KI eine Rolle (z.B. "Handle als Professor") und definiere das Ziel genau.

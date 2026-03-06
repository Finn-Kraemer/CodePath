---
title: IT-Sicherheit
icon: 🔒
---

# IT-Sicherheit

## Phishing erkennen

Betrugsversuche per E-Mail nutzen oft psychologische Tricks. Achte auf die "Drei Warnsignale":
1. **Falsche Domain:** Die Adresse sieht fast richtig aus (z.B. `paypa1.com` statt `paypal.com`).
2. **Extreme Dringlichkeit:** "Dein Konto wird in 24h gelöscht!" erzeugt Panik.
3. **Verdächtige Links:** Der Link führt nicht zur echten Webseite, sondern zu einer unsicheren Adresse.

## Verschlüsselung (Caesar)

Eine der ältesten Methoden ist die Caesar-Verschlüsselung, bei der jeder Buchstabe um eine feste Anzahl von Stellen im Alphabet verschoben wird (A+3 = D).

### In Python umsetzen:
```python
# 'ord' macht aus einem Buchstaben eine Zahl
code = ord("A") # 65

# 'chr' macht aus einer Zahl einen Buchstaben
zeichen = chr(65 + 3) # "D"
```

## SQL Injection

Dies ist ein gefährlicher Angriff auf Datenbanken. Wenn ein Programmierer Benutzereingaben nicht filtert, kann ein Hacker eigenen SQL-Code einschleusen.
Ein Trick ist die Nutzung von `'--`, um den Rest der Passwortabfrage in einen Kommentar zu verwandeln und sich ohne Passwort einzuloggen.

## 2-Faktor-Authentifizierung (2FA)

Zwei Faktoren machen den Login viel sicherer. Man kombiniert:
1. **Wissen:** Etwas, das du weißt (dein Passwort).
2. **Besitz:** Etwas, das du hast (ein Einmal-Code auf deinem Handy).

## Security Teams

In der professionellen Sicherheit unterscheidet man:
- **Red Team:** Die Angreifer. Sie simulieren Hacks, um Schwachstellen zu finden.
- **Blue Team:** Die Verteidiger. Sie überwachen Systeme und reagieren auf Angriffe.

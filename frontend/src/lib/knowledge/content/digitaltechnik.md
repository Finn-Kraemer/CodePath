---
title: Digitaltechnik
icon: 📟
---

# Digitaltechnik

## Binärsystem (Basis 2)

Computer verstehen nur zwei Zustände: Strom an (1) oder Strom aus (0). Alles im Computer – Texte, Bilder, Musik – wird letztendlich als eine Folge von Nullen und Einsen gespeichert.

**Stellenwerte:** 128, 64, 32, 16, 8, 4, 2, 1.
- Dezimal 10 = 8 + 2 = `1010` binär.
- Dezimal 65 = 64 + 1 = `01000001` binär (Dies ist der Buchstabe "A").

### In Python umrechnen:
```python
# Binär-String zu Dezimalzahl (Basis 2)
zahl = int("1010", 2) # Ergibt 10

# Zahl zu ASCII-Zeichen umwandeln
buchstabe = chr(65) # Ergibt "A"
```

## Speichereinheiten

- **1 Bit:** Die kleinste Einheit (0 oder 1).
- **1 Byte:** 8 Bits (kann eine Zahl von 0 bis 255 speichern).
- **1 Kilobyte (KB):** 1024 Bytes.
- **1 Megabyte (MB):** 1024 KB.
- **1 Gigabyte (GB):** 1024 MB.

## Logikgatter

Logikgatter verarbeiten elektrische Signale nach festen Regeln. Hier sind die wichtigsten Gatter und ihre **Wahrheitstabellen** (1 = Strom an/Wahr, 0 = Strom aus/Falsch):

### UND (AND)
Der Ausgang ist nur 1, wenn **beide** Eingänge 1 sind.

| Eingang A | Eingang B | Ausgang (A AND B) |
| :---: | :---: | :---: |
| 0 | 0 | **0** |
| 0 | 1 | **0** |
| 1 | 0 | **0** |
| 1 | 1 | **1** |

### ODER (OR)
Der Ausgang ist 1, wenn **mindestens ein** Eingang 1 ist.

| Eingang A | Eingang B | Ausgang (A OR B) |
| :---: | :---: | :---: |
| 0 | 0 | **0** |
| 0 | 1 | **1** |
| 1 | 0 | **1** |
| 1 | 1 | **1** |

### NICHT (NOT)
Kehrt das Signal um (Invertierung). Hat nur einen Eingang.

| Eingang A | Ausgang (NOT A) |
| :---: | :---: |
| 0 | **1** |
| 1 | **0** |

### EXKLUSIV-ODER (XOR)
Der Ausgang ist 1, wenn die Eingänge **unterschiedlich** sind.

| Eingang A | Eingang B | Ausgang (A XOR B) |
| :---: | :---: | :---: |
| 0 | 0 | **0** |
| 0 | 1 | **1** |
| 1 | 0 | **1** |
| 1 | 1 | **0** |

## Hardware-Komponenten

- **CPU (Prozessor):** Das "Gehirn". Führt Berechnungen und Logik für Programme und Spiele aus.
- **RAM (Arbeitsspeicher):** Das Kurzzeitgedächtnis. Hält Daten für aktuell laufende Programme bereit. Geht beim Ausschalten verloren.
- **SSD/HDD (Festplatte):** Das Langzeitgedächtnis. Speichert Programme und Dateien dauerhaft.

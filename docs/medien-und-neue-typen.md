# Medien-Upload, „Markieren in Bildern“ und Logikgatter

Nachtrag zu Phase 5: Die beiden Typen, die einen Medien-Upload brauchten. Entscheidung zur Speicherung: ADR-7.

## Medien

| Thema | Umsetzung |
|---|---|
| Speicher | Tabelle `media` (V13), Bilder als `BYTEA`, Schlüssel = SHA-256 + Endung. Gleiche Dateien werden nur einmal gespeichert. |
| Hochladen | **Admin → Inhalte → Aufgabe**: „Bild in die Geschichte einfügen“ (fügt Markdown ein) und im Formular der Bild-Aufgabe „Bild wählen oder hochladen“, jeweils mit Auswahl bereits hochgeladener Bilder. API: `POST/GET /api/admin/media`, `DELETE /api/admin/media/{schlüssel}`. |
| Prüfung | PNG, JPEG, GIF, WebP – erkannt an den ersten Bytes, nicht an Endung oder Browser-Angabe; PNG/JPEG/GIF müssen sich als Bild lesen lassen; höchstens 2 MB und 4096 Pixel pro Seite. SVG und alles andere wird abgelehnt. Dateinamen werden bereinigt. |
| Ausliefern | `GET /api/media/{schlüssel}` nur für angemeldete Nutzer, mit `Content-Security-Policy: default-src 'none'; sandbox`, `nosniff` und einem Jahr Cache (der Inhalt zu einem Schlüssel ändert sich nie). |
| Löschen | Nur wenn keine Aufgabenversion – auch keine frühere – das Bild verwendet. |
| Markdown | Bilder nur aus `/api/media/`; fremde Bildquellen entfernt der Sanitizer (offline, kein Tracking). |
| Content-Pakete | Bilder unter `<modul>/media/<schlüssel>`. Export legt jedes verwendete Bild zum Modul; Import prüft, dass Name und Inhalt zusammenpassen, und lehnt Aufgaben mit fehlenden Bildern ab. `content/validate.py` prüft dasselbe in der CI, `content/tools/add_media.py` legt Bilder richtig benannt ab. Pakete dürfen jetzt bis 20 MB groß sein. |

## „Markieren in Bildern“ (`IMAGE_HOTSPOT`)

- Schülerinnen und Schüler klicken ins Bild und setzen nummerierte Markierungen; ein Klick auf eine Markierung entfernt sie.
- Die Zielbereiche (Rechtecke in Prozent) gehen nie an den Browser. Bewertet wird auf dem Server: getroffene Ziele minus Markierungen daneben, geteilt durch die Zahl der Ziele. Mehr als Ziele + 2 Markierungen sind nicht erlaubt – flächendeckendes Klicken lohnt sich nicht.
- Nach dem Lösen zeigt das Bild die Zielbereiche mit ihrer Erklärung.
- Im Editor werden Zielbereiche mit der Maus auf dem Bild aufgezogen und beschriftet.
- Beispiel: `it-sicherheit/s15` „Clean Desk“ – Bild erzeugt mit `content/tools/draw_clean_desk.py`.

## Logikgatter (`LOGIC_CIRCUIT`)

- Die Aufgabe nennt Eingänge, die Wahrheitstabelle (optional den Ausdruck), die erlaubten Gatter und eine Höchstzahl. Schülerinnen und Schüler bauen die Schaltung aus einer Gatterliste; jedes Gatter darf nur Eingänge und weiter oben stehende Gatter verwenden – Schleifen sind so ausgeschlossen.
- Schaltplan als SVG mit Schaltzeichen nach DIN EN 60617 (&, ≥1, =1, 1 mit Negationskreis). Live-Wahrheitstabelle: Eine Zeile anklicken zeigt die Signale im Plan.
- Bewertung auf dem Server über die Wahrheitstabelle – jede richtige Schaltung zählt, nicht nur die Musterlösung. Teilpunkte erst über der Hälfte der Zeilen (eine konstante Ausgabe trifft oft die Hälfte).
- Ausdrücke im Editor: `NOT`, `AND`, `OR`, `XOR`, `NAND`, `NOR`, `XNOR`, Klammern; auch `!`, `&`, `|`, `^`, `+`, `·`. Rangfolge NOT vor AND vor XOR vor OR. Die Musterschaltung wird im Editor mit demselben Baukasten gebaut und beim Prüfen gegen den Ausdruck getestet.
- Beispiel: `digitaltechnik/d16` „XOR aus Grundgattern bauen“.

## Tests

- `MediaTest`: Upload, gleiche Datei gleicher Schlüssel, Auslieferung mit Sicherheits-Headern nur für Angemeldete, nur die Lehrkraft lädt hoch, SVG/HTML/kaputte/zu große Bilder abgelehnt, verwendete Bilder nicht löschbar, Export enthält Bilder, Import mit falsch benanntem Bild lehnt Bild und Aufgabe ab.
- `HandlersTest`, `LogicCircuitTest`: Treffer, Fehlmarkierungen, Markierungslimit; Schaltungen mit Schleifen, falscher Eingangszahl oder verbotenen Gattern; Ausdrucksparser mit Rangfolge und Fehlern.
- Vitest: Logik im Browser (gleiche Regeln wie der Server), Markdown-Bilder.
- Playwright: beide Typen über die Oberfläche lösen; Bild-Aufgabe im Editor mit Upload und gezeichnetem Zielbereich anlegen und veröffentlichen.

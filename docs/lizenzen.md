# Lizenzhinweise für mitgelieferte Schriften

CodePath bettet in alle PDFs (Teilnahmebestätigung, Mitnehm-Paket, Bewertungsbögen, Aufgabenblätter) freie Unicode-Schriften ein. Eingebettet werden nur die Zeichen, die im Dokument vorkommen. Die Schriftdateien liegen unter `backend/src/main/resources/fonts/` und werden im JAR bzw. Docker-Image mitgeliefert (zusammen gut 10 MB).

| Schrift | Dateien | Wofür | Lizenz |
|---|---|---|---|
| DejaVu Sans, Sans Mono, Serif (2.37) | `DejaVuSans*.ttf`, `DejaVuSansMono*.ttf`, `DejaVuSerif*.ttf` | Haupttext, Code, Serifenschrift der Aufgabenblätter: Latein mit allen europäischen Sonderzeichen, Griechisch, Kyrillisch, Pfeile, mathematische Zeichen, Box-Drawing | Bitstream-Vera-Lizenz mit DejaVu-Ergänzungen (frei, auch zur Weitergabe), Text in `fonts/LICENSE-DejaVu.txt` |
| Noto Sans JP | `NotoSansJP-Regular.ttf` | Ausweichschrift für chinesische und japanische Schriftzeichen | SIL Open Font License 1.1, Text in `fonts/LICENSE-OFL.txt` |
| Noto Emoji (schwarzweiß) | `NotoEmoji-Regular.ttf` | Ausweichschrift für Emojis | SIL Open Font License 1.1, Text in `fonts/LICENSE-OFL.txt` |

Zeichen, die keine der Schriften kennt, entfallen im PDF. Emojis erscheinen schwarzweiß; Emoji-Folgen (z. B. Familien oder Hautfarben) werden als einzelne Zeichen dargestellt.

Technisch: `platform.pdf.PdfFonts` lädt die Schriften einmal (`BaseFont.IDENTITY_H`, eingebettet) und setzt Texte über einen `FontSelector` aus Hauptschrift und Ausweichschriften zusammen. `ArchitectureTest` stellt sicher, dass kein Renderer an `PdfFonts` vorbei die PDF-Standardschriften nutzt.

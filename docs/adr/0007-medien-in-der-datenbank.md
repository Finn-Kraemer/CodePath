# ADR-7: Bilder in der Datenbank, adressiert über ihren Inhalt

**Status:** Akzeptiert, umgesetzt mit „Markieren in Bildern“

**Kontext:** Aufgaben brauchen Bilder (Bild-Hotspots, Abbildungen im Text). Sie müssen versioniert mit den Aufgaben zusammenpassen, in Content-Paketen mitwandern und ins Backup. Der Betrieb soll bei zwei Containern ohne zusätzliches Volume bleiben (ADR-1).

**Entscheidung:** Bilder liegen als `BYTEA` in PostgreSQL. Der Schlüssel ist der SHA-256 des Inhalts plus Endung: Ein Schlüssel zeigt immer auf denselben Inhalt, deshalb dürfen Browser ihn ein Jahr cachen, und eine veröffentlichte Aufgabenversion kann ihr Bild nie „verlieren“, solange es nicht gelöscht wird – und Löschen ist nur erlaubt, wenn keine Version es verwendet. Erlaubt sind PNG, JPEG, GIF und WebP bis 2 MB, erkannt am Inhalt; SVG ist ausgeschlossen, weil es Skripte enthalten kann. In Content-Paketen liegen Bilder unter `<modul>/media/<schlüssel>`; der Import prüft, dass der Name zum Inhalt passt. Markdown zeigt nur Bilder aus `/api/media/`.

**Alternativen:** Dateisystem-Volume (zusätzliches Volume, eigenes Backup, Pfade können auseinanderlaufen); Objektspeicher wie S3/MinIO (dritter Dienst, für Schulserver zu viel); Bilder als Base64 in der Aufgaben-JSON (bläht jede Version und jeden Abruf auf).

**Konsequenzen:** Die Datenbank wächst mit den Bildern – bei Schulmaterial (wenige MB) unproblematisch, das Backup erfasst sie automatisch. Für Videos oder große Mengen wäre ein Objektspeicher die bessere Wahl.

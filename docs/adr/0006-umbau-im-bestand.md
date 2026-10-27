# ADR-6: Umbau im Bestand statt Neuschreiben

**Status:** Vorgeschlagen

**Kontext:** Der Stack bleibt, viele Funktionen (Zeugnis, Beamer, Admin-Akte) funktionieren fachlich. Eine Person nebenbei kann sich keinen langen Zeitraum ohne lauffähiges System leisten.

**Entscheidung:** Das bestehende Repo wird Phase für Phase umgebaut; jede Phase endet einsatzfähig. Datenbank-Migrationen starten mit einer neuen Basis-Migration, da keine Altdaten übernommen werden (Annahme A4).

**Alternativen:** Neuschreiben in einem neuen Repo (sauberer Start, aber monatelang nichts Einsetzbares).

**Konsequenzen:** Übergangsweise leben alter und neuer Code nebeneinander; Tests sichern jeden Schritt ab. Gilt A4 nicht, kommt ein Migrationsskript für Nutzer und Fortschritt hinzu.

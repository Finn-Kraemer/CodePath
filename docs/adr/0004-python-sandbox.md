# ADR-4: Python-Code serverseitig in einer Sandbox ausführen

**Status:** Angenommen (Phase 4), mit bubblewrap statt nsjail – siehe Nachtrag

**Kontext:** Solange der Browser die Ausgabe meldet, sind Code-Punkte fälschbar. Fremden Code auf dem Server auszuführen ist zugleich das größte Sicherheitsrisiko des Plans.

**Entscheidung:** Ein kleiner `runner`-Dienst mit Python, der jede Abgabe in nsjail ausführt: ohne Netzwerk, schreibgeschütztes Dateisystem, 2 s CPU, 64 MB Speicher. Die App ruft ihn nur intern auf. Pyodide im Browser bleibt zum Ausprobieren.

**Alternativen:** GraalPy in der JVM (Sandbox-Grenzen schwer prüfbar); ein Docker-Container je Abgabe (braucht den Docker-Socket); Code-Aufgaben ohne Punkte (sicher und billig, aber weniger Motivation).

**Konsequenzen:** Ein dritter Container. Fällt der Spike durch, gilt die Alternative „ohne Punkte“.

**Nachtrag Phase 4:** Der Spike lief mit bubblewrap statt nsjail. bubblewrap liegt als Paket in Debian und Ubuntu und wird von Flatpak breit eingesetzt; nsjail hätte aus dem Quellcode gebaut werden müssen. Die Grenzen setzen rlimits (2 s CPU, 256 MB Adressraum – 64 MB reichen Python nicht zum Starten –, 32 Prozesse, 1 MB Dateien) plus 5 s Wandzeit und 64 KB Ausgabe. Alle Angriffe des Spikes sind abgewehrt (`docs/historie/phase-4.md`). Der Runner ist ein optionaler dritter Container; ohne ihn gilt die Alternative „ohne Punkte“ automatisch.

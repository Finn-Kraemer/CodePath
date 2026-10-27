# ADR-4: Python-Code serverseitig in einer Sandbox ausführen

**Status:** Vorgeschlagen, abhängig von einem Spike in Phase 4

**Kontext:** Solange der Browser die Ausgabe meldet, sind Code-Punkte fälschbar. Fremden Code auf dem Server auszuführen ist zugleich das größte Sicherheitsrisiko des Plans.

**Entscheidung:** Ein kleiner `runner`-Dienst mit Python, der jede Abgabe in nsjail ausführt: ohne Netzwerk, schreibgeschütztes Dateisystem, 2 s CPU, 64 MB Speicher. Die App ruft ihn nur intern auf. Pyodide im Browser bleibt zum Ausprobieren.

**Alternativen:** GraalPy in der JVM (Sandbox-Grenzen schwer prüfbar); ein Docker-Container je Abgabe (braucht den Docker-Socket); Code-Aufgaben ohne Punkte (sicher und billig, aber weniger Motivation).

**Konsequenzen:** Ein dritter Container. Fällt der Spike durch, gilt die Alternative „ohne Punkte“.

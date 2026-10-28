package de.codepath.backend.engine.handlers;

import java.util.Map;

/** Hilfetexte des Terminals – wortgleich in frontend/src/lib/tasks/terminal-help.ts. */
final class TerminalHelp {

    private TerminalHelp() {
    }

    static final String HELP = String.join("\n",
            "Befehle (mehr zu jedem mit: man befehl)",
            "  Orientierung:  pwd, ls [-a], cd ordner, cd .., cd ~",
            "  Anlegen:       mkdir [-p] ordner, touch datei, echo \"text\" > datei",
            "  Ändern:        mv quelle ziel, cp [-r] quelle ziel, rm [-r] pfad",
            "  Lesen:         cat datei, head [-n 5] datei, tail [-n 5] datei",
            "  Suchen:        grep [-i -n -c -v -r] text datei, find ordner -name \"*.txt\"",
            "  Zählen:        wc [-l -w -c] datei, sort [-r], uniq [-c]",
            "  Verketten:     befehl | befehl, befehl > datei, befehl >> datei",
            "  Versionen:     git (mehr mit: git help)",
            "  Sonstiges:     history, clear, man befehl");

    static final Map<String, String> MAN = Map.ofEntries(
            Map.entry("pwd", "pwd – zeigt, in welchem Ordner du gerade bist (print working directory).\n  ~ ist dein Home-Ordner."),
            Map.entry("ls", "ls – listet, was im Ordner liegt. Ordner enden mit /.\n  ls ordner   zeigt einen anderen Ordner\n  ls -a       zeigt auch versteckte Dateien (Name beginnt mit .)"),
            Map.entry("cd", "cd – wechselt den Ordner (change directory).\n  cd ordner   hinein\n  cd ..       eine Ebene nach oben\n  cd ~        zurück in den Home-Ordner"),
            Map.entry("mkdir", "mkdir – legt einen Ordner an (make directory).\n  mkdir -p a/b/c   legt auch fehlende Zwischenordner an"),
            Map.entry("touch", "touch – legt eine leere Datei an.\n  touch a.txt b.txt   mehrere auf einmal"),
            Map.entry("rm", "rm – löscht Dateien (remove). Es gibt keinen Papierkorb!\n  rm -r ordner   löscht einen Ordner mit allem darin"),
            Map.entry("mv", "mv – verschiebt oder benennt um (move).\n  mv alt.txt neu.txt      umbenennen\n  mv bild.png bilder/     in einen Ordner verschieben"),
            Map.entry("cp", "cp – kopiert (copy).\n  cp a.txt b.txt\n  cp -r ordner kopie   ganze Ordner kopieren"),
            Map.entry("cat", "cat – zeigt den Inhalt einer Datei.\n  cat a.txt b.txt   mehrere hintereinander"),
            Map.entry("echo", "echo – gibt Text aus.\n  echo \"Hallo\" > datei    schreibt in eine Datei (überschreibt sie)\n  echo \"Hallo\" >> datei   hängt eine Zeile an"),
            Map.entry("head", "head – zeigt die ersten Zeilen einer Datei (Standard: 10).\n  head -n 3 datei   nur die ersten 3"),
            Map.entry("tail", "tail – zeigt die letzten Zeilen einer Datei (Standard: 10).\n  tail -n 3 datei   nur die letzten 3 – praktisch für Logs"),
            Map.entry("grep", "grep – findet Zeilen, die einen Text enthalten.\n  grep ERROR server.log\n  -i  Groß-/Kleinschreibung egal   -n  mit Zeilennummer\n  -c  nur zählen                   -v  Zeilen OHNE den Text\n  -r  in allen Dateien eines Ordners suchen"),
            Map.entry("find", "find – sucht Dateien und Ordner nach Namen.\n  find . -name \"*.txt\"     alle .txt-Dateien ab hier\n  find ordner -type d      nur Ordner (-type f: nur Dateien)"),
            Map.entry("wc", "wc – zählt Zeilen, Wörter und Zeichen (word count).\n  wc -l datei   nur Zeilen"),
            Map.entry("sort", "sort – sortiert Zeilen alphabetisch.\n  sort -r   umgekehrt"),
            Map.entry("uniq", "uniq – fasst gleiche Zeilen direkt hintereinander zusammen.\n  uniq -c   mit Anzahl – meist nach sort: sort datei | uniq -c"),
            Map.entry("history", "history – zeigt deine bisherigen Befehle."),
            Map.entry("clear", "clear – leert den Bildschirm."),
            Map.entry("git", "git – Versionsverwaltung. Übersicht mit: git help"),
            Map.entry("man", "man – zeigt die Hilfe zu einem Befehl (manual).\n  man grep"));
}

/** Hilfetexte des Terminals – wortgleich in TerminalHelp.java im Backend. */
export const HELP = [
	'Befehle (mehr zu jedem mit: man befehl)',
	'  Orientierung:  pwd, ls [-a], cd ordner, cd .., cd ~',
	'  Anlegen:       mkdir [-p] ordner, touch datei, echo "text" > datei',
	'  Ändern:        mv quelle ziel, cp [-r] quelle ziel, rm [-r] pfad',
	'  Lesen:         cat datei, head [-n 5] datei, tail [-n 5] datei',
	'  Suchen:        grep [-i -n -c -v -r] text datei, find ordner -name "*.txt"',
	'  Zählen:        wc [-l -w -c] datei, sort [-r], uniq [-c]',
	'  Verketten:     befehl | befehl, befehl > datei, befehl >> datei',
	'  Versionen:     git (mehr mit: git help)',
	'  Sonstiges:     history, clear, man befehl'
].join('\n');

export const MAN: Record<string, string> = {
	pwd: 'pwd – zeigt, in welchem Ordner du gerade bist (print working directory).\n  ~ ist dein Home-Ordner.',
	ls: 'ls – listet, was im Ordner liegt. Ordner enden mit /.\n  ls ordner   zeigt einen anderen Ordner\n  ls -a       zeigt auch versteckte Dateien (Name beginnt mit .)',
	cd: 'cd – wechselt den Ordner (change directory).\n  cd ordner   hinein\n  cd ..       eine Ebene nach oben\n  cd ~        zurück in den Home-Ordner',
	mkdir:
		'mkdir – legt einen Ordner an (make directory).\n  mkdir -p a/b/c   legt auch fehlende Zwischenordner an',
	touch: 'touch – legt eine leere Datei an.\n  touch a.txt b.txt   mehrere auf einmal',
	rm: 'rm – löscht Dateien (remove). Es gibt keinen Papierkorb!\n  rm -r ordner   löscht einen Ordner mit allem darin',
	mv: 'mv – verschiebt oder benennt um (move).\n  mv alt.txt neu.txt      umbenennen\n  mv bild.png bilder/     in einen Ordner verschieben',
	cp: 'cp – kopiert (copy).\n  cp a.txt b.txt\n  cp -r ordner kopie   ganze Ordner kopieren',
	cat: 'cat – zeigt den Inhalt einer Datei.\n  cat a.txt b.txt   mehrere hintereinander',
	echo: 'echo – gibt Text aus.\n  echo "Hallo" > datei    schreibt in eine Datei (überschreibt sie)\n  echo "Hallo" >> datei   hängt eine Zeile an',
	head: 'head – zeigt die ersten Zeilen einer Datei (Standard: 10).\n  head -n 3 datei   nur die ersten 3',
	tail: 'tail – zeigt die letzten Zeilen einer Datei (Standard: 10).\n  tail -n 3 datei   nur die letzten 3 – praktisch für Logs',
	grep: 'grep – findet Zeilen, die einen Text enthalten.\n  grep ERROR server.log\n  -i  Groß-/Kleinschreibung egal   -n  mit Zeilennummer\n  -c  nur zählen                   -v  Zeilen OHNE den Text\n  -r  in allen Dateien eines Ordners suchen',
	find: 'find – sucht Dateien und Ordner nach Namen.\n  find . -name "*.txt"     alle .txt-Dateien ab hier\n  find ordner -type d      nur Ordner (-type f: nur Dateien)',
	wc: 'wc – zählt Zeilen, Wörter und Zeichen (word count).\n  wc -l datei   nur Zeilen',
	sort: 'sort – sortiert Zeilen alphabetisch.\n  sort -r   umgekehrt',
	uniq: 'uniq – fasst gleiche Zeilen direkt hintereinander zusammen.\n  uniq -c   mit Anzahl – meist nach sort: sort datei | uniq -c',
	history: 'history – zeigt deine bisherigen Befehle.',
	clear: 'clear – leert den Bildschirm.',
	git: 'git – Versionsverwaltung. Übersicht mit: git help',
	man: 'man – zeigt die Hilfe zu einem Befehl (manual).\n  man grep'
};

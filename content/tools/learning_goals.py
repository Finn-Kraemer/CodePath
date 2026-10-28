"""Lernziele (ein Satz je Aufgabe) aus der inhaltlichen Durchsicht für CodePath 2.0.

    python content/tools/learning_goals.py   # schreibt die Lernziele in die Content-Pakete
"""
import json
from pathlib import Path

GOALS = {
    # Algorithmen
    "a1": "Du kannst erklären, was einen Algorithmus ausmacht: eindeutige, endliche Schritte zu einem Ziel.",
    "a2": "Du kannst abschätzen, wie viele Vergleiche die lineare Suche im schlechtesten Fall braucht.",
    "a3": "Du kannst eine lineare Suche in Python schreiben.",
    "a4": "Du kannst begründen, warum Halbieren bei sortierten Daten viel schneller ist als Durchzählen.",
    "a5": "Du kannst die binäre Suche selbst implementieren.",
    "a6": "Du kannst das LIFO-Prinzip eines Stacks an einem Alltagsbeispiel erklären.",
    "a7": "Du kannst einen Stack mit einer Python-Liste umsetzen.",
    "a8": "Du kannst eine Queue nach dem FIFO-Prinzip mit deque umsetzen.",
    "a9": "Du kannst beschreiben, was in einem Durchlauf von Bubble Sort passiert.",
    "a10": "Du kannst Bubble Sort selbst programmieren.",
    "a11": "Du kannst die Inorder-Traversierung eines Binärbaums von Hand durchführen.",
    "a12": "Du kannst einen Binärbaum in Python bauen und inorder durchlaufen.",
    "a13": "Du kannst einen Graphen als Adjazenzliste speichern und mit Breitensuche durchlaufen.",
    "a14": "Du kannst für ein Problem eine passende Datenstruktur und einen Algorithmus begründet auswählen.",
    # Cloud & Netze
    "c1": "Du kannst bei einem Verbindungsproblem systematisch von unten nach oben vorgehen.",
    "c2": "Du kannst gültige von ungültigen IPv4-Adressen unterscheiden.",
    "c3": "Du kannst erklären, wie DNS Namen in IP-Adressen übersetzt.",
    "c4": "Du kannst Vor- und Nachteile von Cloud-Diensten gegenüber eigenen Servern abwägen.",
    "c5": "Du kannst erklären, wann UDP besser passt als TCP.",
    "c6": "Du kannst eine Netzwerkfunktion der passenden OSI-Schicht zuordnen.",
    "c7": "Du kannst aus Ping-Messwerten eine durchschnittliche Antwortzeit berechnen.",
    "c8": "Du kannst aus einer Subnetzmaske die Zahl nutzbarer Adressen ableiten.",
    "c9": "Du kannst das Round-Robin-Verfahren zur Lastverteilung umsetzen.",
    "c10": "Du kannst den Aufbau einer HTTP-Anfrage mit Request-Zeile und Headern wiedergeben.",
    "c11": "Du kannst ein einfaches Netzwerk mit Bereichen, Adressen und Sicherheitsregeln planen.",
    # Datenbanken
    "db1": "Du kannst begründen, warum große Datenmengen in eine Datenbank statt in eine Tabellenkalkulation gehören.",
    "db2": "Du kannst eine einfache SELECT-Abfrage mit FROM und WHERE schreiben.",
    "db3": "Du kannst das Ergebnis einer SQL-Abfrage vorhersagen.",
    "db4": "Du kannst erklären, dass NULL „unbekannt“ bedeutet und nicht 0 oder leer.",
    "db5": "Du kannst erklären, wie ein JOIN Tabellen über Schlüssel verbindet.",
    "db6": "Du kannst Daten mit UPDATE und DELETE gezielt ändern und löschen.",
    "db7": "Du kannst GROUP BY mit Zählfunktionen anwenden.",
    "db8": "Du kannst Datensätze in Python mit einer Bedingung filtern.",
    "db9": "Du kannst erklären, warum Transaktionen „alles oder nichts“ ausführen.",
    "db10": "Du kannst das Prinzip eines JOINs in Python nachbauen.",
    "db11": "Du kannst Redundanz in einem Datenbankschema erkennen.",
    "db12": "Du kannst ein Datenbankschema mit Tabellen, Schlüsseln und Beziehungen entwerfen.",
    "db13": "Du kannst Beziehungen (1:n, n:m) in einem Datenmodell abbilden.",
    "sql-query-pro": "Du kannst eine Abfrage mit zwei verknüpften Bedingungen in SQL schreiben.",
    # Digitaltechnik
    "d1": "Du kannst berechnen, wie viele Zustände mit N Bits darstellbar sind.",
    "d2": "Du kannst Binärzahlen in Dezimalzahlen umrechnen.",
    "d3": "Du kannst Dezimalzahlen in Binärzahlen umrechnen.",
    "d4": "Du kannst erklären, wie Zeichen über den ASCII-Code als Zahlen gespeichert werden.",
    "d5": "Du kannst erklären, warum häufige Zeichen mit kurzen Codes Speicher sparen.",
    "d6": "Du kannst Codewörter aus einem Huffman-Baum ablesen.",
    "d7": "Du kannst Gatter in einer booleschen Formel erkennen und in der richtigen Reihenfolge auswerten.",
    "d8": "Du kannst die Gesetze von De Morgan anwenden.",
    "d9": "Du kannst die Wahrheitstabelle einer Schaltung aufstellen.",
    "d10": "Du kannst mit einem KV-Diagramm einen booleschen Ausdruck vereinfachen.",
    "d11": "Du kannst erklären, wie Don't-Care-Einträge die Vereinfachung erleichtern.",
    "d12": "Du kannst einen Zustandsautomaten aus einer Zustandstabelle nachvollziehen.",
    "d13": "Du kannst die Übergänge eines Zustandsautomaten für eine Ampel festlegen.",
    "d14": "Du kannst eine Anforderung in eine boolesche Schaltung übersetzen.",
    # Git
    "g1": "Du kannst erklären, welches Problem Versionskontrolle löst.",
    "g2": "Du kannst beschreiben, was ein Git-Repository enthält.",
    "g3": "Du kannst die drei Bereiche Arbeitsverzeichnis, Staging Area und Repository unterscheiden.",
    "g4": "Du kannst einen ersten Commit mit add und commit anlegen.",
    "g5": "Du kannst die Ausgabe von git status lesen.",
    "g6": "Du kannst eine aussagekräftige Commit-Nachricht schreiben.",
    "g7": "Du kannst begründen, warum neue Funktionen auf einem eigenen Branch entstehen.",
    "g8": "Du kannst Branches anlegen und zwischen ihnen wechseln.",
    "g9": "Du kannst beschreiben, was beim Zusammenführen zweier Branches passiert.",
    "g10": "Du kannst lokales und entferntes Repository unterscheiden.",
    "g11": "Du kannst Änderungen mit push und pull austauschen.",
    "g12": "Du kannst die Schritte eines typischen Team-Workflows ordnen.",
    "g13": "Du kannst entscheiden, welche Dateien nicht ins Repository gehören.",
    "g14": "Du kannst einen vollständigen Git-Workflow für eine Teamaufgabe beschreiben.",
    "terminal-git-init": "Du kannst im Terminal Ordner und Dateien für ein Projekt anlegen.",
    # IT-Sicherheit
    "s1": "Du kannst typische Warnsignale einer Phishing-Mail erkennen.",
    "s2": "Du kannst die Stärke von Passwörtern einschätzen.",
    "s3": "Du kannst erklären, warum Passwörter gehasht und nicht verschlüsselt gespeichert werden.",
    "s4": "Du kannst erklären, wie Zwei-Faktor-Authentifizierung zusätzlich schützt.",
    "s5": "Du kannst die Caesar-Verschlüsselung umkehren.",
    "s6": "Du kannst erklären, wie eine SQL-Injection eine Anmeldung umgeht.",
    "s7": "Du kannst Regeln für sichere Passwörter in einem Programm prüfen.",
    "s8": "Du kannst Firewall-Regeln von oben nach unten auswerten.",
    "s9": "Du kannst Passwörter in Python hashen und vergleichen.",
    "s10": "Du kannst einen Brute-Force-Angriff auf die Caesar-Verschlüsselung programmieren.",
    "s11": "Du kannst einen Man-in-the-Middle-Angriff erkennen und Gegenmaßnahmen nennen.",
    "s12": "Du kannst Sicherheitslücken einer Anwendung strukturiert dokumentieren.",
    "s13": "Du kannst auf einen Angriff mit passenden Schutzmaßnahmen reagieren.",
    # KI & Informatik
    "ai1": "Du kannst lernende Systeme von fest programmierten Regeln unterscheiden.",
    "ai2": "Du kannst beschreiben, wie eine KI aus Beispielen Muster lernt.",
    "ai3": "Du kannst erklären, warum Trainings- und Testdaten getrennt werden.",
    "ai4": "Du kannst überwachtes und unüberwachtes Lernen unterscheiden.",
    "ai5": "Du kannst erkennen, wie einseitige Trainingsdaten zu Verzerrungen führen.",
    "ai6": "Du kannst den Nächste-Nachbarn-Algorithmus in Python nachvollziehen.",
    "ai7": "Du kannst einen präzisen Prompt mit Kontext und Ziel formulieren.",
    "ai8": "Du kannst die Genauigkeit eines Modells aus einer Konfusionsmatrix berechnen.",
    "ai9": "Du kannst die Genauigkeit eines Modells in Python berechnen.",
    "ai10": "Du kannst die Bestandteile eines einfachen neuronalen Netzes benennen.",
    "ai11": "Du kannst einen Entscheidungsbaum als verschachtelte Bedingungen programmieren.",
    "ai12": "Du kannst ethische Risiken automatisierter Entscheidungen benennen.",
    "ai13": "Du kannst ein KI-System von den Daten bis zur Bewertung planen.",
    # Programmierung
    "p1": "Du kannst Pseudocode von echtem Programmcode unterscheiden.",
    "p2": "Du kannst die Symbole eines Flussdiagramms lesen.",
    "p3": "Du kannst erklären, warum eine Variable vor ihrer Verwendung einen Wert braucht.",
    "p4": "Du kannst Zuweisungen Zeile für Zeile im Kopf nachvollziehen.",
    "p5": "Du kannst den passenden Vergleichsoperator für eine Bedingung wählen.",
    "p6": "Du kannst boolesche Ausdrücke mit and, or und not auswerten.",
    "p7": "Du kannst die Datentypen int, float, str und bool erkennen.",
    "p8": "Du kannst eine Verzweigung mit if, elif und else schreiben.",
    "p9": "Du kannst match-case lesen und den Platzhalter _ erklären.",
    "p10": "Du kannst über einen Index auf Listenelemente zugreifen und weißt, dass bei 0 gezählt wird.",
    "p11": "Du kannst range() mit Start, Ende und Schrittweite einsetzen.",
    "p12": "Du kannst mit einer Schleife über ein Dictionary laufen.",
    "p13": "Du kannst Schleifen, Modulo und Verzweigungen in einem Programm kombinieren.",
    "p14": "Du kannst ein kleines Programm planen, umsetzen und deinen Entwurf erklären.",
    # Web-Entwicklung
    "w1": "Du kannst das Client-Server-Prinzip erklären.",
    "w2": "Du kannst die Bestandteile einer URL benennen und Fehler darin finden.",
    "w3": "Du kannst vorhersagen, wie der Browser eine HTML-Seite darstellt.",
    "w4": "Du kannst das Grundgerüst einer HTML-Seite mit passenden Tags aufbauen.",
    "w5": "Du kannst häufige HTTP-Statuscodes ihrer Bedeutung zuordnen.",
    "w6": "Du kannst die Wirkung einfacher CSS-Regeln vorhersagen.",
    "w7": "Du kannst erklären, warum Passwörter per POST und nicht per GET übertragen werden.",
    "w8": "Du kannst ein HTML-Formular mit passenden Eingabefeldern bauen.",
    "w9": "Du kannst nachvollziehen, wie JavaScript auf einen Klick reagiert.",
    "w10": "Du kannst JSON in Python einlesen und auf Werte zugreifen.",
    "w11": "Du kannst verschachtelte Werte in einer API-Antwort finden.",
    "w12": "Du kannst erklären, wie eine Webseite sich an verschiedene Bildschirme anpasst.",
    "w13": "Du kannst eine vollständige Webseite mit HTML und CSS gestalten.",
    "w14": "Du kannst Endpunkte, Methoden und Statuscodes einer REST-API entwerfen.",
    "sorting-request-cycle": "Du kannst die Schritte einer Web-Anfrage vom Browser zum Server und zurück ordnen.",
}


def main():
    root = Path(__file__).resolve().parent.parent
    missing = []
    for path in sorted(root.glob("*/tasks/*.json")):
        task = json.loads(path.read_text(encoding="utf-8"))
        goal = GOALS.get(task["slug"])
        if goal is None:
            missing.append(task["slug"])
            continue
        task["learningGoal"] = goal
        path.write_text(json.dumps(task, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(GOALS)} Lernziele gesetzt; ohne Lernziel: {missing or 'keine'}")


if __name__ == "__main__":
    main()

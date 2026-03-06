package de.codepath.backend.common.content;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.tasks.Difficulty;
import de.codepath.backend.features.tasks.Task;
import de.codepath.backend.features.tasks.TaskType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class CloudModule extends BaseContentModule {

    @Override
    public Module getModuleDefinition() {
        return defineModule("cloud", "Cloud & Netzwerke", 
            "Verstehe wie das Internet physisch und virtuell verbunden ist.", "☁️", 6);
    }

    @Override
    public List<Task> getTasks(Module m) {
        return List.of(
            createTask(m, "c1", "Warum lädt die Seite nicht?",
                "<p><strong>Story:</strong> Du tippst <code>www.schule.de</code> in den Browser – aber es passiert nichts.</p><p><strong>Aufgabe:</strong> Was prüfst du als erstes?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 1,
                Map.of("options", List.of("Ob der Browser aktuell ist", "Ob du Internetverbindung hast (z.B. pingen)", "Ob die Webseite existiert", "Ob dein Computer genug RAM hat"),
                       "correct", List.of(1), "hint", "Bevor man komplexe Fehler sucht, sollte man die Basis-Verbindung prüfen.")),

            createTask(m, "c2", "IP-Adressen entziffern",
                "<p><strong>Story:</strong> Jeder Computer im Internet hat eine IP-Adresse.</p><p><strong>Aufgabe:</strong> Welche dieser Adressen ist eine gültige <strong>IPv4-Adresse</strong>?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 2,
                Map.of("options", List.of("999.1.1.1", "192.168.1 (nur 3 Teile)", "192.168.1.100", "hello.world.net"),
                       "correct", List.of(2), "hint", "Eine IPv4-Adresse hat genau 4 Zahlen von 0–255, getrennt durch Punkte.")),

            createTask(m, "c3", "DNS erklärt",
                "<p><strong>Story:</strong> DNS (Domain Name System) ist das Telefonbuch des Internets. Ergänze den Text!</p><p><strong>Aufgabe:</strong> Fülle die Lücken mit den korrekten Begriffen (DNS, IP, IP-Adresse).</p>",
                TaskType.FILL_BLANK, Difficulty.EASY, 15, 3,
                Map.of("template", "Wenn du www.google.de eintippst, fragt dein Browser zuerst einen ___-Server, der den Domainnamen in eine ___-Adresse übersetzt. Ohne DNS müsstest du dir für jede Webseite eine ___ merken.",
                       "blanks", List.of(Map.of("index", 0, "answer", "DNS", "caseSensitive", false), Map.of("index", 1, "answer", "IP", "caseSensitive", false), Map.of("index", 2, "answer", "IP-Adresse", "caseSensitive", false)),
                       "hint", "DNS übersetzt lesbare Namen in computerlesbare Adressen.")),

            createTask(m, "c4", "Cloud vs. Lokal",
                "<p><strong>Story:</strong> Deine Schule überlegt: Soll das Zeugnisverwaltungsprogramm auf einem lokalen Server in der Schule laufen – oder in der Cloud?</p><p><strong>Aufgabe:</strong> Was ist ein Vorteil der Cloud-Lösung?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 4,
                Map.of("options", List.of("Daten sind immer offline verfügbar", "Es kostet einmalig mehr, dafür nie wieder", "Updates, Wartung und Backups übernimmt der Cloud-Anbieter", "Die Daten sind sicher vor Hacker-Angriffen"),
                       "correct", List.of(2), "hint", "Cloud-Anbieter spezialisieren sich auf Betrieb und Wartung von Infrastruktur.")),

            createTask(m, "c5", "Pakete auf Reisen",
                "<p><strong>Story:</strong> Wenn du eine große Datei (z.B. ein Video) über das Internet sendest, wird sie nicht als Ganzes übertragen.</p><p><strong>Aufgabe:</strong> Was passiert stattdessen mit der Datei?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 5,
                Map.of("options", List.of("Die Datei wartet bis die Leitung frei ist, dann wird sie am Stück gesendet", "Die Datei wird in kleine Pakete aufgeteilt, die einzeln durch das Netzwerk reisen", "Nur der Anfang der Datei wird gesendet, der Rest folgt wenn nötig", "Das Internet kopiert die Datei auf viele Server gleichzeitig"),
                       "correct", List.of(1), "hint", "Das Internet nutzt Paketvermittlung für einen effizienten Datentransport.")),

            createTask(m, "c6", "Ping-Analyse",
                "<p><strong>Story:</strong> Du simulierst einen einfachen Ping-Analyse-Code. Er berechnet die durchschnittliche Antwortzeit.</p><pre><code>ping_zeiten = [12, 45, 8, 23, 67, 11]\n\ngesamt = ___\nfor zeit in ping_zeiten:\n    gesamt ___ zeit\n\ndurchschnitt = gesamt / ___(ping_zeiten)\nprint(f\"Ø {durchschnitt:.1f} ms\")</code></pre><p><strong>Aufgabe:</strong> Ergänze die Lücken im Python-Code (0, +=, len).</p>",
                TaskType.FILL_CODE, Difficulty.MEDIUM, 15, 6,
                Map.of("template", "ping_zeiten = [12, 45, 8, 23, 67, 11]\n\ngesamt = ___\nfor zeit in ping_zeiten:\n    gesamt ___ zeit\n\ndurchschnitt = gesamt / ___(ping_zeiten)\nprint(f\"Ø {durchschnitt:.1f} ms\")",
                       "blanks", List.of(Map.of("index", 0, "answer", "0", "caseSensitive", true), Map.of("index", 1, "answer", "+=", "caseSensitive", true), Map.of("index", 2, "answer", "len", "caseSensitive", true)),
                       "expectedOutput", "Ø 27.7 ms\n", "hint", "Initialisiere die Summe mit 0, addiere jede Zeit mit += und nutze len() für die Anzahl.")),

            createTask(m, "c7", "Schulnetzwerk planen",
                "<p><strong>Story:</strong> Deine Schule bekommt ein neues Gebäude mit 20 Klassenräumen, einem Serverraum und WLAN für 600 Schüler. Du bist der Netzwerkplaner.</p><p><strong>Aufgabe:</strong> Erstelle ein Grobkonzept für die Vernetzung des neuen Gebäudes.</p>",
                TaskType.PRACTICE, Difficulty.HARD, 50, 7,
                Map.of("instructions", "Dein Konzept muss enthalten: 1. Verbindung der Räume (Kabel/WLAN?), 2. Standort von Router/Switch, 3. Inhaltsfilter für Schüler, 4. Backup-Plan bei Ausfall.",
                       "submissionNote", "Trage dein Netzwerkkonzept hier ein."))
        );
    }
}

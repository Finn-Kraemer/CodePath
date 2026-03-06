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
        return defineModule("cloud-netze", "Cloud & Netzwerke", 
            "Wie funktioniert das Internet? Verstehe IP-Adressen, Server und die Cloud.", "☁️", 4);
    }

    @Override
    public List<Task> getTasks(Module m) {
        return List.of(
            createTask(m, "c1", "Kein Internet?",
                "<p>Du tippst <code>www.schule.de</code> in den Browser – aber es passiert nichts.</p>",
                "<p>Was prüfst du als erstes?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 1,
                Map.of("options", List.of("Ob der Monitor an ist", "Ob das LAN-Kabel steckt oder WLAN verbunden ist", "Ob die Tastatur funktioniert"),
                       "correct", List.of(1), "hint", "Hardware-Verbindung (Layer 1) ist die häufigste Fehlerquelle.")),

            createTask(m, "c2", "IP-Adressen-Check",
                "<p>Jeder Computer im Internet hat eine IP-Adresse.</p>",
                "<p>Welche dieser Adressen ist eine gültige <strong>IPv4-Adresse</strong>?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 2,
                Map.of("options", List.of("192.168.1", "172.16.254.1", "300.1.2.3", "hello.world.1.1"),
                       "correct", List.of(1), "hint", "IPv4 besteht aus 4 Zahlen (0-255), getrennt durch Punkte.")),

            createTask(m, "c3", "Das Telefonbuch (DNS)",
                "<p>DNS (Domain Name System) ist das Telefonbuch des Internets. Ergänze den Text!</p>",
                "<p>Fülle die Lücken mit den korrekten Begriffen (DNS, IP, IP-Adresse).</p>",
                TaskType.FILL_BLANK, Difficulty.MEDIUM, 15, 3,
                Map.of("text", "Das ___ wandelt Namen wie google.de in eine ___ um. Computer kommunizieren eigentlich nur über diese ___.",
                       "blanks", List.of("DNS", "IP-Adresse", "IP-Adresse"), "hint", "Menschen merken sich Namen, Computer Zahlen.")),

            createTask(m, "c4", "Cloud-Vorteile",
                "<p>Deine Schule überlegt: Soll das Zeugnisverwaltungsprogramm auf einem lokalen Server in der Schule laufen – oder in der Cloud?</p>",
                "<p>Was ist ein Vorteil der Cloud-Lösung?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 4,
                Map.of("options", List.of("Man braucht gar kein Internet", "Die Daten sind nur auf Papier", "Wartung und Updates übernimmt der Anbieter", "Es kostet niemals Geld"),
                       "correct", List.of(2), "hint", "Cloud bedeutet: Jemand anderes kümmert sich um die Server-Hardware.")),

            createTask(m, "c5", "Pakete auf Reisen",
                "<p>Wenn du eine große Datei (z.B. ein Video) über das Internet sendest, wird sie nicht als Ganzes übertragen.</p>",
                "<p>Was passiert stattdessen mit der Datei?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 5,
                Map.of("options", List.of("Sie wird in kleine Pakete zerlegt", "Sie wird vor dem Senden ausgedruckt", "Sie wird in der Mitte durchgeschnitten"),
                       "correct", List.of(0), "hint", "TCP/IP zerlegt Daten in kleine, handliche Pakete.")),

            createTask(m, "c6", "Netzwerk-Code: Ping-Analyse",
                "<p>Du simulierst einen einfachen Ping-Analyse-Code. Er berechnet die durchschnittliche Antwortzeit.</p><pre><code>ping_zeiten = [12, 45, 8, 23, 67, 11]\n\ngesamt = ___\nfor zeit in ping_zeiten:\n    gesamt ___ zeit\n\ndurchschnitt = gesamt / ___(ping_zeiten)\nprint(f\"Ø {durchschnitt:.1f} ms\")</code></pre>",
                "<p>Ergänze die Lücken im Python-Code (0, +=, len).</p>",
                TaskType.FILL_CODE, Difficulty.MEDIUM, 15, 6,
                Map.of("template", "ping_zeiten = [12, 45, 8, 23, 67, 11]\n\ngesamt = ___\nfor zeit in ping_zeiten:\n    gesamt ___ zeit\n\ndurchschnitt = gesamt / ___(ping_zeiten)\nprint(f\"Ø {durchschnitt:.1f} ms\")",
                       "blanks", List.of(Map.of("index", 0, "answer", "0", "caseSensitive", true), Map.of("index", 1, "answer", "+=", "caseSensitive", true), Map.of("index", 2, "answer", "len", "caseSensitive", true)),
                       "expectedOutput", "Ø 27.7 ms\n", "hint", "Initialisierung bei 0, Aufsummieren mit +=, Anzahl mit len().")),

            createTask(m, "c7", "Netzwerk-Architekt",
                "<p>Deine Schule bekommt ein neues Gebäude mit 20 Klassenräumen, einem Serverraum und WLAN für 600 Schüler. Du bist der Netzwerkplaner.</p>",
                "<p>Erstelle ein Grobkonzept für die Vernetzung des neuen Gebäudes.</p>",
                TaskType.PRACTICE, Difficulty.HARD, 50, 7,
                Map.of("instructions", "Dein Konzept muss enthalten: 1. Welche Hardware brauchst du (Router, Switches, Access Points)?, 2. Wo steht der Server?, 3. Wie sicherst du das WLAN ab?",
                       "submissionNote", "Skizziere deinen Plan."))
        );
    }
}

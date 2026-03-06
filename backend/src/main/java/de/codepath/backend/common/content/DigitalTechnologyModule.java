package de.codepath.backend.common.content;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.tasks.Difficulty;
import de.codepath.backend.features.tasks.Task;
import de.codepath.backend.features.tasks.TaskType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class DigitalTechnologyModule extends BaseContentModule {

    @Override
    public Module getModuleDefinition() {
        return defineModule("digitaltechnik", "Digitaltechnik", 
            "Von 0 und 1 zum Computer. Lerne Binärsystem, Logikgatter und Hardware kennen.", "📟", 2);
    }

    @Override
    public List<Task> getTasks(Module m) {
        return List.of(
            createTask(m, "d1", "Die Sprache der Computer",
                "<p>Ein Computer kennt nur zwei Zustände: <strong>Strom an</strong> (1) und <strong>Strom aus</strong> (0). Mit nur diesen zwei Zuständen speichert er Texte, Bilder, Videos – alles.</p>",
                "<p>Wie speichert ein Computer den Buchstaben \"A\"?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 1,
                Map.of("options", List.of("Als kleines gemaltes Bild", "Als eine bestimmte Folge von Nullen und Einsen (Binärcode)", "Er schreibt es auf die Festplatte"),
                       "correct", List.of(1), "hint", "Alles im Computer wird schlussendlich in Binärzahlen übersetzt.")),

            createTask(m, "d2", "Binär-Rechner",
                "<p>Im Binärsystem (Basis 2) hat jede Stelle doppelt so viel Wert wie die rechts davon: 1, 2, 4, 8, 16, …</p>",
                "<p>Was ist die Dezimalzahl für <code>1010</code> (binär)?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 2,
                Map.of("options", List.of("10", "8", "5", "1010"),
                       "correct", List.of(0), "hint", "8 + 0 + 2 + 0 = ?")),

            createTask(m, "d3", "Bit, Byte, Gigabyte",
                "<p>Dein neues Handy hat 256 GB Speicher. Aber was bedeutet das genau?</p>",
                "<p>Fülle die Lücken im Text (Bit, Byte, Kilobyte, Gigabyte).</p>",
                TaskType.FILL_BLANK, Difficulty.EASY, 15, 3,
                Map.of("text", "Die kleinste Einheit ist ein ___. 8 davon ergeben ein ___. 1024 Megabyte entsprechen einem ___.",
                       "blanks", List.of("Bit", "Byte", "Gigabyte"), "hint", "Die Einheiten steigen meist in 1024er Schritten.")),

            createTask(m, "d4", "Logik-Puzzle",
                "<p>Ein UND-Gatter gibt nur 1 aus, wenn BEIDE Eingaben 1 sind. Ein ODER-Gatter gibt 1 aus, wenn MINDESTENS EINE Eingabe 1 ist.</p>",
                "<p>Eine Alarmanlage soll auslösen wenn: <strong>Tür offen UND es ist nach 22 Uhr</strong>. Welches Gatter wird genutzt?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 4,
                Map.of("options", List.of("UND (AND)", "ODER (OR)", "NICHT (NOT)"),
                       "correct", List.of(0), "hint", "Beide Bedingungen müssen gleichzeitig wahr sein.")),

            createTask(m, "d5", "Binär-Code schreiben",
                "<p>Python kann Binärzahlen direkt verarbeiten. Dein Code soll eine Binärzahl umrechnen.</p><pre><code>binaer = \"1101\"\n\n# int() kann eine Zahl in einer anderen Basis umrechnen\ndezimal = ___(binaer, ___)\nprint(dezimal)  # → 13</code></pre>",
                "<p>Ergänze die Lücken im Python-Code (int, 2).</p>",
                TaskType.FILL_CODE, Difficulty.MEDIUM, 15, 5,
                Map.of("template", "binaer = \"1101\"\n\n# int() kann eine Zahl in einer anderen Basis umrechnen\ndezimal = ___(binaer, ___)\nprint(dezimal)",
                       "blanks", List.of(Map.of("index", 0, "answer", "int", "caseSensitive", true), Map.of("index", 1, "answer", "2", "caseSensitive", true)),
                       "expectedOutput", "13\n", "hint", "int(string, basis) wandelt einen String in eine Zahl um.")),

            createTask(m, "d6", "Hardware-Profi",
                "<p>Du spielst ein Videospiel. Welche Komponente ist dafür verantwortlich, dass das Spiel flüssig läuft und Berechnungen schnell macht?</p>",
                "<p>Welche Hardware-Komponente führt die eigentlichen Berechnungen durch?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 6,
                Map.of("options", List.of("Das Netzteil", "Die CPU (Prozessor)", "Das Gehäuse", "Die Maus"),
                       "correct", List.of(1), "hint", "CPU steht für Central Processing Unit.")),

            createTask(m, "d7", "Geheimcode-Knacker",
                "<p>Du hast eine Geheimbotschaft als ASCII-Binärcode erhalten: <code>01001000 01101001</code>. Schreibe ein Programm das Binärzahlen in Text umwandelt.</p>",
                "<p>Schreibe ein kurzes Skript, das die Binärzahlen 72 und 105 in Text umwandelt.</p>",
                TaskType.CODE, Difficulty.HARD, 20, 7,
                Map.of("starterCode", "b1 = 72\nb2 = 105\n\n# Tipp: chr() wandelt eine Zahl in ein Zeichen um\n# Deine Lösung hier",
                       "expectedOutput", "Hi\n", "hint", "Nutze print(chr(b1) + chr(b2)).")),

            createTask(m, "d8", "Mein Schul-PC",
                "<p>Du hast 800€ Budget und sollst einen Schul-Computer zusammenstellen. Plane den Computer und erkläre deine Entscheidungen.</p>",
                "<p>Erstelle eine Liste der Komponenten und begründe sie kurz.</p>",
                TaskType.PRACTICE, Difficulty.HARD, 50, 8,
                Map.of("instructions", "Deine Planung muss enthalten: 1. CPU, 2. RAM (wieviel?), 3. Festplatte (SSD oder HDD?), 4. Grafikkarte (nötig?), 5. Monitor.",
                       "submissionNote", "Liste deine Komponenten auf."))
        );
    }
}

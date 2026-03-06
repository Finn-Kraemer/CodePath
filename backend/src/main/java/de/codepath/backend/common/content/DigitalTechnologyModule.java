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
            "Entdecke die Logik hinter der Hardware – Bits, Bytes und Logikgatter.", "🔧", 7);
    }

    @Override
    public List<Task> getTasks(Module m) {
        return List.of(
            createTask(m, "dt1", "Alles ist eine Zahl",
                "<p><strong>Story:</strong> Ein Computer kennt nur zwei Zustände: <strong>Strom an</strong> (1) und <strong>Strom aus</strong> (0). Mit nur diesen zwei Zuständen speichert er Texte, Bilder, Videos – alles.</p><p><strong>Aufgabe:</strong> Wie speichert ein Computer den Buchstaben \"A\"?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 1,
                Map.of("options", List.of("Als gezeichneten Buchstaben in einem winzigen Bild", "Als Sprachaufnahme des Buchstabens", "Als eine Zahl (z.B. 65 im ASCII-Standard), die binär gespeichert wird", "\"A\" wird gar nicht gespeichert – Computer verstehen nur Zahlen"),
                       "correct", List.of(2), "hint", "Jedes Zeichen wird intern durch eine eindeutige Nummer (Code) repräsentiert.")),

            createTask(m, "dt2", "Binär zählen",
                "<p><strong>Story:</strong> Im Binärsystem (Basis 2) hat jede Stelle doppelt so viel Wert wie die rechts davon: 1, 2, 4, 8, 16, …</p><p><strong>Aufgabe:</strong> Was ist die Dezimalzahl für <code>1010</code> (binär)?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 2,
                Map.of("options", List.of("10", "10 – (8+0+2+0 = 10)", "12", "8"),
                       "correct", List.of(1), "hint", "Addiere die Stellenwerte: 8*1 + 4*0 + 2*1 + 1*0.")),

            createTask(m, "dt3", "Bits und Bytes",
                "<p><strong>Story:</strong> Dein neues Handy hat 256 GB Speicher. Aber was bedeutet das genau?</p><p><strong>Aufgabe:</strong> Fülle die Lücken im Text (Bit, Byte, Kilobyte, Gigabyte).</p>",
                TaskType.FILL_BLANK, Difficulty.EASY, 15, 3,
                Map.of("template", "Das kleinste Speicherelement heißt ___ und speichert eine 0 oder 1. 8 davon ergeben ein ___. 1024 Byte = 1 ___. 1024 MB = 1 ___.",
                       "blanks", List.of(Map.of("index", 0, "answer", "Bit", "caseSensitive", false), Map.of("index", 1, "answer", "Byte", "caseSensitive", false), Map.of("index", 2, "answer", "Kilobyte", "caseSensitive", false), Map.of("index", 3, "answer", "Gigabyte", "caseSensitive", false)),
                       "hint", "Bit ist die kleinste Einheit, Byte sind 8 Bit.")),

            createTask(m, "dt4", "Logikgatter: UND/ODER",
                "<p><strong>Story:</strong> Ein UND-Gatter gibt nur 1 aus, wenn BEIDE Eingaben 1 sind. Ein ODER-Gatter gibt 1 aus, wenn MINDESTENS EINE Eingabe 1 ist.</p><p><strong>Aufgabe:</strong> Eine Alarmanlage soll auslösen wenn: <strong>Tür offen UND es ist nach 22 Uhr</strong>. Welches Gatter wird genutzt?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 4,
                Map.of("options", List.of("ODER-Gatter – weil einer der Faktoren reicht", "UND-Gatter – weil BEIDE Bedingungen wahr sein müssen", "NICHT-Gatter", "Kein Gatter – das ist kein logisches Problem"),
                       "correct", List.of(1), "hint", "Welche logische Verknüpfung verlangt, dass beide Bedingungen gleichzeitig erfüllt sind?")),

            createTask(m, "dt5", "Binär zu Dezimal in Python",
                "<p><strong>Story:</strong> Python kann Binärzahlen direkt verarbeiten. Dein Code soll eine Binärzahl umrechnen.</p><pre><code>binaer = \"1101\"\n\n# int() kann eine Zahl in einer anderen Basis umrechnen\ndezimal = ___(binaer, ___)\nprint(dezimal)  # → 13</code></pre><p><strong>Aufgabe:</strong> Ergänze die Lücken im Python-Code (int, 2).</p>",
                TaskType.FILL_CODE, Difficulty.MEDIUM, 15, 5,
                Map.of("template", "binaer = \"1101\"\n\ndezimal = ___(binaer, ___)\nprint(dezimal)",
                       "blanks", List.of(Map.of("index", 0, "answer", "int", "caseSensitive", true), Map.of("index", 1, "answer", "2", "caseSensitive", true)),
                       "expectedOutput", "13\n", "hint", "Die Funktion int(string, basis) wandelt einen String in eine Ganzzahl um.")),

            createTask(m, "dt6", "CPU, RAM, SSD – wer macht was?",
                "<p><strong>Story:</strong> Du spielst ein Videospiel. Welche Komponente ist dafür verantwortlich, dass das Spiel flüssig läuft und Berechnungen schnell macht?</p><p><strong>Aufgabe:</strong> Welche Hardware-Komponente führt die eigentlichen Berechnungen durch?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 6,
                Map.of("options", List.of("SSD – weil das Spiel dort gespeichert ist", "RAM – weil der Arbeitsspeicher die Daten hält", "CPU (Prozessor) – sie führt die Berechnungen durch", "Grafikkarte – sie zeichnet alle Bilder"),
                       "correct", List.of(2), "hint", "Der 'Prozessor' ist das Gehirn des Computers.")),

            createTask(m, "dt7", "Binäre Geheimbotschaft",
                "<p><strong>Story:</strong> Du hast eine Geheimbotschaft als ASCII-Binärcode erhalten: <code>01001000 01101001</code>. Schreibe ein Programm das Binärzahlen in Text umwandelt.</p>",
                TaskType.CODE, Difficulty.HARD, 20, 7,
                Map.of("starterCode", "binaer_nachricht = [\"01001000\", \"01101001\"]\n\nergebnis = \"\"\nfor code in binaer_nachricht:\n    # Deine Logik hier\n    pass\n\nprint(ergebnis)",
                       "expectedOutput", "Hi\n", "hint", "Nutze int(code, 2) für die Dezimalzahl und chr(dezimal) für den Buchstaben.")),

            createTask(m, "dt8", "Bau deinen eigenen Computer (auf Papier)",
                "<p><strong>Story:</strong> Du hast 800€ Budget und sollst einen Schul-Computer zusammenstellen. Plane den Computer und erkläre deine Entscheidungen.</p>",
                TaskType.PRACTICE, Difficulty.HARD, 50, 8,
                Map.of("instructions", "Erkläre deine Wahl für: 1. CPU (Kerne?), 2. RAM (wieviel GB?), 3. Speicher (SSD/HDD?), 4. Was lässt du weg um zu sparen?",
                       "submissionNote", "Trage deine PC-Konfiguration hier ein."))
        );
    }
}

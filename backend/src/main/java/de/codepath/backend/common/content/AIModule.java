package de.codepath.backend.common.content;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.tasks.Difficulty;
import de.codepath.backend.features.tasks.Task;
import de.codepath.backend.features.tasks.TaskType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class AIModule extends BaseContentModule {

    @Override
    public Module getModuleDefinition() {
        return defineModule("ki-informatik", "KI & Informatik", 
            "Wie funktionieren ChatGPT und Co.? Erforsche die Welt der Künstlichen Intelligenz.", "🤖", 7);
    }

    @Override
    public List<Task> getTasks(Module m) {
        return List.of(
            createTask(m, "ai1", "KI oder nicht KI?",
                "<p>Welche dieser Dinge nutzt echte KI (maschinelles Lernen) – und welche nicht?</p>",
                "<p><em>\"Ein Taschenrechner berechnet 1000 × 1000 = 1.000.000\"</em> – Ist das KI?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 1,
                Map.of("options", List.of("Ja, das ist KI", "Nein, das ist nur ein fester Algorithmus"),
                       "correct", List.of(1), "hint", "KI lernt aus Daten. Ein Taschenrechner folgt festen Regeln.")),

            createTask(m, "ai2", "Der Lernprozess",
                "<p>Eine KI soll lernen, Katzenfotos von Hundefotos zu unterscheiden. Sie wird mit 100.000 beschrifteten Fotos trainiert.</p>",
                "<p>Was macht die KI während dieses Trainingsprozesses?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 2,
                Map.of("options", List.of("Sie speichert alle 100.000 Fotos einzeln", "Sie sucht nach Mustern (z.B. Ohrenform, Schnauze), um Unterschiede zu erkennen", "Sie fragt einen Menschen bei jedem Foto"),
                       "correct", List.of(1), "hint", "Maschinelles Lernen bedeutet, Muster in Daten zu finden.")),

            createTask(m, "ai3", "Training vs. Test",
                "<p>KI-Entwickler teilen ihre Daten immer in zwei Gruppen. Ergänze den Text!</p>",
                "<p>Fülle die Lücken mit den korrekten Fachbegriffen (Trainings, Test, Overfitting).</p>",
                TaskType.FILL_BLANK, Difficulty.MEDIUM, 15, 3,
                Map.of("text", "Man nutzt die ___ Daten zum Lernen. Mit den ___ Daten prüft man danach, ob die KI auch neue, unbekannte Beispiele erkennt. Wenn eine KI die Beispiele nur auswendig lernt, nennt man das ___.",
                       "blanks", List.of("Trainings", "Test", "Overfitting"), "hint", "Overfitting ist wie Auswendiglernen ohne Verständnis.")),

            createTask(m, "ai4", "Vorsicht: Vorurteile (Bias)",
                "<p>Eine KI wurde trainiert, Bewerbungen für Jobs zu bewerten. Sie wurde mit historischen Bewerbungsdaten aus den letzten 20 Jahren trainiert – damals wurden fast nur Männer eingestellt.</p>",
                "<p>Was ist das wahrscheinliche Problem mit dieser KI?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 20, 4,
                Map.of("options", List.of("Die KI wird zu langsam arbeiten", "Die KI wird Frauen schlechter bewerten, weil sie das Muster aus der Vergangenheit lernt", "Die KI wird gar keine Bewerber mehr auswählen"),
                       "correct", List.of(1), "hint", "KI spiegelt die Daten wider, mit denen sie trainiert wurde.")),

            createTask(m, "ai5", "KI-Code: Der nächste Nachbar",
                "<p>Der einfachste KI-Algorithmus: k-Nearest Neighbor (kNN). Er sagt: \"Was du bist, bestimmen deine Nachbarn.\" Dein Code soll den nächsten Nachbarn in einer Liste finden.</p><pre><code>punkte = [1, 4, 7, 10, 15]\ngesucht = 6\n\nnaechster = ___[0]\nfor p in punkte:\n    if ___(p - gesucht) < ___(naechster - gesucht):\n        naechster = p\n\nprint(naechster)  # → 7</code></pre>",
                "<p>Ergänze die Lücken im Python-Code (punkte, abs).</p>",
                TaskType.FILL_CODE, Difficulty.MEDIUM, 15, 5,
                Map.of("template", "punkte = [1, 4, 7, 10, 15]\ngesucht = 6\n\nnaechster = ___[0]\nfor p in punkte:\n    if ___(p - gesucht) < ___(naechster - gesucht):\n        naechster = p\n\nprint(naechster)",
                       "blanks", List.of(Map.of("index", 0, "answer", "punkte", "caseSensitive", true), Map.of("index", 1, "answer", "abs", "caseSensitive", true), Map.of("index", 2, "answer", "abs", "caseSensitive", true)),
                       "expectedOutput", "7\n", "hint", "Nutze abs() für den absoluten Abstand.")),

            createTask(m, "ai6", "Perfekte Prompts",
                "<p>Du willst, dass eine KI dir bei den Hausaufgaben hilft. Welcher Prompt bekommt die beste Antwort?</p>",
                "<p>Wähle den am besten formulierten Prompt aus.</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 6,
                Map.of("options", List.of("\"Schreib mir was über Informatik.\"", "\"Erkläre mir das Binärsystem für einen 10-jährigen, nutze Beispiele und halte es kurz.\"", "\"Was ist 1+1?\""),
                       "correct", List.of(1), "hint", "Gute Prompts sind spezifisch, geben eine Zielgruppe vor und definieren das Format.")),

            createTask(m, "ai7", "KI-Projektplaner",
                "<p>Du bewirbst dich als KI-Forscher. Die Aufgabe: Entwirf ein KI-System für ein echtes Problem (z.B. Spam-Erkennung, Song-Empfehlungen).</p>",
                "<p>Erstelle ein Konzept für dein eigenes KI-System.</p>",
                TaskType.PRACTICE, Difficulty.HARD, 50, 7,
                Map.of("instructions", "Dein Konzept muss enthalten: 1. Problemstellung, 2. Welche Daten brauchst du?, 3. Was soll die KI vorhersagen?, 4. Wie verhinderst du Fehler?",
                       "submissionNote", "Beschreibe dein KI-System detailliert."))
        );
    }
}

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
        return defineModule("ki", "Künstliche Intelligenz", 
            "Lerne die Grundlagen von Machine Learning und KI kennen.", "🤖", 5);
    }

    @Override
    public List<Task> getTasks(Module m) {
        return List.of(
            createTask(m, "ki1", "Ist das KI?",
                "<p><strong>Story:</strong> Welche dieser Dinge nutzt echte KI (maschinelles Lernen) – und welche nicht?</p><p><strong>Aufgabe:</strong> <em>\"Ein Taschenrechner berechnet 1000 × 1000 = 1.000.000\"</em> – Ist das KI?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 1,
                Map.of("options", List.of("Kein KI – der Taschenrechner folgt festen Regeln, er \"lernt\" nichts", "Das ist KI – er verarbeitet Daten", "Das ist schwache KI", "Das hängt vom Taschenrechner ab"),
                       "correct", List.of(0), "hint", "KI zeichnet sich dadurch aus, dass sie aus Daten lernt, statt nur festen Programmierregeln zu folgen.")),

            createTask(m, "ki2", "Wie lernt eine KI?",
                "<p><strong>Story:</strong> Eine KI soll lernen, Katzenfotos von Hundefotos zu unterscheiden. Sie wird mit 100.000 beschrifteten Fotos trainiert.</p><p><strong>Aufgabe:</strong> Was macht die KI während dieses Trainingsprozesses?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 2,
                Map.of("options", List.of("Sie speichert alle Fotos und vergleicht neue Bilder direkt damit", "Sie fragt einen Menschen bei jedem neuen Foto", "Sie sucht nach Mustern in den Daten (z.B. Ohrenform) und verbessert sich durch Fehler", "Sie schaut die Fotos an und denkt nach wie ein Mensch"),
                       "correct", List.of(2), "hint", "Maschinelles Lernen basiert auf statistischer Mustererkennung.")),

            createTask(m, "ki3", "Training vs. Test",
                "<p><strong>Story:</strong> KI-Entwickler teilen ihre Daten immer in zwei Gruppen. Ergänze den Text!</p><p><strong>Aufgabe:</strong> Fülle die Lücken mit den korrekten Fachbegriffen (Trainings, Test, Overfitting).</p>",
                TaskType.FILL_BLANK, Difficulty.EASY, 15, 3,
                Map.of("template", "Die ___ -Daten lernt die KI auswendig. Mit den ___ -Daten prüft man hinterher ob sie wirklich gelernt hat – oder nur auswendig gelernt hat. Wenn eine KI nur auf Trainingsdaten gut funktioniert, nennt man das ___.",
                       "blanks", List.of(Map.of("index", 0, "answer", "Trainings", "caseSensitive", false), Map.of("index", 1, "answer", "Test", "caseSensitive", false), Map.of("index", 2, "answer", "Overfitting", "caseSensitive", false)),
                       "hint", "Overfitting (Überanpassung) ist ein großes Problem, wenn das Modell zu sehr an den Trainingsdaten klebt.")),

            createTask(m, "ki4", "KI-Bias erkennen",
                "<p><strong>Story:</strong> Eine KI wurde trainiert, Bewerbungen für Jobs zu bewerten. Sie wurde mit historischen Bewerbungsdaten aus den letzten 20 Jahren trainiert – damals wurden fast nur Männer eingestellt.</p><p><strong>Aufgabe:</strong> Was ist das wahrscheinliche Problem mit dieser KI?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 4,
                Map.of("options", List.of("Die KI ist zu langsam", "Die KI hat den historischen Bias übernommen und bevorzugt wahrscheinlich männliche Bewerber", "Die KI kann keine PDFs lesen", "20 Jahre sind zu viele Daten"),
                       "correct", List.of(1), "hint", "KI spiegelt oft die Vorurteile (Bias) wider, die in ihren Trainingsdaten enthalten sind.")),

            createTask(m, "ki5", "Nearest Neighbor von Hand",
                "<p><strong>Story:</strong> Der einfachste KI-Algorithmus: k-Nearest Neighbor (kNN). Er sagt: \"Was du bist, bestimmen deine Nachbarn.\" Dein Code soll den nächsten Nachbarn in einer Liste finden.</p><pre><code>punkte = [1, 4, 7, 10, 15]\ngesucht = 6\n\nnaechster = ___[0]\nfor p in punkte:\n    if ___(p - gesucht) < ___(naechster - gesucht):\n        naechster = p\n\nprint(naechster)  # → 7</code></pre><p><strong>Aufgabe:</strong> Ergänze die Lücken im Python-Code (punkte, abs).</p>",
                TaskType.FILL_CODE, Difficulty.MEDIUM, 15, 5,
                Map.of("template", "punkte = [1, 4, 7, 10, 15]\ngesucht = 6\n\nnaechster = ___[0]\nfor p in punkte:\n    if ___(p - gesucht) < ___(naechster - gesucht):\n        naechster = p",
                       "blanks", List.of(Map.of("index", 0, "answer", "punkte", "caseSensitive", true), Map.of("index", 1, "answer", "abs", "caseSensitive", true), Map.of("index", 2, "answer", "abs", "caseSensitive", true)),
                       "expectedOutput", "7", "hint", "abs() gibt den absoluten Wert (Betrag) einer Zahl zurück.")),

            createTask(m, "ki6", "Prompt Engineering",
                "<p><strong>Story:</strong> Du willst, dass eine KI dir bei den Hausaufgaben hilft. Welcher Prompt bekommt die beste Antwort?</p><p><strong>Aufgabe:</strong> Wähle den am besten formulierten Prompt aus.</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 6,
                Map.of("options", List.of("\"Hausaufgaben\"", "\"Hilf mir\"", "\"Ich bin Schüler der 8. Klasse. Erkläre mir den Satz des Pythagoras in einfachen Worten mit einem Beispiel.\"", "\"Erkläre alles über Mathematik\""),
                       "correct", List.of(2), "hint", "Gute Prompts sind spezifisch, geben Kontext und definieren das Ziel klar.")),

            createTask(m, "ki7", "Trainiere deine eigene KI-Idee",
                "<p><strong>Story:</strong> Du bewirbst dich als KI-Forscher. Die Aufgabe: Entwirf ein KI-System für ein echtes Problem (z.B. Spam-Erkennung, Song-Empfehlungen).</p><p><strong>Aufgabe:</strong> Erstelle ein Konzept für dein eigenes KI-System.</p>",
                TaskType.PRACTICE, Difficulty.HARD, 50, 7,
                Map.of("instructions", "Dein Konzept muss beantworten: 1. Was ist der Input? 2. Was ist der Output? 3. Trainingsdaten (was und wie viele)? 4. Prüfung der Qualität? 5. Mögliche Probleme (Bias)?",
                       "submissionNote", "Beschreibe dein KI-Konzept hier."))
        );
    }
}

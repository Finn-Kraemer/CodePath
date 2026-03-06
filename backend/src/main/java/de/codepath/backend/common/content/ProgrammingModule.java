package de.codepath.backend.common.content;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.tasks.Difficulty;
import de.codepath.backend.features.tasks.Task;
import de.codepath.backend.features.tasks.TaskType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class ProgrammingModule extends BaseContentModule {

    @Override
    public Module getModuleDefinition() {
        return defineModule("programmierung", "Programmierung", 
            "Lerne die Grundlagen der Informatik mit Python – Variablen, Schleifen und Funktionen.", "🐍", 1);
    }

    @Override
    public List<Task> getTasks(Module m) {
        return List.of(
            createTask(m, "p1", "Der kaputte Roboter",
                "<p>Dein Roboter soll \"Hallo Welt\" sagen – aber er schweigt. Schau dir seinen Code an:</p><pre><code>nachricht = \"Hallo Welt\"\nPrint(nachricht)</code></pre>",
                "<p>Was ist das Problem?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 1,
                Map.of("options", List.of("\"nachricht\" ist kein gültiger Variablenname", "\"Print\" muss kleingeschrieben sein: \"print\"", "Der Text braucht keine Anführungszeichen", "Es fehlt ein Semikolon am Ende"),
                       "correct", List.of(1), "hint", "Python achtet streng auf Groß- und Kleinschreibung bei Funktionen.")),

            createTask(m, "p2", "Variablen-Detektiv",
                "<p>Ein Kollege hat diesen Code geschrieben. Ohne ihn auszuführen: Was gibt <code>print(ergebnis)</code> am Ende aus?</p><pre><code>zahl = 10\nzahl = zahl + 5\nzahl = zahl * 2\nergebnis = zahl - 3\nprint(ergebnis)</code></pre>",
                "<p>Berechne das Endergebnis im Kopf.</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 2,
                Map.of("options", List.of("20", "25", "27", "30"),
                       "correct", List.of(2), "hint", "Gehe den Code Zeile für Zeile durch: 10 + 5 = 15, dann 15 * 2 = ...")),

            createTask(m, "p3", "Lücke im Passwort-Generator",
                "<p>Du baust einen einfachen Passwort-Generator. Er soll 5 Sternchen ausgeben. Aber die Schleife fehlt noch:</p><pre><code>___ i ___ range(5):\n    print(\"*\")</code></pre>",
                "<p>Ergänze die Lücken im Code.</p>",
                TaskType.FILL_CODE, Difficulty.EASY, 15, 3,
                Map.of("template", "___ i ___ range(5):\n    print(\"*\")",
                       "blanks", List.of(Map.of("index", 0, "answer", "for", "caseSensitive", true), Map.of("index", 1, "answer", "in", "caseSensitive", true)),
                       "expectedOutput", "*\n*\n*\n*\n*\n", "hint", "Eine Standard-Schleife in Python beginnt mit 'for' und nutzt das Schlüsselwort 'in'.")),

            createTask(m, "p4", "FizzBuzz Klassiker",
                "<p>FizzBuzz ist ein legendärer Coding-Test in Vorstellungsgesprächen. Wenn du das löst, hättest du einen Job bekommen!</p>",
                "<p>Schreibe Code, der die Zahlen 1–15 ausgibt – aber:<br>- Vielfache von 3 → <code>Fizz</code><br>- Vielfache von 5 → <code>Buzz</code><br>- Vielfache von 3 UND 5 → <code>FizzBuzz</code></p>",
                TaskType.CODE, Difficulty.MEDIUM, 20, 4,
                Map.of("starterCode", "for i in range(1, 16):\n    # Deine Lösung hier\n    pass",
                       "expectedOutput", "1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz\n", "hint", "Nutze i % 3 == 0 um zu prüfen, ob i durch 3 teilbar ist. Wichtig: Prüfe 'FizzBuzz' zuerst!")),

            createTask(m, "p5", "Bug-Hunt: Die kaputte Funktion",
                "<p>Dein Kollege hat eine Funktion geschrieben, die den Flächeninhalt eines Rechtecks berechnen soll. Aber sie gibt immer das falsche Ergebnis zurück. Findest du den Bug?</p><pre><code>def flaeche(breite, hoehe):\n    return breite + hoehe\n\nprint(flaeche(4, 5))  # Sollte 20 ausgeben, gibt aber 9 aus</code></pre>",
                "<p>Was muss geändert werden?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 5,
                Map.of("options", List.of("Die Funktion heißt falsch", "Die Parameter sind in der falschen Reihenfolge", "Es muss \"*\" statt \"+\" sein (Fläche = Breite × Höhe)", "return fehlt"),
                       "correct", List.of(2), "hint", "Wie berechnet man mathematisch die Fläche eines Rechtecks?")),

            createTask(m, "p6", "Countdown-Rakete",
                "<p>Houston, wir haben ein Problem – der Raketen-Countdown läuft rückwärts... oder doch nicht?</p><pre><code>___ i in range(10, ___, ___):\n    print(i)\nprint(\"🚀 Start!\")</code></pre>",
                "<p>Ergänze den Code, damit von 10 bis 1 heruntergezählt wird.</p>",
                TaskType.FILL_CODE, Difficulty.MEDIUM, 15, 6,
                Map.of("template", "___ i in range(10, ___, ___):\n    print(i)\nprint(\"🚀 Start!\")",
                       "blanks", List.of(Map.of("index", 0, "answer", "for", "caseSensitive", true), Map.of("index", 1, "answer", "0", "caseSensitive", true), Map.of("index", 2, "answer", "-1", "caseSensitive", true)),
                       "expectedOutput", "10\n9\n8\n7\n6\n5\n4\n3\n2\n1\n🚀 Start!\n", "hint", "range(Start, Stopp, Schritt). Der Stopp-Wert ist exklusiv!")),

            createTask(m, "p7", "Schere-Stein-Papier Logik",
                "<p>Du programmierst den Kern eines Schere-Stein-Papier-Spiels. Regeln: <code>\"schere\"</code> schlägt <code>\"papier\"</code>, <code>\"stein\"</code> schlägt <code>\"schere\"</code>, <code>\"papier\"</code> schlägt <code>\"stein\"</code>.</p>",
                "<p>Schreibe eine Funktion <code>gewinner(spieler, computer)</code>, die den Gewinner (<code>\"spieler\"</code>, <code>\"computer\"</code> oder <code>\"unentschieden\"</code>) zurückgibt.</p>",
                TaskType.CODE, Difficulty.HARD, 20, 7,
                Map.of("starterCode", "def gewinner(spieler, computer):\n    # Deine Logik hier\n    pass\n\n# Test\nprint(gewinner(\"schere\", \"papier\"))\nprint(gewinner(\"stein\", \"stein\"))\nprint(gewinner(\"papier\", \"schere\"))",
                       "expectedOutput", "spieler\nunentschieden\ncomputer\n", "hint", "Nutze if/elif-Bedingungen. Vergiss das Unentschieden nicht!")),

            createTask(m, "p8", "Startup-Pitch: Dein erstes Programm",
                "<p>Du hast 5 Minuten, um deine App-Idee einem Investor zu präsentieren!</p>",
                "<p>Denk dir eine kleine App oder ein Programm aus, das du mit Python bauen würdest. Dann schreibe den Code (oder zumindest den wichtigsten Teil davon).</p>",
                TaskType.PRACTICE, Difficulty.HARD, 50, 8,
                Map.of("instructions", "Dein Pitch muss enthalten: 1. Was macht deine App? (1-2 Sätze), 2. Wer würde sie nutzen?, 3. Dein Code (Funktion, Schleife oder Bedingung), 4. Beispiel-Ausgabe.",
                       "submissionNote", "Beschreibe deine Idee und füge deinen Code-Entwurf ein."))
        );
    }
}

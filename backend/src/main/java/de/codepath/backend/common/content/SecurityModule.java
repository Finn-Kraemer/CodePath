package de.codepath.backend.common.content;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.tasks.Difficulty;
import de.codepath.backend.features.tasks.Task;
import de.codepath.backend.features.tasks.TaskType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class SecurityModule extends BaseContentModule {

    @Override
    public Module getModuleDefinition() {
        return defineModule("it-sicherheit", "IT-Sicherheit", 
            "Passwörter, Verschlüsselung und Angriffe – wie schützt man sich im digitalen Alltag?", "🔒", 3);
    }

    @Override
    public List<Task> getTasks(Module m) {
        return List.of(
            createTask(m, "s1", "Phishing-Mail enttarnen",
                "<p><strong>Story:</strong> Du bekommst folgende E-Mail. Wie viele Warnsignale findest du?</p><pre><code>Von: support@paypa1.com\nBetreff: ⚠️ DRINGEND: Ihr Konto wird in 24h gelöscht!!!\nInhalt: \"Sehr geehrter Kunde, klicken Sie JETZT auf den Link und bestätigen Sie Ihre Daten: http://paypal-sicher.xyz/login\"</code></pre><p><strong>Aufgabe:</strong> Wie viele Warnsignale findest du in dieser E-Mail?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 1,
                Map.of("options", List.of("Keine – das sieht legitim aus", "1 Warnsignal (der Link)", "2 Warnsignale", "Mindestens 3 Warnsignale (Absender, Dringlichkeit, URL)"),
                       "correct", List.of(3), "hint", "Achte auf die Schreibweise der Domain und die Art der Aufforderung.")),

            createTask(m, "s2", "Passwort-Stärke einschätzen",
                "<p><strong>Story:</strong> Ein Passwort-Checker gibt dir Punkte: Länge +1, Großbuchstaben +1, Zahlen +1, Sonderzeichen +1, kein Wörterbuch-Wort +1.</p><p><strong>Aufgabe:</strong> Wie viele Punkte bekommt das Passwort <code>Hund2024!</code>?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 2,
                Map.of("options", List.of("2 Punkte", "3 Punkte", "4 Punkte", "5 Punkte"),
                       "correct", List.of(2), "hint", "Großbuchstabe (H) ✓, Zahl (2024) ✓, Sonderzeichen (!) ✓, aber es ist kurz und \"Hund\" ist ein bekanntes Wort.")),

            createTask(m, "s3", "Caesar-Verschlüsselung knacken",
                "<p><strong>Story:</strong> Julius Caesar hat seine Nachrichten verschlüsselt, indem er jeden Buchstaben um 3 Stellen im Alphabet verschob. A→D, B→E, usw. Dein Programm soll eine Nachricht decodieren.</p><pre><code>def caesar_decode(text, versatz):\n    ergebnis = \"\"\n    for buchstabe in text:\n        if buchstabe._____():\n            neuer_code = _____(buchstabe) - versatz\n            ergebnis += _____(neuer_code)\n        else:\n            ergebnis += buchstabe\n    return ergebnis\n\nprint(caesar_decode(\"KHOOR\", 3))  # → HELLO</code></pre><p><strong>Aufgabe:</strong> Ergänze die Lücken im Python-Code (isalpha, ord, chr).</p>",
                TaskType.FILL_CODE, Difficulty.MEDIUM, 15, 3,
                Map.of("template", "def caesar_decode(text, versatz):\n    ergebnis = \"\"\n    for buchstabe in text:\n        if buchstabe._____():\n            neuer_code = _____(buchstabe) - versatz\n            ergebnis += _____(neuer_code)\n        else:\n            ergebnis += buchstabe\n    return ergebnis",
                       "blanks", List.of(Map.of("index", 0, "answer", "isalpha", "caseSensitive", true), Map.of("index", 1, "answer", "ord", "caseSensitive", true), Map.of("index", 2, "answer", "chr", "caseSensitive", true)),
                       "expectedOutput", "HELLO", "hint", "isalpha() prüft auf Buchstaben, ord() gibt den Zahlenwert eines Zeichens, chr() macht daraus wieder ein Zeichen.")),

            createTask(m, "s4", "SQL Injection verstehen",
                "<p><strong>Story:</strong> Eine schlecht programmierte Login-Seite baut diese SQL-Abfrage: <code>SELECT * FROM users WHERE username='[EINGABE]' AND password='[EINGABE]'</code>. Ein Hacker gibt als Nutzername ein: <code>admin'--</code></p><p><strong>Aufgabe:</strong> Was passiert bei dieser Eingabe?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 4,
                Map.of("options", List.of("Die Abfrage schlägt fehl wegen des Sonderzeichens", "Der Kommentar \"--\" deaktiviert die Passwort-Prüfung – der Hacker ist eingeloggt", "Die Datenbank wird gelöscht", "Nichts – moderne Datenbanken schützen sich automatisch"),
                       "correct", List.of(1), "hint", "In SQL leitet '--' einen Kommentar ein, der den Rest der Zeile ignoriert.")),

            createTask(m, "s5", "Passwort-Checker programmieren",
                "<p><strong>Story:</strong> Du baust das Sicherheits-Backend für eine neue App. Schreibe eine Funktion <code>passwort_check(pw)</code>, die prüft ob ein Passwort sicher ist.</p><p><strong>Aufgabe:</strong> Das Passwort ist sicher, wenn es mindestens 8 Zeichen hat UND mindestens eine Zahl enthält. Ausgabe: <code>\"Sicher\"</code> oder <code>\"Unsicher\"</code>.</p>",
                TaskType.CODE, Difficulty.MEDIUM, 20, 5,
                Map.of("starterCode", "def passwort_check(pw):\n    # Deine Logik hier\n    pass\n\nprint(passwort_check(\"abc\"))        # → Unsicher\nprint(passwort_check(\"abcdefgh\"))   # → Unsicher\nprint(passwort_check(\"abcdef12\"))   # → Sicher",
                       "expectedOutput", "Unsicher\nUnsicher\nSicher\n", "hint", "Nutze len(pw) >= 8 und any(c.isdigit() for c in pw).")),

            createTask(m, "s6", "2-Faktor-Authentifizierung erklären",
                "<p><strong>Story:</strong> Dein Chef versteht nicht, warum die App 2FA (Zwei-Faktor-Authentifizierung) braucht. Erkläre es ihm!</p><p><strong>Aufgabe:</strong> Fülle die Lücken im Erklärungstext (Faktoren, Passwort, Code, Handy).</p>",
                TaskType.FILL_BLANK, Difficulty.MEDIUM, 15, 6,
                Map.of("template", "Bei 2FA braucht man zwei verschiedene ___ zum Einloggen. Der erste Faktor ist meist das ___, der zweite ein einmaliger ___ auf dem Handy. Selbst wenn ein Hacker das Passwort kennt, kann er ohne das ___ nicht einloggen.",
                       "blanks", List.of(Map.of("index", 0, "answer", "Faktoren", "caseSensitive", false), Map.of("index", 1, "answer", "Passwort", "caseSensitive", false), Map.of("index", 2, "answer", "Code", "caseSensitive", false), Map.of("index", 3, "answer", "Handy", "caseSensitive", false)),
                       "hint", "Zwei-Faktor bedeutet 'Etwas das du weißt' (Passwort) und 'Etwas das du hast' (Handy).")),

            createTask(m, "s7", "Red Team vs. Blue Team",
                "<p><strong>Story:</strong> In echten Firmen gibt es ein Red Team (Angreifer) und ein Blue Team (Verteidiger). Du bist heute im Blue Team. Die Schulwebseite wurde gehackt (Passwort war \"admin123\", kein HTTPS).</p><p><strong>Aufgabe:</strong> Nenne mindestens 3 Sofortmaßnahmen und 3 langfristige Maßnahmen zur Verbesserung der Sicherheit.</p>",
                TaskType.PRACTICE, Difficulty.HARD, 50, 7,
                Map.of("instructions", "1. Sofortmaßnahmen (nächste 10 Min.), 2. Langfristige Maßnahmen, 3. Bonus: Was könnte das Red Team noch versucht haben?",
                       "submissionNote", "Beschreibe dein Vorgehen als Sicherheitsanalyst."))
        );
    }
}

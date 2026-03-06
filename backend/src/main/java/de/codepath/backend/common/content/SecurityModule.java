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
            "Schütze Daten und Systeme. Lerne Phishing-Erkennung, Verschlüsselung und Hacking-Abwehr.", "🔒", 6);
    }

    @Override
    public List<Task> getTasks(Module m) {
        return List.of(
            createTask(m, "s1", "Phishing-Alarm",
                "<p>Du bekommst folgende E-Mail. Wie viele Warnsignale findest du?</p><pre><code>Von: support@paypa1.com\nBetreff: ⚠️ DRINGEND: Ihr Konto wird in 24h gelöscht!!!\nInhalt: \"Sehr geehrter Kunde, klicken Sie JETZT auf den Link und bestätigen Sie Ihre Daten: http://paypal-sicher.xyz/login\"</code></pre>",
                "<p>Wie viele Warnsignale findest du in dieser E-Mail?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 1,
                Map.of("options", List.of("Keine, sieht echt aus", "1 Signal", "Mindestens 3 (Falsche URL, Zeitdruck, seltsame Links)", "Nur die Rechtschreibung"),
                       "correct", List.of(2), "hint", "Achte auf Absender, Dringlichkeit und die Ziel-URL.")),

            createTask(m, "s2", "Passwort-Check",
                "<p>Ein Passwort-Checker gibt dir Punkte: Länge +1, Großbuchstaben +1, Zahlen +1, Sonderzeichen +1, kein Wörterbuch-Wort +1.</p>",
                "<p>Wie viele Punkte bekommt das Passwort <code>Hund2024!</code>?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 2,
                Map.of("options", List.of("2 Punkte", "3 Punkte", "4 Punkte", "5 Punkte"),
                       "correct", List.of(2), "hint", "Länge ok, Großbuchstabe dabei, Zahl dabei, Sonderzeichen dabei. Aber: 'Hund' ist ein Wort.")),

            createTask(m, "s3", "Julius Caesars Geheimcode",
                "<p>Julius Caesar hat seine Nachrichten verschlüsselt, indem er jeden Buchstaben um 3 Stellen im Alphabet verschob. A→D, B→E, usw. Dein Programm soll eine Nachricht decodieren.</p><pre><code>def caesar_decode(text, versatz):\n    ergebnis = \"\"\n    for buchstabe in text:\n        if buchstabe._____():\n            neuer_code = _____(buchstabe) - versatz\n            ergebnis += _____(neuer_code)\n        else:\n            ergebnis += buchstabe\n    return ergebnis\n\nprint(caesar_decode(\"KHOOR\", 3))  # → HELLO</code></pre>",
                "<p>Ergänze die Lücken im Python-Code (isalpha, ord, chr).</p>",
                TaskType.FILL_CODE, Difficulty.MEDIUM, 15, 3,
                Map.of("template", "def caesar_decode(text, versatz):\n    ergebnis = \"\"\n    for buchstabe in text:\n        if buchstabe._____():\n            neuer_code = _____(buchstabe) - versatz\n            ergebnis += _____(neuer_code)\n        else:\n            ergebnis += buchstabe\n    return ergebnis\n\nprint(caesar_decode(\"KHOOR\", 3))",
                       "blanks", List.of(Map.of("index", 0, "answer", "isalpha", "caseSensitive", true), Map.of("index", 1, "answer", "ord", "caseSensitive", true), Map.of("index", 2, "answer", "chr", "caseSensitive", true)),
                       "expectedOutput", "HELLO\n", "hint", "ord() gibt den Zahlenwert eines Zeichens, chr() das Zeichen zu einer Zahl.")),

            createTask(m, "s4", "SQL-Injection verhindern",
                "<p>Eine schlecht programmierte Login-Seite baut diese SQL-Abfrage: <code>SELECT * FROM users WHERE username='[EINGABE]' AND password='[EINGABE]'</code>. Ein Hacker gibt als Nutzername ein: <code>admin'--</code></p>",
                "<p>Was passiert bei dieser Eingabe?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 20, 4,
                Map.of("options", List.of("Der Hacker wird gesperrt", "Die SQL-Abfrage wird manipuliert und der Hacker loggt sich ohne Passwort ein", "Die Datenbank stürzt ab"),
                       "correct", List.of(1), "hint", "Das '--' in SQL startet einen Kommentar und blendet den Rest der Abfrage aus.")),

            createTask(m, "s5", "Dein Sicherheits-Skript",
                "<p>Du baust das Sicherheits-Backend für eine neue App. Schreibe eine Funktion <code>passwort_check(pw)</code>, die prüft ob ein Passwort sicher ist.</p>",
                "<p>Das Passwort ist sicher, wenn es mindestens 8 Zeichen hat UND mindestens eine Zahl enthält. Ausgabe: <code>\"Sicher\"</code> oder <code>\"Unsicher\"</code>.</p>",
                TaskType.CODE, Difficulty.MEDIUM, 15, 5,
                Map.of("starterCode", "def passwort_check(pw):\n    # Deine Lösung hier\n    pass\n\n# Test\nprint(passwort_check(\"geheim123\"))\nprint(passwort_check(\"kurz1\"))\nprint(passwort_check(\"nurtextohnezahl\"))",
                       "expectedOutput", "Sicher\nUnsicher\nUnsicher\n", "hint", "Nutze len(pw) und any(char.isdigit() for char in pw).")),

            createTask(m, "s6", "Zwei-Faktor-Authentifizierung (2FA)",
                "<p>Dein Chef versteht nicht, warum die App 2FA (Zwei-Faktor-Authentifizierung) braucht. Erkläre es ihm!</p>",
                "<p>Fülle die Lücken im Erklärungstext (Faktoren, Passwort, Code, Handy).</p>",
                TaskType.FILL_BLANK, Difficulty.MEDIUM, 10, 6,
                Map.of("text", "2FA bedeutet, dass man zwei verschiedene ___ braucht. Neben dem ___ braucht man meist einen ___ von einer App auf dem ___.",
                       "blanks", List.of("Faktoren", "Passwort", "Code", "Handy"), "hint", "Etwas das du weißt + etwas das du hast.")),

            createTask(m, "s7", "Security-Berater",
                "<p>In echten Firmen gibt es ein Red Team (Angreifer) und ein Blue Team (Verteidiger). Du bist heute im Blue Team. Die Schulwebseite wurde gehackt (Passwort war \"admin123\", kein HTTPS).</p>",
                "<p>Nenne mindestens 3 Sofortmaßnahmen und 3 langfristige Maßnahmen zur Verbesserung der Sicherheit.</p>",
                TaskType.PRACTICE, Difficulty.HARD, 50, 7,
                Map.of("instructions", "Dein Bericht muss enthalten: 1. Was ist passiert?, 2. Sofortmaßnahmen (Quick Fixes), 3. Langfristige Strategie.",
                       "submissionNote", "Schreibe deinen Sicherheits-Bericht."))
        );
    }
}

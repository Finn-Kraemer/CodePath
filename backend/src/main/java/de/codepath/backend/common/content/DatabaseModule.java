package de.codepath.backend.common.content;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.tasks.Difficulty;
import de.codepath.backend.features.tasks.Task;
import de.codepath.backend.features.tasks.TaskType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class DatabaseModule extends BaseContentModule {

    @Override
    public Module getModuleDefinition() {
        return defineModule("datenbanken", "Datenbanken", 
            "Wie speichern Programme Daten dauerhaft? Lerne SQL-Grundlagen kennen.", "🗄️", 4);
    }

    @Override
    public List<Task> getTasks(Module m) {
        return List.of(
            createTask(m, "d1", "Chaos ohne Datenbank",
                "<p><strong>Story:</strong> Netflix speichert 200 Millionen Nutzerprofile. Stell dir vor, sie würden das in einer Excel-Tabelle machen. Was wäre das größte Problem?</p><p><strong>Aufgabe:</strong> Warum ist Excel für Millionen von Nutzern ungeeignet?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 1,
                Map.of("options", List.of("Excel ist zu teuer", "Gleichzeitige Änderungen von Millionen Nutzern gleichzeitig sind unmöglich – Excel skaliert nicht", "Excel kann keine Videos speichern", "Das Dateiformat ist nicht kompatibel"),
                       "correct", List.of(1), "hint", "Denk an die Skalierbarkeit und gleichzeitige Zugriffe (Concurrency).")),

            createTask(m, "d2", "SQL im Supermarkt",
                "<p><strong>Story:</strong> Du arbeitest im IT-Team eines Supermarkts. Ergänze die SQL-Abfragen!</p><p><strong>Aufgabe:</strong> Fülle die Lücken in den SQL-Befehlen (SELECT, FROM, WHERE, BY).</p>",
                TaskType.FILL_BLANK, Difficulty.EASY, 15, 2,
                Map.of("template", "1. Alle Produkte anzeigen: ___ * ___ produkte\n2. Nur Produkte unter 1€: SELECT * FROM produkte ___ preis < 1.00\n3. Produkte nach Preis sortiert: SELECT * FROM produkte ORDER ___ preis",
                       "blanks", List.of(Map.of("index", 0, "answer", "SELECT", "caseSensitive", false), Map.of("index", 1, "answer", "FROM", "caseSensitive", false), Map.of("index", 2, "answer", "WHERE", "caseSensitive", false), Map.of("index", 3, "answer", "BY", "caseSensitive", false)),
                       "hint", "SELECT wählt Spalten, FROM die Tabelle, WHERE filtert Zeilen, ORDER BY sortiert.")),

            createTask(m, "d3", "Was gibt diese Abfrage zurück?",
                "<p><strong>Story:</strong> Gegeben ist die Tabelle <code>bestellungen</code> (id, kunde, betrag, status). Status kann 'bezahlt' oder 'offen' sein.</p><pre><code>SELECT kunde, betrag FROM bestellungen\nWHERE status = 'offen'\nORDER BY betrag DESC;</code></pre><p><strong>Aufgabe:</strong> Was kommt zurück, wenn Ben (12.50€, offen) und Dan (5€, offen) in der Tabelle stehen?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 3,
                Map.of("options", List.of("Anna (25€) und Clara (89€)", "Ben (12.50€) und Dan (5€) – in dieser Reihenfolge", "Alle 4 Zeilen", "Nur Dan, weil er am wenigsten bezahlt hat"),
                       "correct", List.of(1), "hint", "DESC steht für 'descending' (absteigend), also vom größten zum kleinsten Wert.")),

            createTask(m, "d4", "JOIN verstehen",
                "<p><strong>Story:</strong> Deine App hat zwei Tabellen: <code>nutzer</code> (id, name) und <code>bestellungen</code> (id, nutzer_id, produkt).</p><p><strong>Aufgabe:</strong> Du willst eine Liste: \"Welcher Nutzer hat was bestellt?\" Welche SQL-Technik brauchst du?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 4,
                Map.of("options", List.of("Zwei separate SELECTs", "Eine WHERE-Bedingung", "Ein JOIN – er verknüpft beide Tabellen über nutzer_id", "Das ist ohne Programmierung nicht möglich"),
                       "correct", List.of(2), "hint", "JOINs werden verwendet, um Daten aus mehreren Tabellen basierend auf einer Beziehung zu kombinieren.")),

            createTask(m, "d5", "Datenbankschema debuggen",
                "<p><strong>Story:</strong> Ein Junior-Entwickler hat dieses Schema entworfen: <code>nachrichten (id, text, absender_name, empfaenger_name, datum)</code>.</p><p><strong>Aufgabe:</strong> Was ist das grundlegende Problem mit diesem Design?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.HARD, 10, 5,
                Map.of("options", List.of("Die Tabelle hat zu wenige Spalten", "Namen statt IDs – wenn jemand seinen Namen ändert, stimmen alte Daten nicht mehr", "datum gehört nicht in die Nachrichten-Tabelle", "Es braucht eine zweite Tabelle für Anhänge"),
                       "correct", List.of(1), "hint", "Denk an die Datenintegrität: Was passiert, wenn sich ein Name ändert?")),

            createTask(m, "d6", "Python trifft Datenbank",
                "<p><strong>Story:</strong> Du hast eine Liste von Schülern in Python und willst sie \"wie eine Datenbank\" durchsuchen.</p><pre><code>schueler = [\n    {\"name\": \"Anna\", \"punkte\": 150},\n    {\"name\": \"Ben\", \"punkte\": 80},\n    {\"name\": \"Clara\", \"punkte\": 200},\n]\n\n___ s ___ schueler:\n    if s[___] > 100:\n        print(s[\"name\"])</code></pre><p><strong>Aufgabe:</strong> Ergänze den Code, der alle Schüler mit mehr als 100 Punkten ausgibt.</p>",
                TaskType.FILL_CODE, Difficulty.HARD, 15, 6,
                Map.of("template", "___ s ___ schueler:\n    if s[___] > 100:\n        print(s[\"name\"])",
                       "blanks", List.of(Map.of("index", 0, "answer", "for", "caseSensitive", true), Map.of("index", 1, "answer", "in", "caseSensitive", true), Map.of("index", 2, "answer", "\"punkte\"", "caseSensitive", true)),
                       "expectedOutput", "Anna\nClara\n", "hint", "for s in schueler durchläuft die Liste, s[\"punkte\"] greift auf den Wert im Dictionary zu.")),

            createTask(m, "d7", "Entwirf die Datenbank für deine App",
                "<p><strong>Story:</strong> Du gründest ein Startup. Deine App ist eine To-Do-Liste mit Teams: Mehrere Nutzer können gemeinsame Aufgaben-Listen teilen.</p><p><strong>Aufgabe:</strong> Entwirf das Datenbankschema (Tabellen, Spalten, Verknüpfungen).</p>",
                TaskType.PRACTICE, Difficulty.HARD, 50, 7,
                Map.of("instructions", "Beschreibe: 1. Welche Tabellen braucht die App? 2. Welche Spalten hat jede Tabelle? 3. Wie sind sie verknüpft? (Fremdschlüssel) 4. Bonus: 2 SQL-Abfragen.",
                       "submissionNote", "Trage deinen Datenbankentwurf hier ein."))
        );
    }
}

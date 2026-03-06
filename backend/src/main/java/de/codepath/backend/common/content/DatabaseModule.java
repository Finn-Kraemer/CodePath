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
        return defineModule("datenbanken", "Datenbanken (SQL)", 
            "Strukturiere Daten wie ein Profi. Lerne SQL-Abfragen und Tabellen-Designs.", "🗄️", 3);
    }

    @Override
    public List<Task> getTasks(Module m) {
        return List.of(
            createTask(m, "db1", "Excel vs. Datenbank",
                "<p>Netflix speichert 200 Millionen Nutzerprofile. Stell dir vor, sie würden das in einer Excel-Tabelle machen. Was wäre das größte Problem?</p>",
                "<p>Warum ist Excel für Millionen von Nutzern ungeeignet?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 1,
                Map.of("options", List.of("Excel ist zu grün", "Zu langsam bei großen Datenmengen & kein gleichzeitiger Zugriff für Millionen User", "Excel kann keine Namen speichern"),
                       "correct", List.of(1), "hint", "Datenbanken sind auf Geschwindigkeit und Multi-User-Zugriff optimiert.")),

            createTask(m, "db2", "SQL Grundbefehle",
                "<p>Du arbeitest im IT-Team eines Supermarkts. Ergänze die SQL-Abfragen!</p>",
                "<p>Fülle die Lücken in den SQL-Befehlen (SELECT, FROM, WHERE, BY).</p>",
                TaskType.FILL_BLANK, Difficulty.EASY, 10, 2,
                Map.of("text", "___ name, preis ___ produkte ___ preis > 5.00 ORDER ___ preis DESC;",
                       "blanks", List.of("SELECT", "FROM", "WHERE", "BY"), "hint", "SELECT (was), FROM (wo), WHERE (Bedingung), ORDER BY (Sortierung).")),

            createTask(m, "db3", "Ergebnis-Check",
                "<p>Gegeben ist die Tabelle <code>bestellungen</code> (id, kunde, betrag, status). Status kann 'bezahlt' oder 'offen' sein.</p><pre><code>SELECT kunde, betrag FROM bestellungen\nWHERE status = 'offen'\nORDER BY betrag DESC;</code></pre>",
                "<p>Was kommt zurück, wenn Ben (12.50€, offen) und Dan (5€, offen) in der Tabelle stehen?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 15, 3,
                Map.of("options", List.of("Zuerst Dan, dann Ben", "Nur Ben", "Zuerst Ben, dann Dan", "Fehlermeldung"),
                       "correct", List.of(2), "hint", "DESC steht für absteigend (Descending).")),

            createTask(m, "db4", "Tabellen verknüpfen (Join)",
                "<p>Deine App hat zwei Tabellen: <code>nutzer</code> (id, name) und <code>bestellungen</code> (id, nutzer_id, produkt).</p>",
                "<p>Du willst eine Liste: \"Welcher Nutzer hat was bestellt?\" Welche SQL-Technik brauchst du?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 4,
                Map.of("options", List.of("SQL UPDATE", "SQL DELETE", "SQL JOIN", "SQL CREATE"),
                       "correct", List.of(2), "hint", "JOIN verbindet zwei Tabellen über eine gemeinsame Spalte (ID).")),

            createTask(m, "db5", "Daten-Redundanz",
                "<p>Ein Junior-Entwickler hat dieses Schema entworfen: <code>nachrichten (id, text, absender_name, empfaenger_name, datum)</code>.</p>",
                "<p>Was ist das grundlegende Problem mit diesem Design?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 5,
                Map.of("options", List.of("Die Tabelle ist zu klein", "Namen ändern sich; man sollte IDs statt Namen speichern (Normalisierung)", "Texte dürfen nicht in Datenbanken"),
                       "correct", List.of(1), "hint", "In Datenbanken vermeidet man doppelte Daten (Redundanz).")),

            createTask(m, "db6", "Python & Daten",
                "<p>Du hast eine Liste von Schülern in Python und willst sie \"wie eine Datenbank\" durchsuchen.</p><pre><code>schueler = [\n    {\"name\": \"Anna\", \"punkte\": 150},\n    {\"name\": \"Ben\", \"punkte\": 80},\n    {\"name\": \"Clara\", \"punkte\": 200},\n]\n\n___ s ___ schueler:\n    if s[___] > 100:\n        print(s[\"name\"])</code></pre>",
                "<p>Ergänze den Code, der alle Schüler mit mehr als 100 Punkten ausgibt.</p>",
                TaskType.FILL_CODE, Difficulty.MEDIUM, 15, 6,
                Map.of("template", "schueler = [\n    {\"name\": \"Anna\", \"punkte\": 150},\n    {\"name\": \"Ben\", \"punkte\": 80},\n    {\"name\": \"Clara\", \"punkte\": 200},\n]\n\n___ s ___ schueler:\n    if s[___] > 100:\n        print(s[\"name\"])",
                       "blanks", List.of(Map.of("index", 0, "answer", "for", "caseSensitive", true), Map.of("index", 1, "answer", "in", "caseSensitive", true), Map.of("index", 2, "answer", "\"punkte\"", "caseSensitive", true)),
                       "expectedOutput", "Anna\nClara\n", "hint", "Ein Dictionary-Zugriff erfolgt über den Key (hier: \"punkte\").")),

            createTask(m, "db7", "Schema-Designer",
                "<p>Du gründest ein Startup. Deine App ist eine To-Do-Liste mit Teams: Mehrere Nutzer können gemeinsame Aufgaben-Listen teilen.</p>",
                "<p>Entwirf das Datenbankschema (Tabellen, Spalten, Verknüpfungen).</p>",
                TaskType.PRACTICE, Difficulty.HARD, 50, 7,
                Map.of("instructions", "Dein Entwurf muss enthalten: 1. Tabellen-Namen, 2. Spalten-Namen (z.B. id, name), 3. Primärschlüssel, 4. Wie sind Nutzer und Aufgaben verknüpft?",
                       "submissionNote", "Beschreibe dein Schema."))
        );
    }
}

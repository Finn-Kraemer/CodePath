package de.codepath.backend.common.content;

import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.tasks.Difficulty;
import de.codepath.backend.features.tasks.Task;
import de.codepath.backend.features.tasks.TaskType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class WebDevModule extends BaseContentModule {

    @Override
    public Module getModuleDefinition() {
        return defineModule("web-entwicklung", "Web-Entwicklung", 
            "Baue deine eigenen Webseiten. Lerne HTML, CSS und wie das World Wide Web funktioniert.", "🌐", 5);
    }

    @Override
    public List<Task> getTasks(Module m) {
        return List.of(
            createTask(m, "w1", "Anatomie einer URL",
                "<p>Dein Chef schickt dir diese URL und fragt: \"Warum funktioniert das nicht?\" – Erkläre ihm was fehlt:</p><pre><code>www.meine-seite.de/shop/artikel</code></pre>",
                "<p>Was fehlt an dieser URL?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 1,
                Map.of("options", List.of("Das Protokoll (z.B. https://)", "Der Punkt am Ende", "Das www ist falsch"),
                       "correct", List.of(0), "hint", "Jede vollständige URL beginnt mit einem Protokoll.")),

            createTask(m, "w2", "Request & Response",
                "<p>In der Web-Entwicklung gibt es immer ein Hin und Her zwischen Browser und Server. Ordne zu: Was ist ein <strong>Request</strong>, was ist eine <strong>Response</strong>?</p>",
                "<p><em>\"Der Browser fragt: Gib mir die Startseite von YouTube.\"</em> – Was ist das?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 2,
                Map.of("options", List.of("Das ist eine Response", "Das ist ein Request"),
                       "correct", List.of(1), "hint", "Request = Anfrage (Browser -> Server), Response = Antwort (Server -> Browser).")),

            createTask(m, "w3", "HTML-Detektiv",
                "<p>Dein Kollege hat eine HTML-Seite gebaut. Was sieht der Nutzer im Browser, wenn er sie öffnet?</p><pre><code>&lt;h1&gt;Meine Seite&lt;/h1&gt;\n&lt;p&gt;Willkommen!&lt;/p&gt;\n&lt;!-- Das hier ist ein Kommentar --&gt;\n&lt;p&gt;Viel Spaß.&lt;/p&gt;</code></pre>",
                "<p>Was wird im Browser angezeigt?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 3,
                Map.of("options", List.of("Alles, inklusive des Kommentars", "Überschrift und zwei Textabsätze", "Nur die Überschrift"),
                       "correct", List.of(1), "hint", "Kommentare werden im Browser nicht angezeigt.")),

            createTask(m, "w4", "HTML-Lückentext",
                "<p>Deine Webagentur soll eine digitale Visitenkarte für einen Kunden bauen. Ergänze die fehlenden HTML-Tags:</p><pre><code>&lt;!DOCTYPE html&gt;\n&lt;___&gt;\n  &lt;head&gt;\n    &lt;title&gt;Max Mustermann&lt;/title&gt;\n  &lt;/head&gt;\n  &lt;___&gt;\n    &lt;___&gt;Max Mustermann&lt;/___&gt;\n    &lt;p&gt;Web-Entwickler&lt;/p&gt;\n    &lt;___ href=\"mailto:max@mail.de\"&gt;Schreib mir!&lt;/___&gt;\n  &lt;/body&gt;\n&lt;/html&gt;</code></pre>",
                "<p>Fülle die Lücken mit den korrekten HTML-Tags (html, body, h1, a).</p>",
                TaskType.FILL_CODE, Difficulty.MEDIUM, 15, 4,
                Map.of("template", "<!DOCTYPE html>\n<___>\n  <head>\n    <title>Max Mustermann</title>\n  </head>\n  <___>\n    <___>Max Mustermann</___>\n    <p>Web-Entwickler</p>\n    <___ href=\"mailto:max@mail.de\">Schreib mir!</___>\n  </body>\n</html>",
                       "blanks", List.of(Map.of("index", 0, "answer", "html", "caseSensitive", true), Map.of("index", 1, "answer", "body", "caseSensitive", true), Map.of("index", 2, "answer", "h1", "caseSensitive", true), Map.of("index", 3, "answer", "h1", "caseSensitive", true), Map.of("index", 4, "answer", "a", "caseSensitive", true), Map.of("index", 5, "answer", "a", "caseSensitive", true)),
                       "expectedOutput", "", "hint", "Jedes HTML-Dokument braucht ein html- und ein body-Tag.")),

            createTask(m, "w5", "Daten-Format: JSON",
                "<p>Du rufst eine Wetter-API auf: <code>GET https://api.wetter.de/aktuell?stadt=Berlin</code>. Die API antwortet mit:</p><pre><code>{\n  \"stadt\": \"Berlin\",\n  \"temperatur\": 18,\n  \"wetter\": \"bewölkt\"\n}</code></pre>",
                "<p>Dein Kollege will die Temperatur aus dieser Antwort lesen. Welcher Code ist richtig? (Annahme: <code>data</code> enthält das JSON-Objekt)</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 5,
                Map.of("options", List.of("data.getTemp()", "data[\"temperatur\"]", "data.city.temperature"),
                       "correct", List.of(1), "hint", "In JavaScript/Python greift man meist über den Key auf JSON-Daten zu.")),

            createTask(m, "w6", "Web-Statuscodes",
                "<p>Du erklärst deinem jüngeren Geschwisterkind, was Statuscodes bedeuten. Ergänze:</p>",
                "<p>Fülle die Lücken mit den richtigen HTTP-Statuscodes (200, 301, 404, 500).</p>",
                TaskType.FILL_BLANK, Difficulty.MEDIUM, 10, 6,
                Map.of("text", "Alles OK: ___. Seite nicht gefunden: ___. Server-Fehler: ___. Permanente Umleitung: ___.",
                       "blanks", List.of("200", "404", "500", "301"), "hint", "404 ist der wohl bekannteste Fehler im Web.")),

            createTask(m, "w7", "Dein Web-Entwurf",
                "<p>Der Bürgermeister deiner Stadt möchte eine moderne Webseite. Er hat nur ein Budget für 1 Seite und kommt morgen zum Pitch.</p>",
                "<p>Entwirf die Startseite in HTML – entweder als echten Code oder als detaillierte Beschreibung.</p>",
                TaskType.PRACTICE, Difficulty.HARD, 50, 7,
                Map.of("instructions", "Dein Entwurf muss enthalten: 1. Eine Überschrift, 2. Ein Menü (Nav), 3. Einen Info-Text über die Stadt, 4. Ein Kontaktformular-Entwurf.",
                       "submissionNote", "Schreibe deinen HTML-Code oder die Struktur auf."))
        );
    }
}

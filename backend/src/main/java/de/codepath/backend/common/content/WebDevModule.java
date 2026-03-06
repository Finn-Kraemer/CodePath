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
            "Wie funktioniert das Internet? Lerne HTML, HTTP und das Client-Server-Modell kennen.", "🌐", 2);
    }

    @Override
    public List<Task> getTasks(Module m) {
        return List.of(
            createTask(m, "w1", "Die kaputte URL",
                "<p><strong>Story:</strong> Dein Chef schickt dir diese URL und fragt: \"Warum funktioniert das nicht?\" – Erkläre ihm was fehlt:</p><pre><code>www.meine-seite.de/shop/artikel</code></pre><p><strong>Aufgabe:</strong> Was fehlt an dieser URL?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 1,
                Map.of("options", List.of("Die URL ist zu lang", "Das Protokoll fehlt – es muss \"https://\" am Anfang stehen", "Bindestriche sind in URLs nicht erlaubt", "URLs dürfen keine Unterordner haben"),
                       "correct", List.of(1), "hint", "Eine vollständige URL beginnt immer mit einem Protokoll wie http:// oder https://.")),

            createTask(m, "w2", "Request oder Response?",
                "<p><strong>Story:</strong> In der Web-Entwicklung gibt es immer ein Hin und Her zwischen Browser und Server. Ordne zu: Was ist ein <strong>Request</strong>, was ist eine <strong>Response</strong>?</p><p><strong>Aufgabe:</strong> <em>\"Der Browser fragt: Gib mir die Startseite von YouTube.\"</em> – Was ist das?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 2,
                Map.of("options", List.of("Das ist ein Request – der Browser stellt eine Anfrage", "Das ist eine Response – YouTube antwortet", "Das ist beides gleichzeitig", "Weder noch – Webseiten werden lokal gespeichert"),
                       "correct", List.of(0), "hint", "Wer fragt (Client) und wer antwortet (Server)?")),

            createTask(m, "w3", "HTML-Detektiv: Was wird angezeigt?",
                "<p><strong>Story:</strong> Dein Kollege hat eine HTML-Seite gebaut. Was sieht der Nutzer im Browser, wenn er sie öffnet?</p><pre><code>&lt;h1&gt;Meine Seite&lt;/h1&gt;\n&lt;p&gt;Willkommen!&lt;/p&gt;\n&lt;!-- Das hier ist ein Kommentar --&gt;\n&lt;p&gt;Viel Spaß.&lt;/p&gt;</code></pre><p><strong>Aufgabe:</strong> Was wird im Browser angezeigt?</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.EASY, 10, 3,
                Map.of("options", List.of("Alles inklusive &lt;!-- Das hier ist ein Kommentar --&gt;", "Eine große Überschrift \"Meine Seite\", dann \"Willkommen!\" und \"Viel Spaß.\" – der Kommentar ist unsichtbar", "Nur die Überschrift", "Den rohen HTML-Code"),
                       "correct", List.of(1), "hint", "Kommentare in HTML dienen nur dem Entwickler und werden vom Browser ignoriert.")),

            createTask(m, "w4", "Baue eine Visitenkarte",
                "<p><strong>Story:</strong> Deine Webagentur soll eine digitale Visitenkarte für einen Kunden bauen. Ergänze die fehlenden HTML-Tags:</p><pre><code>&lt;!DOCTYPE html&gt;\n&lt;___&gt;\n  &lt;head&gt;\n    &lt;title&gt;Max Mustermann&lt;/title&gt;\n  &lt;/head&gt;\n  &lt;___&gt;\n    &lt;___&gt;Max Mustermann&lt;/___&gt;\n    &lt;p&gt;Web-Entwickler&lt;/p&gt;\n    &lt;___ href=\"mailto:max@mail.de\"&gt;Schreib mir!&lt;/___&gt;\n  &lt;/body&gt;\n&lt;/html&gt;</code></pre><p><strong>Aufgabe:</strong> Fülle die Lücken mit den korrekten HTML-Tags (html, body, h1, a).</p>",
                TaskType.FILL_BLANK, Difficulty.MEDIUM, 15, 4,
                Map.of("template", "<!DOCTYPE html>\n<___>\n  <head>\n    <title>Max Mustermann</title>\n  </head>\n  <___>\n    <___>Max Mustermann</___>\n    <p>Web-Entwickler</p>\n    <___ href=\"mailto:max@mail.de\">Schreib mir!</___>\n  </body>\n</html>",
                       "blanks", List.of(Map.of("index", 0, "answer", "html", "caseSensitive", false), Map.of("index", 1, "answer", "body", "caseSensitive", false), Map.of("index", 2, "answer", "h1", "caseSensitive", false), Map.of("index", 3, "answer", "h1", "caseSensitive", false), Map.of("index", 4, "answer", "a", "caseSensitive", false), Map.of("index", 5, "answer", "a", "caseSensitive", false)),
                       "hint", "html umschließt alles, body den Inhalt, h1 ist eine Überschrift, a ist ein Link.")),

            createTask(m, "w5", "API-Detektiv: Was kommt zurück?",
                "<p><strong>Story:</strong> Du rufst eine Wetter-API auf: <code>GET https://api.wetter.de/aktuell?stadt=Berlin</code>. Die API antwortet mit:</p><pre><code>{\n  \"stadt\": \"Berlin\",\n  \"temperatur\": 18,\n  \"wetter\": \"bewölkt\"\n}</code></pre><p><strong>Aufgabe:</strong> Dein Kollege will die Temperatur aus dieser Antwort lesen. Welcher Code ist richtig? (Annahme: <code>data</code> enthält das JSON-Objekt)</p>",
                TaskType.MULTIPLE_CHOICE, Difficulty.MEDIUM, 10, 5,
                Map.of("options", List.of("data.temperatur()", "data[temperatur]", "data[\"temperatur\"]", "data->temperatur"),
                       "correct", List.of(2), "hint", "In Python greift man auf Dictionary-Werte mit eckigen Klammern und dem Key als String zu.")),

            createTask(m, "w6", "Fehler 404 erklärt",
                "<p><strong>Story:</strong> Du erklärst deinem jüngeren Geschwisterkind, was Statuscodes bedeuten. Ergänze:</p><p><strong>Aufgabe:</strong> Fülle die Lücken mit den richtigen HTTP-Statuscodes (200, 301, 404, 500).</p>",
                TaskType.FILL_BLANK, Difficulty.MEDIUM, 15, 6,
                Map.of("template", "- Der Server hat die Seite gefunden und alles ist OK → Code ___\n- Die Seite wurde dauerhaft woanders hingezogen → Code ___\n- Die Seite wurde nicht gefunden → Code ___\n- Der Server hat einen internen Fehler → Code ___",
                       "blanks", List.of(Map.of("index", 0, "answer", "200", "caseSensitive", false), Map.of("index", 1, "answer", "301", "caseSensitive", false), Map.of("index", 2, "answer", "404", "caseSensitive", false), Map.of("index", 3, "answer", "500", "caseSensitive", false)),
                       "hint", "2xx = Erfolg, 3xx = Umleitung, 4xx = Client-Fehler, 5xx = Server-Fehler.")),

            createTask(m, "w7", "Die Webseite für den Bürgermeister",
                "<p><strong>Story:</strong> Der Bürgermeister deiner Stadt möchte eine moderne Webseite. Er hat nur ein Budget für 1 Seite und kommt morgen zum Pitch.</p><p><strong>Aufgabe:</strong> Entwirf die Startseite in HTML – entweder als echten Code oder als detaillierte Beschreibung.</p>",
                TaskType.PRACTICE, Difficulty.HARD, 50, 7,
                Map.of("instructions", "Die Seite muss enthalten: Eine Überschrift (Stadtname), eine Begrüßung, 3 Bereiche (z.B. News, Kontakt, Zeiten), mind. 1 Link und 1 Liste.",
                       "submissionNote", "Trage deinen HTML-Entwurf oder eine detaillierte Struktur ein."))
        );
    }
}

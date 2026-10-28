package de.codepath.backend.engine.lab;

import java.util.List;
import java.util.Map;

/**
 * Eine Übungsseite des Browser-Labors. Seiten und Antworten kommen ausschließlich aus diesen
 * Vorlagen – Lehrkräfte wählen ein Szenario, schreiben aber kein HTML (kein Schadcode über den Editor).
 */
public interface LabScenario {

    String id();

    String title();

    /** Werkzeuge, die man braucht – wird in der Aufgabe angezeigt. */
    List<String> tools();

    /** Anzahl der Code-Teile. */
    int parts();

    /** Antwort auf eine Anfrage an {@code <basis>/<pfad>}; der leere Pfad ist die Startseite. */
    LabResponse handle(LabRequest request);

    record LabRequest(String basePath, String method, String path, Map<String, String> query,
                      Map<String, String> headers, String body, LabCodes codes) {

        public String header(String name) {
            return headers.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), "");
        }
    }

    record LabResponse(int status, String contentType, String body, Map<String, String> headers) {

        public static LabResponse html(String body) {
            return new LabResponse(200, "text/html;charset=UTF-8", body, Map.of());
        }

        public static LabResponse script(String body) {
            return new LabResponse(200, "text/javascript;charset=UTF-8", body, Map.of());
        }

        public static LabResponse json(int status, String body) {
            return new LabResponse(status, "application/json;charset=UTF-8", body, Map.of());
        }

        public static LabResponse json(int status, String body, Map<String, String> headers) {
            return new LabResponse(status, "application/json;charset=UTF-8", body, headers);
        }

        public static LabResponse notFound() {
            return json(404, "{\"fehler\":\"Nicht gefunden\"}");
        }
    }
}

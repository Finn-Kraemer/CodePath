package de.codepath.backend.engine.lab;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/** Gemeinsamer Rahmen der Übungsseiten und kleine Helfer. */
final class LabPage {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private LabPage() {
    }

    static String json(Map<String, ?> value) {
        return JSON.writeValueAsString(value);
    }

    static Map<?, ?> parse(String body) {
        try {
            return body == null || body.isBlank() ? Map.of() : JSON.readValue(body, Map.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Seite mit Labor-Hinweis oben; {{BASIS}} im Inhalt wird durch den Pfad des Labors ersetzt. */
    static String page(String title, String basePath, String head, String body) {
        return """
                <!doctype html>
                <html lang="de">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>{{TITLE}} – Browser-Labor</title>
                <style>
                  body { margin: 0; font-family: system-ui, sans-serif; background: #f1f5f9; color: #0f172a; }
                  .labor { background: #0f172a; color: #e2e8f0; padding: 8px 16px; font-size: 13px; }
                  .labor b { color: #fbbf24; }
                  main { max-width: 900px; margin: 24px auto; padding: 0 16px; }
                  .karte { background: white; border: 1px solid #cbd5e1; padding: 16px; margin-bottom: 16px; }
                  button { font: inherit; padding: 8px 14px; border: 2px solid #0f172a; background: #0f172a; color: white; cursor: pointer; }
                  button:disabled { background: #94a3b8; border-color: #94a3b8; cursor: not-allowed; }
                  .fehler { background: #fee2e2; border: 1px solid #fca5a5; color: #7f1d1d; padding: 12px; }
                </style>
                {{HEAD}}
                </head>
                <body>
                <div class="labor">CodePath <b>Browser-Labor</b> · Öffne die Entwicklerwerkzeuge mit <b>F12</b>
                  (Mac: <b>⌥ ⌘ I</b>). Gesucht sind die Teile deines Codes – trage sie in CodePath ein.</div>
                <main>
                {{BODY}}
                </main>
                </body>
                </html>
                """
                .replace("{{TITLE}}", title)
                .replace("{{HEAD}}", head)
                .replace("{{BODY}}", body)
                .replace("{{BASIS}}", basePath);
    }
}

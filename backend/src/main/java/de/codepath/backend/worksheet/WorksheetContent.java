package de.codepath.backend.worksheet;

import de.codepath.backend.engine.MediaKeys;
import de.codepath.backend.platform.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Inhalt eines Aufgabenblatts: {@code {"settings": {...}, "blocks": [...]}}. Geprüft wird die Form
 * (bekannte Blocktypen, Grenzen, Farben, Bildschlüssel); fehlende Felder ergänzt erst der Renderer
 * mit Standardwerten. Blocktypen und Felder: {@code frontend/src/lib/worksheet/types.ts}.
 */
@Component
@RequiredArgsConstructor
public class WorksheetContent {

    /** Blöcke, die als nummerierte Aufgabe zählen (Punkte, „Aufgabe 1“). */
    static final Set<String> TASK_TYPES = Set.of("task", "choice", "gap", "truefalse", "matching", "order");
    static final Set<String> BLOCK_TYPES = Set.of("heading", "text", "box", "task", "choice", "gap", "truefalse",
            "matching", "order", "table", "code", "image", "space", "divider", "pagebreak");
    static final int MAX_BLOCKS = 300;
    static final int MAX_CHARS = 500_000;
    static final int MAX_DEPTH = 6;
    static final int MAX_TITLE = 200;
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    /** Kennzahlen für die Übersicht. */
    public record Stats(String kind, String subject, int blocks, int tasks, double points) {
    }

    private final ObjectMapper objectMapper;

    /** Prüft den Inhalt und gibt ihn als JSON-Text zum Speichern zurück. */
    public String validate(JsonNode content) {
        if (content == null || !content.isObject()) throw ApiException.badRequest("Ungültiger Inhalt des Aufgabenblatts.");
        JsonNode settings = content.get("settings");
        if (settings != null && !settings.isNull()) {
            if (!settings.isObject()) throw ApiException.badRequest("Die Einstellungen müssen ein Objekt sein.");
            String accent = settings.path("accent").asString("");
            if (!accent.isEmpty() && !COLOR.matcher(accent).matches()) {
                throw ApiException.badRequest("Die Akzentfarbe muss die Form #rrggbb haben.");
            }
            checkMedia(settings.path("logo").asString(""));
        }
        JsonNode blocks = content.get("blocks");
        if (blocks == null || !blocks.isArray()) throw ApiException.badRequest("Das Aufgabenblatt braucht eine Liste von Blöcken.");
        if (blocks.size() > MAX_BLOCKS) throw ApiException.badRequest("Höchstens " + MAX_BLOCKS + " Blöcke je Aufgabenblatt.");
        for (JsonNode block : blocks) {
            if (!block.isObject()) throw ApiException.badRequest("Jeder Block muss ein Objekt sein.");
            JsonNode type = block.get("type");
            if (type == null || !type.isString() || !BLOCK_TYPES.contains(type.asString())) {
                throw ApiException.badRequest("Unbekannter Blocktyp: " + (type == null ? "–" : type.asString()));
            }
            if (type.asString().equals("image")) checkMedia(block.path("key").asString(""));
            checkDepth(block, 0);
        }
        String value = objectMapper.writeValueAsString(content);
        if (value.length() > MAX_CHARS) throw ApiException.badRequest("Das Aufgabenblatt ist zu umfangreich.");
        return value;
    }

    /** Titel bereinigen und prüfen. */
    public static String title(String title) {
        String value = title == null ? "" : title.strip();
        if (value.isEmpty()) throw ApiException.badRequest("Bitte einen Titel angeben.");
        if (value.length() > MAX_TITLE) throw ApiException.badRequest("Der Titel ist zu lang (höchstens " + MAX_TITLE + " Zeichen).");
        return value;
    }

    /** Inhalt eines neuen Blatts: Kopf mit Namensfeld, eine Einleitung und eine Beispielaufgabe. */
    public String starter() {
        Map<String, Object> settings = Map.of("kind", "Arbeitsblatt");
        List<Object> blocks = List.of(
                Map.of("id", "b1", "type", "text", "text", "Lies dir die Aufgaben genau durch. Schreibe sauber und vollständig."),
                Map.of("id", "b2", "type", "task", "prompt", "Erkläre in eigenen Worten, was ein Algorithmus ist.",
                        "points", 2, "answer", "lines", "size", 4, "solution",
                        "Eine eindeutige Schritt-für-Schritt-Anleitung, mit der man ein Problem löst."));
        return validate(objectMapper.valueToTree(Map.of("settings", settings, "blocks", blocks)));
    }

    /** Gespeicherten JSON-Text lesen; Unlesbares wird zu einem leeren Blatt. */
    public JsonNode read(String value) {
        try {
            JsonNode node = value == null ? null : objectMapper.readTree(value);
            if (node != null && node.isObject()) return node;
        } catch (JacksonException e) {
            // fällt auf ein leeres Blatt zurück
        }
        return objectMapper.valueToTree(Map.of("settings", Map.of(), "blocks", List.of()));
    }

    public Stats stats(JsonNode content) {
        JsonNode settings = content.path("settings");
        int tasks = 0;
        for (JsonNode block : content.path("blocks")) {
            if (isTask(block)) tasks++;
        }
        return new Stats(WorksheetRenderer.text(settings, "kind"), WorksheetRenderer.text(settings, "subject"),
                content.path("blocks").size(), tasks, WorksheetRenderer.totalPoints(content.path("blocks")));
    }

    /** Nummerierte Aufgabe mit Punkten? Tabellen zählen nur mit Arbeitsauftrag. */
    static boolean isTask(JsonNode block) {
        String type = WorksheetRenderer.text(block, "type");
        return TASK_TYPES.contains(type) || ("table".equals(type) && !WorksheetRenderer.text(block, "prompt").isBlank());
    }

    private static void checkMedia(String key) {
        if (!key.isEmpty() && !MediaKeys.isKey(key)) throw ApiException.badRequest("Ungültiger Bildschlüssel: " + key);
    }

    private static void checkDepth(JsonNode node, int depth) {
        if (depth > MAX_DEPTH) throw ApiException.badRequest("Der Inhalt ist zu tief verschachtelt.");
        for (JsonNode child : node) checkDepth(child, depth + 1);
    }
}

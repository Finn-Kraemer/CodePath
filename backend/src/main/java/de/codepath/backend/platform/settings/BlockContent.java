package de.codepath.backend.platform.settings;

import de.codepath.backend.platform.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seiteninhalt aus Blöcken (Startseite und eigene Seiten). Geprüft wird nur die Form; Texte werden im
 * Browser escaped bzw. als bereinigtes Markdown gezeigt.
 */
@Component
@RequiredArgsConstructor
public class BlockContent {

    static final Set<String> BLOCK_TYPES = Set.of("hero", "text", "list", "cards", "split", "timeline", "grades", "image", "cta");
    static final int MAX_BLOCKS = 60;
    static final int MAX_CHARS = 200_000;
    static final int MAX_DEPTH = 6;

    private final ObjectMapper objectMapper;

    /** Prüft den Inhalt und gibt ihn als JSON-Text zum Speichern zurück. */
    public String validate(JsonNode content) {
        if (content == null || !content.isObject()) throw ApiException.badRequest("Ungültiger Seiteninhalt.");
        JsonNode blocks = content.get("blocks");
        if (blocks == null || !blocks.isArray()) throw ApiException.badRequest("Die Seite braucht eine Liste von Blöcken.");
        if (blocks.size() > MAX_BLOCKS) throw ApiException.badRequest("Höchstens " + MAX_BLOCKS + " Blöcke.");
        for (JsonNode block : blocks) {
            if (!block.isObject()) throw ApiException.badRequest("Jeder Block muss ein Objekt sein.");
            JsonNode type = block.get("type");
            if (type == null || !type.isString() || !BLOCK_TYPES.contains(type.asString())) {
                throw ApiException.badRequest("Unbekannter Blocktyp: " + (type == null ? "–" : type.asString()));
            }
            checkDepth(block, 0);
        }
        String value = objectMapper.writeValueAsString(content);
        if (value.length() > MAX_CHARS) throw ApiException.badRequest("Die Seite ist zu umfangreich.");
        return value;
    }

    /** Inhalt einer neuen Seite: ein Textabschnitt mit dem Titel. */
    public String starter(String title) {
        Map<String, Object> block = Map.of("id", "intro", "type", "text", "theme", "light", "badge", "",
                "title", title, "body", "", "align", "left");
        return validate(objectMapper.valueToTree(Map.of("blocks", List.of(block))));
    }

    /** Gespeicherten JSON-Text lesen; Unlesbares gilt als nicht vorhanden. */
    public JsonNode read(String value) {
        if (value == null) return null;
        try {
            return objectMapper.readTree(value);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static void checkDepth(JsonNode node, int depth) {
        if (depth > MAX_DEPTH) throw ApiException.badRequest("Der Inhalt ist zu tief verschachtelt.");
        for (JsonNode child : node) checkDepth(child, depth + 1);
    }
}

package de.codepath.backend.platform.settings;

import de.codepath.backend.engine.MediaKeys;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

/**
 * Inhalt der Startseite als Liste von Blöcken (JSON), bearbeitet von der Lehrkraft. Es gibt einen
 * Entwurf und eine veröffentlichte Fassung; ohne veröffentlichte Fassung zeigt das Frontend seine
 * eingebaute Standardseite. Texte werden im Browser escaped bzw. als bereinigtes Markdown gezeigt,
 * hier wird nur die Form geprüft.
 */
@Service
@RequiredArgsConstructor
public class HomepageService {

    static final String PUBLISHED = "homepage_published";
    static final String DRAFT = "homepage_draft";

    static final Set<String> BLOCK_TYPES = Set.of("hero", "text", "list", "cards", "split", "timeline", "grades", "image", "cta");
    static final int MAX_BLOCKS = 60;
    static final int MAX_CHARS = 200_000;
    static final int MAX_DEPTH = 6;

    public record HomepageState(JsonNode published, JsonNode draft) {
    }

    private final SystemSettingRepository repository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public JsonNode published() {
        return read(PUBLISHED);
    }

    @Transactional(readOnly = true)
    public HomepageState state() {
        return new HomepageState(read(PUBLISHED), read(DRAFT));
    }

    @Transactional
    public void saveDraft(JsonNode content) {
        repository.save(new SystemSetting(DRAFT, validate(content)));
    }

    /** Veröffentlicht den übergebenen Inhalt; der Entwurf entspricht danach der Live-Fassung. */
    @Transactional
    public void publish(JsonNode content) {
        String value = validate(content);
        repository.save(new SystemSetting(PUBLISHED, value));
        repository.save(new SystemSetting(DRAFT, value));
        auditService.log("HOMEPAGE_PUBLISHED", "homepage", Map.of("blocks", content.get("blocks").size()));
    }

    /** Zurück zur eingebauten Standardseite. */
    @Transactional
    public void reset() {
        repository.deleteById(PUBLISHED);
        repository.deleteById(DRAFT);
        auditService.log("HOMEPAGE_RESET", "homepage", Map.of());
    }

    /** Bilder der veröffentlichten Startseite dürfen auch Besucher ohne Anmeldung sehen. */
    @Transactional(readOnly = true)
    public boolean isPublicMedia(String key) {
        return repository.findById(PUBLISHED).map(s -> MediaKeys.find(s.getValue()).contains(key)).orElse(false);
    }

    /** Wird ein Bild in Entwurf oder Live-Fassung verwendet? Dann darf es nicht gelöscht werden. */
    @Transactional(readOnly = true)
    public boolean usesMedia(String key) {
        return repository.findAllById(Set.of(PUBLISHED, DRAFT)).stream()
                .anyMatch(s -> MediaKeys.find(s.getValue()).contains(key));
    }

    private JsonNode read(String key) {
        return repository.findById(key).map(s -> {
            try {
                return objectMapper.readTree(s.getValue());
            } catch (JacksonException e) {
                return null;
            }
        }).orElse(null);
    }

    String validate(JsonNode content) {
        if (content == null || !content.isObject()) throw ApiException.badRequest("Ungültiger Inhalt der Startseite.");
        JsonNode blocks = content.get("blocks");
        if (blocks == null || !blocks.isArray()) throw ApiException.badRequest("Die Startseite braucht eine Liste von Blöcken.");
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
        if (value.length() > MAX_CHARS) throw ApiException.badRequest("Die Startseite ist zu umfangreich.");
        return value;
    }

    private static void checkDepth(JsonNode node, int depth) {
        if (depth > MAX_DEPTH) throw ApiException.badRequest("Der Inhalt ist zu tief verschachtelt.");
        for (JsonNode child : node) checkDepth(child, depth + 1);
    }
}

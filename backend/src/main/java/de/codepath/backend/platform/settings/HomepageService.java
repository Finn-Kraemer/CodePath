package de.codepath.backend.platform.settings;

import de.codepath.backend.engine.MediaKeys;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.platform.media.MediaUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Inhalt der Startseite als Liste von Blöcken (JSON), bearbeitet von der Lehrkraft. Es gibt einen
 * Entwurf und eine veröffentlichte Fassung; ohne veröffentlichte Fassung zeigt das Frontend seine
 * eingebaute Standardseite.
 */
@Service
@RequiredArgsConstructor
public class HomepageService implements MediaUsage {

    static final String PUBLISHED = "homepage_published";
    static final String DRAFT = "homepage_draft";

    public record HomepageState(JsonNode published, JsonNode draft) {
    }

    private final SystemSettingRepository repository;
    private final BlockContent blockContent;
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
        repository.save(new SystemSetting(DRAFT, blockContent.validate(content)));
    }

    /** Veröffentlicht den übergebenen Inhalt; der Entwurf entspricht danach der Live-Fassung. */
    @Transactional
    public void publish(JsonNode content) {
        String value = blockContent.validate(content);
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
    @Override
    @Transactional(readOnly = true)
    public boolean isPublic(String key) {
        return repository.findById(PUBLISHED).map(s -> MediaKeys.find(s.getValue()).contains(key)).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> usedBy(String key) {
        boolean used = repository.findAllById(Set.of(PUBLISHED, DRAFT)).stream()
                .anyMatch(s -> MediaKeys.find(s.getValue()).contains(key));
        return used ? Optional.of("auf der Startseite") : Optional.empty();
    }

    private JsonNode read(String key) {
        return repository.findById(key).map(s -> blockContent.read(s.getValue())).orElse(null);
    }
}

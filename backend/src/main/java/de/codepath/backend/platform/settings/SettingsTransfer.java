package de.codepath.backend.platform.settings;

import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.transfer.ItemRunner;
import de.codepath.backend.platform.transfer.TransferItem;
import de.codepath.backend.platform.transfer.TransferSection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** Schalter der Installation im zentralen Export. Geheimnisse (z. B. für das Browser-Labor) bleiben draußen. */
@Component
@RequiredArgsConstructor
public class SettingsTransfer implements TransferSection {

    static final Map<String, String> EXPORTED = new LinkedHashMap<>();

    static {
        EXPORTED.put(SettingsService.FEEDBACK_ENABLED, "Feedback-Runde");
        EXPORTED.put(SettingsService.CERTIFICATES_ENABLED, "Zeugnisse freigegeben");
    }

    private final SystemSettingRepository repository;
    private final ItemRunner runner;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return "settings";
    }

    @Override
    public String label() {
        return "Einstellungen";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public JsonNode export(Set<String> only) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        for (String key : EXPORTED.keySet()) {
            if (!only.isEmpty() && !only.contains(key)) continue;
            values.put(key, repository.findById(key).map(s -> "true".equalsIgnoreCase(s.getValue())).orElse(false));
        }
        return objectMapper.valueToTree(values);
    }

    @Override
    public List<TransferItem> importData(JsonNode data, Long actorId) {
        if (!data.isObject()) return List.of(TransferItem.error(label(), "-", "Erwartet wird ein Objekt mit Schaltern."));
        List<TransferItem> items = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : data.properties()) {
            String name = EXPORTED.getOrDefault(entry.getKey(), entry.getKey());
            items.add(runner.run(label(), name, () -> {
                if (!EXPORTED.containsKey(entry.getKey())) throw ApiException.badRequest("Unbekannte Einstellung – übersprungen.");
                if (!entry.getValue().isBoolean()) throw ApiException.badRequest("Erwartet wird true oder false.");
                String value = String.valueOf(entry.getValue().asBoolean());
                Optional<SystemSetting> existing = repository.findById(entry.getKey());
                if (existing.isPresent() && existing.get().getValue().equalsIgnoreCase(value)) return TransferItem.Action.UNCHANGED;
                repository.save(new SystemSetting(entry.getKey(), value));
                return existing.isPresent() ? TransferItem.Action.UPDATED : TransferItem.Action.CREATED;
            }));
        }
        return items;
    }
}

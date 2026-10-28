package de.codepath.backend.platform.settings;

import de.codepath.backend.platform.transfer.ItemRunner;
import de.codepath.backend.platform.transfer.TransferItem;
import de.codepath.backend.platform.transfer.TransferSection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Startseite (Entwurf und veröffentlichte Fassung) im zentralen Export. */
@Component
@RequiredArgsConstructor
public class HomepageTransfer implements TransferSection {

    private final SystemSettingRepository repository;
    private final BlockContent blockContent;
    private final ItemRunner runner;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return "homepage";
    }

    @Override
    public String label() {
        return "Startseite";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public JsonNode export(Set<String> only) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("draft", read(HomepageService.DRAFT));
        node.set("published", read(HomepageService.PUBLISHED));
        return node;
    }

    @Override
    public List<TransferItem> importData(JsonNode data, Long actorId) {
        List<TransferItem> items = new ArrayList<>();
        if (!data.isObject()) return List.of(TransferItem.error(label(), "-", "Erwartet wird ein Objekt mit draft und published."));
        // Fehlt eine Fassung in der Datei, bleibt die vorhandene unangetastet
        for (String part : List.of("published", "draft")) {
            JsonNode content = data.get(part);
            if (content == null || content.isNull()) continue;
            String setting = part.equals("published") ? HomepageService.PUBLISHED : HomepageService.DRAFT;
            String name = part.equals("published") ? "veröffentlichte Fassung" : "Entwurf";
            items.add(runner.run(label(), name, () -> {
                String value = blockContent.validate(content);
                Optional<SystemSetting> existing = repository.findById(setting);
                if (existing.isPresent() && Objects.equals(blockContent.read(existing.get().getValue()), blockContent.read(value))) {
                    return TransferItem.Action.UNCHANGED;
                }
                repository.save(new SystemSetting(setting, value));
                return existing.isPresent() ? TransferItem.Action.UPDATED : TransferItem.Action.CREATED;
            }));
        }
        if (items.isEmpty()) items.add(TransferItem.unchanged(label(), "keine Startseite in der Datei"));
        return items;
    }

    private JsonNode read(String key) {
        return repository.findById(key).map(s -> blockContent.read(s.getValue())).orElse(null);
    }
}

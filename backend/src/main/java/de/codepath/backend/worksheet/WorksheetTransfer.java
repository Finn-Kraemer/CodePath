package de.codepath.backend.worksheet;

import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.transfer.ItemRunner;
import de.codepath.backend.platform.transfer.TransferItem;
import de.codepath.backend.platform.transfer.TransferSection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Aufgabenblätter im zentralen Export; beim Import entscheidet der Titel, ob ein Blatt neu ist. */
@Component
@RequiredArgsConstructor
public class WorksheetTransfer implements TransferSection {

    public record WorksheetExport(String title, JsonNode content) {
    }

    private final WorksheetRepository repository;
    private final WorksheetContent worksheetContent;
    private final ItemRunner runner;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return "worksheets";
    }

    @Override
    public String label() {
        return "Aufgabenblätter";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    @Transactional(readOnly = true)
    public JsonNode export(Set<String> only) {
        List<WorksheetExport> sheets = repository.findAllByOrderByUpdatedAtDescIdDesc().stream()
                .filter(w -> only.isEmpty() || only.contains(w.getTitle()))
                .map(w -> new WorksheetExport(w.getTitle(), worksheetContent.read(w.getContent())))
                .toList();
        return objectMapper.valueToTree(sheets);
    }

    @Override
    public List<TransferItem> importData(JsonNode data, Long actorId) {
        if (!data.isArray()) return List.of(TransferItem.error(label(), "-", "Erwartet wird eine Liste von Aufgabenblättern."));
        List<TransferItem> items = new ArrayList<>();
        for (JsonNode node : data) {
            String name = node.path("title").asString("?");
            items.add(runner.run(label(), name, () -> importSheet(objectMapper.treeToValue(node, WorksheetExport.class))));
        }
        return items;
    }

    private TransferItem.Action importSheet(WorksheetExport in) {
        String title = WorksheetContent.title(in.title());
        if (in.content() == null || in.content().isNull()) throw ApiException.badRequest("Das Aufgabenblatt hat keinen Inhalt.");
        String content = worksheetContent.validate(in.content());
        Optional<Worksheet> existing = repository.findFirstByTitleOrderByIdAsc(title);
        if (existing.isPresent() && Objects.equals(worksheetContent.read(existing.get().getContent()), worksheetContent.read(content))) {
            return TransferItem.Action.UNCHANGED;
        }
        Worksheet sheet = existing.orElseGet(Worksheet::new);
        sheet.setTitle(title);
        sheet.setContent(content);
        sheet.setUpdatedAt(LocalDateTime.now());
        repository.save(sheet);
        return existing.isPresent() ? TransferItem.Action.UPDATED : TransferItem.Action.CREATED;
    }
}

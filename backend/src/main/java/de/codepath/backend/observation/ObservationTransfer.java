package de.codepath.backend.observation;

import de.codepath.backend.platform.transfer.ItemRunner;
import de.codepath.backend.platform.transfer.TransferItem;
import de.codepath.backend.platform.transfer.TransferSection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Klassenraum-Vorlagen (Raum + Kriterien mit Optionen) im zentralen Export, Abgleich über den Namen.
 * Personen, Bewertungen und Notizen gehören nicht dazu – die stehen nur im Export des einzelnen Kurses.
 */
@Component
@RequiredArgsConstructor
public class ObservationTransfer implements TransferSection {

    public record TemplateExport(String name, RoomLayout layout, List<ObservationExchange.ExportCriterion> criteria) {
    }

    private final ObservationBoardRepository boardRepository;
    private final ObservationCriterionRepository criterionRepository;
    private final ObservationService service;
    private final ItemRunner runner;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return "observation-templates";
    }

    @Override
    public String label() {
        return "Klassenraum-Vorlagen";
    }

    @Override
    public int order() {
        return 85;
    }

    @Override
    @Transactional(readOnly = true)
    public JsonNode export(Set<String> only) {
        List<TemplateExport> result = new ArrayList<>();
        for (ObservationBoard b : boardRepository.findByCourseIdIsNullOrderByNameAscIdAsc()) {
            if (!only.isEmpty() && !only.contains(b.getName())) continue;
            result.add(new TemplateExport(b.getName(), service.layout(b), criteria(b)));
        }
        return objectMapper.valueToTree(result);
    }

    @Override
    public List<TransferItem> importData(JsonNode data, Long actorId) {
        if (!data.isArray()) return List.of(TransferItem.error(label(), "-", "Erwartet wird eine Liste von Vorlagen."));
        List<TransferItem> items = new ArrayList<>();
        for (JsonNode node : data) {
            String name = node.path("name").asString("?");
            items.add(runner.run(label(), name, () -> importTemplate(objectMapper.treeToValue(node, TemplateExport.class))));
        }
        return items;
    }

    private TransferItem.Action importTemplate(TemplateExport in) {
        String name = in.name() == null || in.name().isBlank() ? "Klassenraum" : in.name().strip();
        if (name.length() > 200) name = name.substring(0, 200);
        RoomLayout layout = RoomLayout.validate(in.layout());
        List<ObservationExchange.ExportCriterion> criteria = in.criteria() == null ? List.of() : in.criteria();
        Optional<ObservationBoard> existing = boardRepository.findFirstByCourseIdIsNullAndNameOrderByIdAsc(name);
        if (existing.isPresent() && service.layout(existing.get()).equals(layout) && criteria(existing.get()).equals(normalized(criteria))) {
            return TransferItem.Action.UNCHANGED;
        }
        final String finalName = name;
        ObservationBoard board = existing.orElseGet(() -> {
            ObservationBoard b = new ObservationBoard();
            b.setName(finalName);
            return b;
        });
        board.setLayout(objectMapper.writeValueAsString(layout));
        board.setUpdatedAt(LocalDateTime.now());
        boardRepository.save(board);
        service.replaceCriteria(board, criteria.stream().map(c -> new ObservationService.CriterionRequest(c.name(), c.kind(), c.min(), c.max(), c.options(), c.multiple())).toList());
        return existing.isPresent() ? TransferItem.Action.UPDATED : TransferItem.Action.CREATED;
    }

    private List<ObservationExchange.ExportCriterion> criteria(ObservationBoard board) {
        return criterionRepository.findByBoardIdOrderByPositionAscIdAsc(board.getId()).stream()
                .map(service::criterionView)
                .map(c -> new ObservationExchange.ExportCriterion(c.name(), c.kind(), c.min(), c.max(), c.options(), c.multiple()))
                .toList();
    }

    /** Wie die Kriterien nach dem Speichern aussähen (für den Vergleich „unverändert“). */
    private List<ObservationExchange.ExportCriterion> normalized(List<ObservationExchange.ExportCriterion> in) {
        List<ObservationExchange.ExportCriterion> result = new ArrayList<>();
        for (ObservationExchange.ExportCriterion c : in) {
            ObservationCriterion probe = new ObservationCriterion();
            service.apply(probe, new ObservationService.CriterionRequest(c.name(), c.kind(), c.min(), c.max(), c.options(), c.multiple()));
            ObservationService.CriterionView v = service.criterionView(probe);
            result.add(new ObservationExchange.ExportCriterion(v.name(), v.kind(), v.min(), v.max(), v.options(), v.multiple()));
        }
        return result;
    }
}

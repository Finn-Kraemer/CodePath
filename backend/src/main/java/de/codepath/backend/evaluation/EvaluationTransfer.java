package de.codepath.backend.evaluation;

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
import java.util.Set;

/** Vorlagen für Bewertungsbögen im zentralen Export. Runden und Antworten bleiben auf dem Server (Datenschutz). */
@Component
@RequiredArgsConstructor
public class EvaluationTransfer implements TransferSection {

    public record TemplateExport(String slug, String name, String description, EvaluationPart kind, List<EvaluationCriterion> criteria) {
    }

    private final EvaluationTemplateRepository repository;
    private final EvaluationTemplateService templateService;
    private final ItemRunner runner;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return "evaluation-templates";
    }

    @Override
    public String label() {
        return "Bewertungsbögen (Vorlagen)";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    @Transactional(readOnly = true)
    public JsonNode export(Set<String> only) {
        List<TemplateExport> list = repository.findAllByOrderByKindAscNameAsc().stream()
                .filter(t -> only.isEmpty() || only.contains(t.getSlug()))
                .map(t -> new TemplateExport(t.getSlug(), t.getName(), t.getDescription(), t.getKind(),
                        templateService.toCriteria(t.getCriteria())))
                .toList();
        return objectMapper.valueToTree(list);
    }

    @Override
    public List<TransferItem> importData(JsonNode data, Long actorId) {
        if (!data.isArray()) return List.of(TransferItem.error(label(), "-", "Erwartet wird eine Liste von Vorlagen."));
        List<TransferItem> items = new ArrayList<>();
        for (JsonNode node : data) {
            String name = node.path("slug").asString("?");
            items.add(runner.run(label(), name, () -> importTemplate(objectMapper.treeToValue(node, TemplateExport.class))));
        }
        return items;
    }

    private TransferItem.Action importTemplate(TemplateExport in) {
        String slug = in.slug() == null ? "" : in.slug().strip();
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 80) {
            throw ApiException.badRequest("Ungültige Adresse der Vorlage.");
        }
        EvaluationTemplate template = repository.findBySlug(slug).orElse(null);
        boolean created = template == null;
        if (created) {
            template = new EvaluationTemplate();
            template.setSlug(slug);
        } else if (Objects.equals(template.getName(), in.name()) && Objects.equals(template.getDescription(), in.description())
                && template.getKind() == in.kind()
                && in.criteria() != null
                && Objects.equals(Criteria.normalize(templateService.toCriteria(template.getCriteria())), Criteria.normalize(in.criteria()))) {
            return TransferItem.Action.UNCHANGED;
        }
        templateService.apply(template, new EvaluationTemplateService.TemplateRequest(in.name(), in.description(), in.kind(), in.criteria()));
        template.setUpdatedAt(LocalDateTime.now());
        repository.save(template);
        return created ? TransferItem.Action.CREATED : TransferItem.Action.UPDATED;
    }
}

package de.codepath.backend.pathway;

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
import java.util.Optional;
import java.util.Set;

/** Lernpfade im zentralen Export; Module und Aufgaben, auf die sie zeigen, müssen nach dem Import existieren. */
@Component
@RequiredArgsConstructor
public class LearningPathTransfer implements TransferSection {

    public record PathExport(String slug, String title, String description, Boolean published, PathGraph.Graph graph) {
    }

    private final LearningPathRepository repository;
    private final LearningPathService service;
    private final ItemRunner runner;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return "learningPaths";
    }

    @Override
    public String label() {
        return "Lernpfade";
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    @Transactional(readOnly = true)
    public JsonNode export(Set<String> only) {
        List<PathExport> paths = repository.findAllByOrderByPositionAscIdAsc().stream()
                .filter(p -> only.isEmpty() || only.contains(p.getSlug()))
                .map(p -> new PathExport(p.getSlug(), p.getTitle(), p.getDescription(), p.isPublished(), service.read(p.getGraph())))
                .toList();
        return objectMapper.valueToTree(paths);
    }

    @Override
    public List<TransferItem> importData(JsonNode data, Long actorId) {
        if (!data.isArray()) return List.of(TransferItem.error(label(), "-", "Erwartet wird eine Liste von Lernpfaden."));
        List<TransferItem> items = new ArrayList<>();
        for (JsonNode node : data) {
            String name = node.path("slug").asString("?");
            items.add(runner.run(label(), name, () -> importPath(objectMapper.treeToValue(node, PathExport.class))));
        }
        return items;
    }

    private TransferItem.Action importPath(PathExport in) {
        String slug = LearningPathService.slug(in.slug());
        String title = LearningPathService.title(in.title());
        String description = LearningPathService.description(in.description());
        String graph = service.write(service.checked(in.graph() == null ? PathGraph.Graph.empty() : in.graph()));
        boolean published = Boolean.TRUE.equals(in.published());
        Optional<LearningPath> existing = repository.findBySlug(slug);
        LearningPath path = existing.orElseGet(LearningPath::new);
        if (existing.isPresent() && title.equals(path.getTitle()) && description.equals(path.getDescription())
                && published == path.isPublished() && service.read(path.getGraph()).equals(service.read(graph))) {
            return TransferItem.Action.UNCHANGED;
        }
        if (existing.isEmpty()) {
            path.setSlug(slug);
            path.setPosition(repository.findAllByOrderByPositionAscIdAsc().stream().mapToInt(LearningPath::getPosition).max().orElse(-1) + 1);
        }
        path.setTitle(title);
        path.setDescription(description);
        path.setPublished(published);
        path.setGraph(graph);
        path.setUpdatedAt(LocalDateTime.now());
        repository.save(path);
        return existing.isPresent() ? TransferItem.Action.UPDATED : TransferItem.Action.CREATED;
    }
}

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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Lernpfade im zentralen Export; Module, Aufgaben und eingebundene Bausteine müssen nach dem Import existieren. */
@Component
@RequiredArgsConstructor
public class LearningPathTransfer implements TransferSection {

    public record PathExport(String slug, String title, String description, Boolean published,
                             Boolean enforce, Boolean templateOnly, Boolean lockOthers, String unlockRestAfter,
                             PathGraph.Graph graph) {
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
        List<LearningPath> all = repository.findAllByOrderByPositionAscIdAsc();
        Map<String, LearningPath> bySlug = new LinkedHashMap<>();
        Map<String, PathGraph.Graph> graphs = new LinkedHashMap<>();
        for (LearningPath p : all) {
            bySlug.put(p.getSlug(), p);
            graphs.put(p.getSlug(), service.read(p.getGraph()));
        }
        Set<String> wanted = new LinkedHashSet<>();
        if (only.isEmpty()) {
            wanted.addAll(bySlug.keySet());
        } else {
            Deque<String> queue = new ArrayDeque<>(only);
            while (!queue.isEmpty()) {
                String slug = queue.poll();
                if (!bySlug.containsKey(slug) || !wanted.add(slug)) continue;
                PathGraph.Graph g = graphs.get(slug);
                if (g != null) {
                    for (PathGraph.Node n : g.nodes()) {
                        if (n.subpath() && n.path() != null) {
                            queue.add(n.path());
                        }
                    }
                }
            }
        }
        List<String> orderedSlugs = sortByDependencies(wanted, graphs);
        List<PathExport> paths = orderedSlugs.stream()
                .map(bySlug::get)
                .filter(Objects::nonNull)
                .map(p -> new PathExport(p.getSlug(), p.getTitle(), p.getDescription(), p.isPublished(),
                        p.isEnforce(), p.isTemplateOnly(), p.isLockOthers(), p.getUnlockRestAfter(), graphs.get(p.getSlug())))
                .toList();
        return objectMapper.valueToTree(paths);
    }

    @Override
    public List<TransferItem> importData(JsonNode data, Long actorId) {
        if (!data.isArray()) return List.of(TransferItem.error(label(), "-", "Erwartet wird eine Liste von Lernpfaden."));
        List<JsonNode> rawNodes = new ArrayList<>();
        data.forEach(rawNodes::add);
        List<JsonNode> sortedNodes = sortJsonNodesByDependencies(rawNodes);
        List<TransferItem> items = new ArrayList<>();
        for (JsonNode node : sortedNodes) {
            String name = node.path("slug").asString("?");
            items.add(runner.run(label(), name, () -> importPath(objectMapper.treeToValue(node, PathExport.class))));
        }
        return items;
    }

    private static List<String> sortByDependencies(Set<String> slugs, Map<String, PathGraph.Graph> graphs) {
        List<String> ordered = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String slug : slugs) {
            visitSlug(slug, slugs, graphs, visited, ordered);
        }
        return ordered;
    }

    private static void visitSlug(String slug, Set<String> allowed, Map<String, PathGraph.Graph> graphs,
                                  Set<String> visited, List<String> ordered) {
        if (!allowed.contains(slug) || !visited.add(slug)) return;
        PathGraph.Graph g = graphs.get(slug);
        if (g != null) {
            for (PathGraph.Node n : g.nodes()) {
                if (n.subpath() && n.path() != null) {
                    visitSlug(n.path(), allowed, graphs, visited, ordered);
                }
            }
        }
        ordered.add(slug);
    }

    private List<JsonNode> sortJsonNodesByDependencies(List<JsonNode> nodes) {
        Map<String, List<JsonNode>> bySlug = new LinkedHashMap<>();
        Map<String, PathGraph.Graph> graphs = new LinkedHashMap<>();
        List<JsonNode> unslugged = new ArrayList<>();
        for (JsonNode node : nodes) {
            String s = node.path("slug").asString("");
            if (s.isBlank()) {
                unslugged.add(node);
            } else {
                bySlug.computeIfAbsent(s, k -> new ArrayList<>()).add(node);
                try {
                    PathExport exp = objectMapper.treeToValue(node, PathExport.class);
                    if (exp.graph() != null) graphs.put(s, exp.graph());
                } catch (Exception ignored) {
                }
            }
        }
        List<String> orderedSlugs = sortByDependencies(bySlug.keySet(), graphs);
        List<JsonNode> result = new ArrayList<>(unslugged);
        for (String s : orderedSlugs) {
            result.addAll(bySlug.getOrDefault(s, List.of()));
        }
        return result;
    }

    private TransferItem.Action importPath(PathExport in) {
        String slug = LearningPathService.slug(in.slug());
        String title = LearningPathService.title(in.title());
        String description = LearningPathService.description(in.description());
        PathGraph.Graph checkedGraph = service.checked(slug, in.graph() == null ? PathGraph.Graph.empty() : in.graph());
        String unlockRestAfter = in.unlockRestAfter() == null || in.unlockRestAfter().isBlank() ? null : in.unlockRestAfter().strip();
        service.validateUnlockRestAfter(checkedGraph, unlockRestAfter);
        String graph = service.write(checkedGraph);
        boolean published = Boolean.TRUE.equals(in.published());
        boolean enforce = Boolean.TRUE.equals(in.enforce());
        boolean templateOnly = Boolean.TRUE.equals(in.templateOnly());
        boolean lockOthers = Boolean.TRUE.equals(in.lockOthers());
        Optional<LearningPath> existing = repository.findBySlug(slug);
        LearningPath path = existing.orElseGet(LearningPath::new);
        if (existing.isPresent() && title.equals(path.getTitle()) && description.equals(path.getDescription())
                && published == path.isPublished() && enforce == path.isEnforce()
                && templateOnly == path.isTemplateOnly() && lockOthers == path.isLockOthers()
                && Objects.equals(unlockRestAfter, path.getUnlockRestAfter())
                && service.read(path.getGraph()).equals(service.read(graph))) {
            return TransferItem.Action.UNCHANGED;
        }
        if (existing.isEmpty()) {
            path.setSlug(slug);
            path.setPosition(repository.findAllByOrderByPositionAscIdAsc().stream().mapToInt(LearningPath::getPosition).max().orElse(-1) + 1);
        }
        path.setTitle(title);
        path.setDescription(description);
        path.setPublished(published);
        path.setEnforce(enforce);
        path.setTemplateOnly(templateOnly);
        path.setLockOthers(lockOthers);
        path.setUnlockRestAfter(unlockRestAfter);
        path.setGraph(graph);
        service.validateSingleEnforced(path);
        path.setUpdatedAt(LocalDateTime.now());
        repository.save(path);
        return existing.isPresent() ? TransferItem.Action.UPDATED : TransferItem.Action.CREATED;
    }
}

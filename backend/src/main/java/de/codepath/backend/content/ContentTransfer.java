package de.codepath.backend.content;

import de.codepath.backend.platform.transfer.TransferItem;
import de.codepath.backend.platform.transfer.TransferSection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Module und ihre aktuellen Aufgabenversionen im zentralen Export – dasselbe Format wie die Content-Pakete,
 * nur als JSON statt als Ordner. Archivierte Aufgaben und Entwürfe bleiben draußen.
 */
@Component
@RequiredArgsConstructor
public class ContentTransfer implements TransferSection {

    public record ModuleExport(ContentPackage.ModuleFile module, List<ContentPackage.TaskFile> tasks) {
    }

    private final ModuleRepository moduleRepository;
    private final TaskRepository taskRepository;
    private final ContentImporter importer;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return "modules";
    }

    @Override
    public String label() {
        return "Module & Aufgaben";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    @Transactional(readOnly = true)
    public JsonNode export(Set<String> only) {
        List<ModuleExport> modules = new ArrayList<>();
        for (Module m : moduleRepository.findAllByOrderBySortOrderAsc()) {
            if (!only.isEmpty() && !only.contains(m.getSlug())) continue;
            List<ContentPackage.TaskFile> tasks = new ArrayList<>();
            for (Task t : taskRepository.findByModuleWithVersion(m.getId())) {
                if (t.getCurrentVersion() == null || t.isArchived()) continue;
                tasks.add(TaskFiles.fromVersion(t, t.getCurrentVersion()));
            }
            modules.add(new ModuleExport(ModuleFiles.toFile(m), tasks));
        }
        return objectMapper.valueToTree(modules);
    }

    /** Wie ein ZIP-Import der Lehrkraft: Geändertes wird zur neuen Version, gelöschte Aufgaben kommen zurück. */
    @Override
    public List<TransferItem> importData(JsonNode data, Long actorId) {
        List<ContentPackage.ModuleBundle> bundles = new ArrayList<>();
        List<TransferItem> problems = new ArrayList<>();
        if (!data.isArray()) return List.of(TransferItem.error(label(), "-", "Erwartet wird eine Liste von Modulen."));
        for (JsonNode node : data) {
            try {
                ModuleExport module = objectMapper.treeToValue(node, ModuleExport.class);
                bundles.add(new ContentPackage.ModuleBundle(module.module(), module.tasks() == null ? List.of() : module.tasks()));
            } catch (RuntimeException e) {
                String slug = node.path("module").path("slug").asString("?");
                problems.add(TransferItem.error(ContentImporter.MODULES, slug, "Ungültiges Format: " + e.getMessage()));
            }
        }
        List<TransferItem> items = new ArrayList<>(problems);
        items.addAll(importer.importBundles(bundles, ContentImporter.Mode.UPDATE, actorId, true).items());
        return items;
    }
}

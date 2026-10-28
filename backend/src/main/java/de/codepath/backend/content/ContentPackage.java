package de.codepath.backend.content;

import de.codepath.backend.engine.TaskType;

import java.util.List;
import java.util.Map;

/**
 * Austauschformat für Inhalte (ADR-5): ein Ordner je Modul mit module.json und einer Datei je
 * Aufgabe unter tasks/, Bilder unter media/. Texte sind Markdown. Das JSON-Schema liegt in content/schema/.
 */
public final class ContentPackage {

    private ContentPackage() {
    }

    public record ModuleFile(String slug, String title, String description, String icon, Integer sortOrder) {
    }

    public record TaskFile(
            String slug,
            TaskType type,
            String title,
            Difficulty difficulty,
            Integer points,
            Integer sortOrder,
            String learningGoal,
            String story,
            String assignment,
            List<String> hints,
            String explanation,
            Map<String, Object> rules,
            Map<String, Object> config) {
    }

    /** media: Bilder des Moduls unter &lt;modul&gt;/media/&lt;schlüssel&gt;, Schlüssel = SHA-256 + Endung. */
    public record ModuleBundle(ModuleFile module, List<TaskFile> tasks, Map<String, byte[]> media) {
        public ModuleBundle(ModuleFile module, List<TaskFile> tasks) {
            this(module, tasks, Map.of());
        }
    }
}

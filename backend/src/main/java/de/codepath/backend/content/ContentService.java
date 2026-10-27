package de.codepath.backend.content;

import de.codepath.backend.engine.AttemptRules;
import de.codepath.backend.engine.TaskSpec;
import de.codepath.backend.engine.TaskType;
import de.codepath.backend.platform.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Lesende Schnittstelle des content-Pakets für alle anderen Pakete. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentService {

    private final ModuleRepository moduleRepository;
    private final TaskRepository taskRepository;
    private final TaskVersionRepository versionRepository;

    public static TaskSpec spec(TaskType type, TaskVersion version) {
        return new TaskSpec(type, version.getPoints(), version.getConfig(),
                AttemptRules.from(type, version.getRules()), version.getHints());
    }

    public List<Module> modules() {
        return moduleRepository.findAllByOrderBySortOrderAsc();
    }

    public Module module(String slug) {
        return moduleRepository.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Modul nicht gefunden."));
    }

    public Module module(Long id) {
        return moduleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Modul nicht gefunden."));
    }

    /** Sichtbare Aufgaben eines Moduls: veröffentlicht und nicht archiviert. */
    public List<PublishedTask> publishedTasks(Module module) {
        return taskRepository.findByModuleWithVersion(module.getId()).stream()
                .filter(t -> !t.isArchived() && t.getCurrentVersion() != null)
                .map(t -> new PublishedTask(t, module, t.getCurrentVersion()))
                .toList();
    }

    public List<PublishedTask> allPublishedTasks() {
        return taskRepository.findAllWithVersion().stream()
                .filter(t -> !t.isArchived() && t.getCurrentVersion() != null)
                .map(t -> new PublishedTask(t, t.getModule(), t.getCurrentVersion()))
                .toList();
    }

    public PublishedTask publishedTask(String moduleSlug, String taskSlug) {
        Module module = module(moduleSlug);
        Task task = taskRepository.findByModuleIdAndSlug(module.getId(), taskSlug)
                .filter(t -> !t.isArchived() && t.getCurrentVersion() != null)
                .orElseThrow(() -> ApiException.notFound("Aufgabe nicht gefunden."));
        return new PublishedTask(task, module, task.getCurrentVersion());
    }

    public PublishedTask publishedTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .filter(t -> t.getCurrentVersion() != null)
                .orElseThrow(() -> ApiException.notFound("Aufgabe nicht gefunden."));
        return new PublishedTask(task, task.getModule(), task.getCurrentVersion());
    }

    public Optional<TaskVersion> version(Long versionId) {
        return versionRepository.findById(versionId);
    }
}

package de.codepath.backend.classroom;

import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.Module;
import de.codepath.backend.identity.CourseService;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.platform.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReleaseService {

    private final ModuleReleaseRepository repository;
    private final TaskReleaseRepository taskReleaseRepository;
    private final AccessPolicy accessPolicy;
    private final ContentService contentService;
    private final CourseService courseService;
    private final AuditService auditService;

    /**
     * @param taskCount, maxPoints was der Kurs in diesem Modul sieht – mit einzeln freigegebenen oder gesperrten Aufgaben
     * @param adjustedTasks Anzahl der Aufgaben mit eigener Freigabe
     */
    public record ModuleReleaseView(Long id, String slug, String title, String description, String icon,
                                    boolean isUnlocked, LocalDateTime availableUntil, int taskCount, int maxPoints,
                                    int adjustedTasks) {
    }

    /** Eine Aufgabe in der Modul-Steuerung; mode null = folgt dem Modul. */
    public record TaskReleaseView(Long id, String slug, String title, String type, int points, TaskReleaseMode mode,
                                  boolean visible) {
    }

    public List<ModuleReleaseView> forCourse(Long courseId) {
        courseService.get(courseId);
        Map<Long, ModuleRelease> releases = repository.findByCourseId(courseId).stream()
                .collect(Collectors.toMap(ModuleRelease::getModuleId, r -> r));
        Map<Long, TaskReleaseMode> modes = taskModes(courseId);
        AccessPolicy.Releases view = accessPolicy.forCourse(courseId);
        return contentService.modules().stream().map(m -> {
            ModuleRelease r = releases.get(m.getId());
            var all = contentService.publishedTasks(m);
            var visible = view.visible(all);
            int adjusted = (int) all.stream().filter(t -> modes.containsKey(t.taskId())).count();
            return new ModuleReleaseView(m.getId(), m.getSlug(), m.getTitle(), m.getDescription(), m.getIcon(),
                    r != null && r.isUnlocked(), r != null ? r.getAvailableUntil() : null,
                    visible.size(), visible.stream().mapToInt(t -> t.version().getPoints()).sum(), adjusted);
        }).toList();
    }

    /** Aufgaben eines Moduls mit ihrer Freigabe im Kurs. */
    public List<TaskReleaseView> tasks(Long courseId, Long moduleId) {
        courseService.get(courseId);
        Module module = contentService.module(moduleId);
        Map<Long, TaskReleaseMode> modes = taskModes(courseId);
        AccessPolicy.Releases view = accessPolicy.forCourse(courseId);
        return contentService.publishedTasks(module).stream()
                .map(t -> new TaskReleaseView(t.taskId(), t.task().getSlug(), t.version().getTitle(), t.type().name(),
                        t.version().getPoints(), modes.get(t.taskId()), view.taskVisible(t)))
                .toList();
    }

    /** Freigabe einer Aufgabe setzen; null = wieder dem Modul folgen. */
    @Transactional
    public TaskReleaseView setTaskMode(Long courseId, Long moduleId, Long taskId, TaskReleaseMode mode) {
        courseService.get(courseId);
        Module module = contentService.module(moduleId);
        var task = contentService.publishedTasks(module).stream().filter(t -> t.taskId().equals(taskId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Aufgabe nicht in diesem Modul."));
        TaskRelease.Key key = new TaskRelease.Key(courseId, taskId);
        if (mode == null) taskReleaseRepository.deleteById(key);
        else taskReleaseRepository.save(new TaskRelease(courseId, taskId, mode));
        Map<String, Object> details = new HashMap<>();
        details.put("task", module.getSlug() + "/" + task.task().getSlug());
        details.put("mode", mode == null ? "MODULE" : mode.name());
        auditService.log("TASK_RELEASE_SET", "course:" + courseId, details);
        return new TaskReleaseView(task.taskId(), task.task().getSlug(), task.version().getTitle(), task.type().name(),
                task.version().getPoints(), mode, accessPolicy.forCourse(courseId).taskVisible(task));
    }

    private Map<Long, TaskReleaseMode> taskModes(Long courseId) {
        Map<Long, TaskReleaseMode> modes = new HashMap<>();
        taskReleaseRepository.findByCourseId(courseId).forEach(r -> modes.put(r.getTaskId(), r.getMode()));
        return modes;
    }

    @Transactional
    public boolean toggle(Long courseId, Long moduleId) {
        Module module = contentService.module(moduleId);
        courseService.get(courseId);
        ModuleRelease release = repository.findById(new ModuleRelease.Key(courseId, moduleId))
                .orElseGet(() -> new ModuleRelease(courseId, moduleId));
        release.setUnlocked(!release.isUnlocked());
        repository.save(release);
        auditService.log(release.isUnlocked() ? "MODULE_UNLOCKED" : "MODULE_LOCKED", "course:" + courseId,
                Map.of("module", module.getSlug()));
        return release.isUnlocked();
    }

    @Transactional
    public void setDeadline(Long courseId, Long moduleId, LocalDateTime deadline) {
        Module module = contentService.module(moduleId);
        courseService.get(courseId);
        ModuleRelease release = repository.findById(new ModuleRelease.Key(courseId, moduleId))
                .orElseGet(() -> new ModuleRelease(courseId, moduleId));
        release.setAvailableUntil(deadline);
        repository.save(release);
        Map<String, Object> details = new HashMap<>();
        details.put("module", module.getSlug());
        details.put("deadline", deadline == null ? null : deadline.toString());
        auditService.log("MODULE_DEADLINE_SET", "course:" + courseId, details);
    }
}

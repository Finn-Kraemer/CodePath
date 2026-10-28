package de.codepath.backend.classroom;

import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.Module;
import de.codepath.backend.identity.CourseService;
import de.codepath.backend.platform.audit.AuditService;
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
    private final ContentService contentService;
    private final CourseService courseService;
    private final AuditService auditService;

    public record ModuleReleaseView(Long id, String slug, String title, String description, String icon,
                                    boolean isUnlocked, LocalDateTime availableUntil, int taskCount, int maxPoints) {
    }

    public List<ModuleReleaseView> forCourse(Long courseId) {
        courseService.get(courseId);
        Map<Long, ModuleRelease> releases = repository.findByCourseId(courseId).stream()
                .collect(Collectors.toMap(ModuleRelease::getModuleId, r -> r));
        return contentService.modules().stream().map(m -> {
            ModuleRelease r = releases.get(m.getId());
            var tasks = contentService.publishedTasks(m);
            return new ModuleReleaseView(m.getId(), m.getSlug(), m.getTitle(), m.getDescription(), m.getIcon(),
                    r != null && r.isUnlocked(), r != null ? r.getAvailableUntil() : null,
                    tasks.size(), tasks.stream().mapToInt(t -> t.version().getPoints()).sum());
        }).toList();
    }

    /** IDs der für den Kurs freigeschalteten Module (für Zeugnis und Fortschritt). */
    public List<Long> unlockedModuleIds(Long courseId) {
        return repository.findByCourseId(courseId).stream().filter(ModuleRelease::isUnlocked).map(ModuleRelease::getModuleId).toList();
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

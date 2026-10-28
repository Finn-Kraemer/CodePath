package de.codepath.backend.classroom;

import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.Module;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Die eine Stelle, die entscheidet, was jemand sehen und wo abgeben darf: Kursmitglied, Kurs aktiv,
 * Modul oder einzelne Aufgabe für den Kurs freigegeben, Frist offen. Die Lehrkraft darf alles.
 *
 * <p>Eine Aufgabe folgt ihrem Modul, außer die Lehrkraft hat sie im Kurs einzeln freigegeben
 * ({@link TaskReleaseMode#SHOWN}, auch bei gesperrtem Modul) oder gesperrt ({@link TaskReleaseMode#HIDDEN}).
 * Ein Modul ist sichtbar, wenn es freigeschaltet ist oder mindestens eine Aufgabe darin einzeln freigegeben.
 * Übersicht, Fortschritt und Teilnahmebestätigung zählen nur die sichtbaren Aufgaben.
 */
@Service
@RequiredArgsConstructor
public class AccessPolicy {

    private final ModuleReleaseRepository releaseRepository;
    private final TaskReleaseRepository taskReleaseRepository;
    private final UserService userService;
    private final ContentService contentService;

    /**
     * @param visible  Modul bzw. Aufgabe erscheint
     * @param open     Abgaben sind möglich
     * @param deadline Frist oder null
     */
    public record ModuleAccess(boolean visible, boolean open, LocalDateTime deadline, String reason) {
    }

    private static final ModuleAccess FULL = new ModuleAccess(true, true, null, null);
    private static final String MODULE_LOCKED = "Dieses Modul ist aktuell gesperrt.";
    private static final String TASK_LOCKED = "Diese Aufgabe ist aktuell gesperrt.";

    /** Freigaben eines Kurses auf einen Blick (zwei Abfragen), für viele Prüfungen hintereinander. */
    public final class Releases {
        private final boolean teacher;
        private final String noCourseReason;
        private final Map<Long, ModuleRelease> modules;
        private final Map<Long, TaskReleaseMode> tasks;

        private Releases(boolean teacher, String noCourseReason, Map<Long, ModuleRelease> modules, Map<Long, TaskReleaseMode> tasks) {
            this.teacher = teacher;
            this.noCourseReason = noCourseReason;
            this.modules = modules;
            this.tasks = tasks;
        }

        public boolean taskVisible(PublishedTask task) {
            return taskAccess(task).visible();
        }

        public ModuleAccess taskAccess(PublishedTask task) {
            if (teacher) return FULL;
            if (noCourseReason != null) return new ModuleAccess(false, false, null, noCourseReason);
            ModuleRelease release = modules.get(task.module().getId());
            TaskReleaseMode mode = tasks.get(task.taskId());
            if (mode == TaskReleaseMode.HIDDEN) return new ModuleAccess(false, false, null, TASK_LOCKED);
            if (mode == TaskReleaseMode.SHOWN) return withDeadline(release);
            return release != null && release.isUnlocked() ? withDeadline(release) : new ModuleAccess(false, false, null, MODULE_LOCKED);
        }

        public ModuleAccess moduleAccess(Module module, List<PublishedTask> moduleTasks) {
            if (teacher) return FULL;
            if (noCourseReason != null) return new ModuleAccess(false, false, null, noCourseReason);
            ModuleRelease release = modules.get(module.getId());
            boolean unlocked = release != null && release.isUnlocked();
            boolean anyShown = moduleTasks.stream().anyMatch(t -> tasks.get(t.taskId()) == TaskReleaseMode.SHOWN);
            return unlocked || anyShown ? withDeadline(release) : new ModuleAccess(false, false, null, MODULE_LOCKED);
        }

        public List<PublishedTask> visible(List<PublishedTask> moduleTasks) {
            return moduleTasks.stream().filter(this::taskVisible).toList();
        }
    }

    private static ModuleAccess withDeadline(ModuleRelease release) {
        LocalDateTime deadline = release == null ? null : release.getAvailableUntil();
        if (deadline != null && deadline.isBefore(LocalDateTime.now())) {
            return new ModuleAccess(true, false, deadline, "Die Bearbeitungszeit für dieses Modul ist abgelaufen.");
        }
        return new ModuleAccess(true, true, deadline, null);
    }

    public Releases releases(User user) {
        if (user.isTeacher()) return new Releases(true, null, Map.of(), Map.of());
        Optional<Course> course = userService.courseOf(user.getId()).filter(Course::isActive);
        if (course.isEmpty()) return new Releases(false, "Du bist in keinem aktiven Kurs.", Map.of(), Map.of());
        return forCourse(course.get().getId());
    }

    /** Freigaben eines Kurses aus Sicht einer Schülerin (für Zeugnisse und Modul-Steuerung). */
    public Releases forCourse(Long courseId) {
        Map<Long, ModuleRelease> modules = releaseRepository.findByCourseId(courseId).stream()
                .collect(Collectors.toMap(ModuleRelease::getModuleId, Function.identity()));
        Map<Long, TaskReleaseMode> tasks = new HashMap<>();
        taskReleaseRepository.findByCourseId(courseId).forEach(r -> tasks.put(r.getTaskId(), r.getMode()));
        return new Releases(false, null, modules, tasks);
    }

    public ModuleAccess access(User user, Module module) {
        return releases(user).moduleAccess(module, contentService.publishedTasks(module));
    }

    /** Sichtbare Aufgaben eines Moduls für diese Person. */
    public List<PublishedTask> visibleTasks(User user, Module module) {
        return releases(user).visible(contentService.publishedTasks(module));
    }

    public void requireVisible(User user, Module module) {
        ModuleAccess access = access(user, module);
        if (!access.visible()) throw ApiException.forbidden(access.reason());
    }

    public ModuleAccess requireTaskVisible(User user, PublishedTask task) {
        ModuleAccess access = releases(user).taskAccess(task);
        if (!access.visible()) throw ApiException.forbidden(access.reason());
        return access;
    }

    public void requireTaskOpen(User user, PublishedTask task) {
        ModuleAccess access = requireTaskVisible(user, task);
        if (!access.open()) throw ApiException.forbidden(access.reason());
    }
}

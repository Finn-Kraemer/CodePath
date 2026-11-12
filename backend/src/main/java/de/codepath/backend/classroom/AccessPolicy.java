package de.codepath.backend.classroom;

import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.Module;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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
 * Modul oder einzelne Aufgabe freigegeben, Frist offen. Die Lehrkraft darf alles.
 *
 * <p>Rangfolge der Freigaben (die erste, die etwas festlegt, gilt):
 * <ol>
 *   <li>Aufgabe für die Person ({@link UserRelease} mit {@code taskId})</li>
 *   <li>Modul für die Person ({@link UserRelease} mit {@code moduleId})</li>
 *   <li>Aufgabe im Kurs ({@link TaskReleaseMode#SHOWN}, auch bei gesperrtem Modul, oder {@link TaskReleaseMode#HIDDEN})</li>
 *   <li>Modul im Kurs ({@link ModuleRelease})</li>
 * </ol>
 * Was für eine Person freigegeben ist, gilt auch an einem verbindlichen CodePath vorbei. Ein Modul ist sichtbar,
 * wenn mindestens eine Aufgabe darin sichtbar ist. Übersicht, Fortschritt und Teilnahmebestätigung zählen nur
 * die sichtbaren Aufgaben.
 */
@Service
@RequiredArgsConstructor
public class AccessPolicy {

    private final ModuleReleaseRepository releaseRepository;
    private final TaskReleaseRepository taskReleaseRepository;
    private final UserReleaseRepository userReleaseRepository;
    private final UserService userService;
    private final ContentService contentService;
    private final ObjectProvider<PathGate> pathGate;

    /**
     * @param visible  Modul bzw. Aufgabe erscheint
     * @param open     Abgaben sind möglich
     * @param blocked  Durch einen verbindlichen CodePath noch gesperrt (Inhalt und Abgabe gesperrt)
     * @param deadline Frist oder null
     */
    public record ModuleAccess(boolean visible, boolean open, boolean blocked, LocalDateTime deadline, String reason) {
        public ModuleAccess(boolean visible, boolean open, LocalDateTime deadline, String reason) {
            this(visible, open, false, deadline, reason);
        }
    }

    private static final ModuleAccess FULL = new ModuleAccess(true, true, false, null, null);
    private static final String MODULE_LOCKED = "Dieses Modul ist aktuell gesperrt.";
    private static final String TASK_LOCKED = "Diese Aufgabe ist aktuell gesperrt.";

    /** Freigaben eines Kurses (und ggf. einer Person) auf einen Blick, für viele Prüfungen hintereinander. */
    public final class Releases {
        private final boolean teacher;
        private final String noCourseReason;
        private final Map<Long, ModuleRelease> modules;
        private final Map<Long, TaskReleaseMode> tasks;
        private final Map<Long, UserRelease> userModules;
        private final Map<Long, UserRelease> userTasks;
        private final PathGate.Evaluation gate;

        private Releases(boolean teacher, String noCourseReason, Map<Long, ModuleRelease> modules,
                         Map<Long, TaskReleaseMode> tasks, Map<Long, UserRelease> userModules,
                         Map<Long, UserRelease> userTasks, PathGate.Evaluation gate) {
            this.teacher = teacher;
            this.noCourseReason = noCourseReason;
            this.modules = modules;
            this.tasks = tasks;
            this.userModules = userModules;
            this.userTasks = userTasks;
            this.gate = gate;
        }

        Releases withGate(PathGate.Evaluation evaluation) {
            return new Releases(teacher, noCourseReason, modules, tasks, userModules, userTasks, evaluation);
        }

        Releases withUser(List<UserRelease> releases) {
            Map<Long, UserRelease> byModule = new HashMap<>();
            Map<Long, UserRelease> byTask = new HashMap<>();
            for (UserRelease r : releases) {
                if (r.getTaskId() != null) byTask.put(r.getTaskId(), r);
                else if (r.getModuleId() != null) byModule.put(r.getModuleId(), r);
            }
            return new Releases(teacher, noCourseReason, modules, tasks, byModule, byTask, gate);
        }

        public boolean taskVisible(PublishedTask task) {
            return taskAccess(task).visible();
        }

        /** Freigabe der Person für diese Aufgabe (Aufgabe vor Modul), sonst null. */
        UserRelease personal(PublishedTask task) {
            UserRelease own = userTasks.get(task.taskId());
            return own != null ? own : userModules.get(task.module().getId());
        }

        public ModuleAccess taskAccess(PublishedTask task) {
            if (teacher) return FULL;
            if (noCourseReason != null) return new ModuleAccess(false, false, false, null, noCourseReason);
            ModuleRelease release = modules.get(task.module().getId());
            UserRelease personal = personal(task);
            if (personal != null) {
                // Die Lehrkraft hat für diese Person entschieden: gilt vor Kurs und CodePath
                if (personal.getMode() == TaskReleaseMode.HIDDEN) return new ModuleAccess(false, false, false, null, TASK_LOCKED);
                return withDeadline(personal.getAvailableUntil() != null ? personal.getAvailableUntil() : deadline(release));
            }
            PathGate.Decision decision = gate.task(task);
            if (decision.state() == PathGate.State.HIDDEN) {
                return new ModuleAccess(false, false, false, null, decision.reason() != null ? decision.reason() : TASK_LOCKED);
            }
            TaskReleaseMode mode = tasks.get(task.taskId());
            if (decision.state() == PathGate.State.UNLOCKED_OUTSIDE) {
                if (mode == TaskReleaseMode.HIDDEN) return new ModuleAccess(false, false, false, null, TASK_LOCKED);
                return withDeadline(deadline(release));
            }
            if (mode == TaskReleaseMode.HIDDEN) return new ModuleAccess(false, false, false, null, TASK_LOCKED);
            ModuleAccess base = mode == TaskReleaseMode.SHOWN
                    ? withDeadline(deadline(release))
                    : (release != null && release.isUnlocked() ? withDeadline(deadline(release)) : new ModuleAccess(false, false, false, null, MODULE_LOCKED));
            if (!base.visible()) return base;
            if (decision.state() == PathGate.State.BLOCKED) {
                return new ModuleAccess(true, false, true, base.deadline(), decision.reason());
            }
            return base;
        }

        public ModuleAccess moduleAccess(Module module, List<PublishedTask> moduleTasks) {
            if (teacher) return FULL;
            if (noCourseReason != null) return new ModuleAccess(false, false, false, null, noCourseReason);
            ModuleRelease release = modules.get(module.getId());
            UserRelease ownModule = userModules.get(module.getId());
            LocalDateTime deadline = ownModule != null && ownModule.getMode() == TaskReleaseMode.SHOWN && ownModule.getAvailableUntil() != null
                    ? ownModule.getAvailableUntil() : deadline(release);
            boolean personal = moduleTasks.stream().anyMatch(t -> {
                UserRelease r = personal(t);
                return r != null && r.getMode() == TaskReleaseMode.SHOWN;
            });
            PathGate.Decision decision = gate.module(module);
            if (decision.state() == PathGate.State.HIDDEN && !personal) {
                return new ModuleAccess(false, false, false, null, decision.reason() != null ? decision.reason() : MODULE_LOCKED);
            }
            if (decision.state() == PathGate.State.UNLOCKED_OUTSIDE && (ownModule == null || ownModule.getMode() == TaskReleaseMode.SHOWN)) {
                return withDeadline(deadline);
            }
            List<PublishedTask> vis = visible(moduleTasks);
            if (vis.isEmpty()) return new ModuleAccess(false, false, false, null, MODULE_LOCKED);
            if (vis.stream().allMatch(t -> taskAccess(t).blocked())) {
                ModuleAccess first = taskAccess(vis.getFirst());
                return new ModuleAccess(true, false, true, first.deadline(), first.reason());
            }
            return withDeadline(deadline);
        }

        public List<PublishedTask> visible(List<PublishedTask> moduleTasks) {
            return moduleTasks.stream().filter(this::taskVisible).toList();
        }

        /** Aufgaben, die die Person nur über eine Bonus-Freigabe sieht (zählen nicht in der Rangliste). */
        public boolean bonus(PublishedTask task) {
            UserRelease r = personal(task);
            return r != null && r.getMode() == TaskReleaseMode.SHOWN && r.isBonus();
        }

        /** Für diese Person eigens freigegeben (Aufgabe oder Modul). */
        public boolean personallyShown(PublishedTask task) {
            UserRelease r = personal(task);
            return r != null && r.getMode() == TaskReleaseMode.SHOWN;
        }
    }

    private static LocalDateTime deadline(ModuleRelease release) {
        return release == null ? null : release.getAvailableUntil();
    }

    private static ModuleAccess withDeadline(LocalDateTime deadline) {
        if (deadline != null && deadline.isBefore(LocalDateTime.now())) {
            return new ModuleAccess(true, false, false, deadline, "Die Bearbeitungszeit für dieses Modul ist abgelaufen.");
        }
        return new ModuleAccess(true, true, false, deadline, null);
    }

    /** Reine Kursfreigaben einer Person ohne CodePath-Sperren (Grundlage für die Pfadauswertung). */
    public Releases courseReleases(User user) {
        if (user.isTeacher()) return new Releases(true, null, Map.of(), Map.of(), Map.of(), Map.of(), PathGate.NO_OP);
        // Archivierte Kurse bleiben lesbar (Schüler können sich dort nicht anmelden, die Lehrkraft exportiert)
        Optional<Course> course = userService.courseOf(user.getId()).filter(Course::isReadable);
        if (course.isEmpty()) return new Releases(false, "Du bist in keinem aktiven Kurs.", Map.of(), Map.of(), Map.of(), Map.of(), PathGate.NO_OP);
        return forCourse(course.get().getId()).withUser(userReleaseRepository.findByUserId(user.getId()));
    }

    public Releases releases(User user) {
        Releases base = courseReleases(user);
        if (user.isTeacher() || base.noCourseReason != null) return base;
        PathGate gateBean = pathGate.getIfAvailable();
        if (gateBean == null) return base;
        return base.withGate(gateBean.evaluate(user, base));
    }

    /** Freigaben eines Kurses aus Sicht einer Schülerin (für Zeugnisse und Modul-Steuerung). */
    public Releases forCourse(Long courseId) {
        Map<Long, ModuleRelease> modules = releaseRepository.findByCourseId(courseId).stream()
                .collect(Collectors.toMap(ModuleRelease::getModuleId, Function.identity()));
        Map<Long, TaskReleaseMode> tasks = new HashMap<>();
        taskReleaseRepository.findByCourseId(courseId).forEach(r -> tasks.put(r.getTaskId(), r.getMode()));
        return new Releases(false, null, modules, tasks, Map.of(), Map.of(), PathGate.NO_OP);
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
        if (!access.visible() || access.blocked()) throw ApiException.forbidden(access.reason());
        return access;
    }

    public void requireTaskOpen(User user, PublishedTask task) {
        ModuleAccess access = requireTaskVisible(user, task);
        if (!access.open()) throw ApiException.forbidden(access.reason());
    }
}

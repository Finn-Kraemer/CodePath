package de.codepath.backend.classroom;

import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.Module;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Differenzierung: Module oder Aufgaben für einzelne Schülerinnen und Schüler freigeben oder sperren –
 * Zusatzaufgaben für Schnelle, leichtere Aufgaben zur Unterstützung. Geht den Kursfreigaben vor
 * (Rangfolge in {@link AccessPolicy}).
 */
@Service
@RequiredArgsConstructor
public class UserReleaseService {

    private final UserReleaseRepository repository;
    private final UserService userService;
    private final ContentService contentService;
    private final AuditService auditService;

    /** Eine Freigabe für eine Person; genau eins von Modul und Aufgabe ist gesetzt. */
    public record UserReleaseView(Long id, String username, String displayName, Long moduleId, String moduleSlug,
                                  String moduleTitle, String moduleIcon, Long taskId, String taskSlug, String taskTitle,
                                  TaskReleaseMode mode, LocalDateTime availableUntil, boolean bonus) {
    }

    /**
     * @param mode SHOWN, HIDDEN oder null (Freigabe für die Person aufheben)
     */
    public record Change(List<String> usernames, Long moduleId, Long taskId, TaskReleaseMode mode,
                         LocalDateTime availableUntil, boolean bonus) {
    }

    @Transactional(readOnly = true)
    public List<UserReleaseView> forStudent(String username) {
        User user = student(username);
        return repository.findByUserId(user.getId()).stream().map(r -> view(r, user))
                .sorted(Comparator.comparing(UserReleaseView::moduleTitle, Comparator.nullsLast(String::compareTo))
                        .thenComparing(v -> v.taskId() == null ? "" : v.taskTitle()))
                .toList();
    }

    /** Freigaben für Mitglieder eines Kurses in einem Modul (das Modul selbst und seine Aufgaben). */
    @Transactional(readOnly = true)
    public List<UserReleaseView> forModule(Long courseId, Long moduleId) {
        Module module = contentService.module(moduleId);
        Set<Long> taskIds = new HashSet<>();
        for (PublishedTask t : contentService.publishedTasks(module)) taskIds.add(t.taskId());
        List<Long> members = userService.memberIds(courseId);
        Map<Long, User> users = new HashMap<>();
        for (Long id : members) userService.byId(id).ifPresent(u -> users.put(id, u));
        return repository.findByUserIdIn(members).stream()
                .filter(r -> moduleId.equals(r.getModuleId()) || (r.getTaskId() != null && taskIds.contains(r.getTaskId())))
                .map(r -> view(r, users.get(r.getUserId())))
                .sorted(Comparator.comparing(UserReleaseView::username))
                .toList();
    }

    @Transactional
    public List<UserReleaseView> apply(Change change) {
        if ((change.moduleId() == null) == (change.taskId() == null)) {
            throw ApiException.badRequest("Bitte entweder ein Modul oder eine Aufgabe angeben.");
        }
        if (change.usernames() == null || change.usernames().isEmpty()) {
            throw ApiException.badRequest("Bitte mindestens eine Person auswählen.");
        }
        if (change.usernames().size() > 500) throw ApiException.badRequest("Höchstens 500 Personen auf einmal.");
        String target;
        if (change.taskId() != null) {
            PublishedTask task = contentService.publishedTask(change.taskId());
            target = task.module().getSlug() + "/" + task.task().getSlug();
        } else {
            target = contentService.module(change.moduleId()).getSlug();
        }
        List<UserReleaseView> result = new ArrayList<>();
        for (String username : new LinkedHashSet<>(change.usernames())) {
            User user = student(username);
            Optional<UserRelease> existing = change.taskId() != null
                    ? repository.findByUserIdAndTaskId(user.getId(), change.taskId())
                    : repository.findByUserIdAndModuleId(user.getId(), change.moduleId());
            if (change.mode() == null) {
                existing.ifPresent(repository::delete);
                continue;
            }
            UserRelease release = existing.orElseGet(UserRelease::new);
            release.setUserId(user.getId());
            release.setModuleId(change.moduleId());
            release.setTaskId(change.taskId());
            release.setMode(change.mode());
            release.setAvailableUntil(change.mode() == TaskReleaseMode.SHOWN ? change.availableUntil() : null);
            release.setBonus(change.mode() == TaskReleaseMode.SHOWN && change.bonus());
            repository.save(release);
            result.add(view(release, user));
        }
        Map<String, Object> details = new HashMap<>();
        details.put("target", target);
        details.put("mode", change.mode() == null ? "NONE" : change.mode().name());
        details.put("users", new ArrayList<>(new LinkedHashSet<>(change.usernames())));
        details.put("bonus", change.bonus());
        if (change.availableUntil() != null) details.put("deadline", change.availableUntil().toString());
        auditService.log("USER_RELEASE_SET", change.taskId() != null ? "task:" + change.taskId() : "module:" + change.moduleId(), details);
        return result;
    }

    @Transactional
    public void delete(String username, Long id) {
        User user = student(username);
        UserRelease release = repository.findById(id).filter(r -> r.getUserId().equals(user.getId()))
                .orElseThrow(() -> ApiException.notFound("Freigabe nicht gefunden."));
        repository.delete(release);
        auditService.log("USER_RELEASE_SET", release.getTaskId() != null ? "task:" + release.getTaskId() : "module:" + release.getModuleId(),
                Map.of("mode", "NONE", "users", List.of(username)));
    }

    /**
     * Aufgaben, die eine Person nur als Bonus bekommen hat (Zusatzaufgabe ohne Ranglistenwirkung).
     * Eine eigene Aufgabenfreigabe ohne Bonus geht einer Bonus-Freigabe des Moduls vor.
     */
    @Transactional(readOnly = true)
    public Map<Long, Set<Long>> bonusTasks(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<Long, List<UserRelease>> byUser = new HashMap<>();
        for (UserRelease r : repository.findByUserIdIn(userIds)) byUser.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(r);
        Map<Long, Set<Long>> result = new HashMap<>();
        Map<Long, List<PublishedTask>> moduleTasks = new HashMap<>();
        byUser.forEach((userId, releases) -> {
            Map<Long, UserRelease> own = new HashMap<>();
            for (UserRelease r : releases) if (r.getTaskId() != null) own.put(r.getTaskId(), r);
            Set<Long> bonus = new HashSet<>();
            for (UserRelease r : releases) {
                if (r.getMode() != TaskReleaseMode.SHOWN || !r.isBonus()) continue;
                if (r.getTaskId() != null) {
                    bonus.add(r.getTaskId());
                } else {
                    for (PublishedTask t : moduleTasks.computeIfAbsent(r.getModuleId(),
                            id -> contentService.publishedTasks(contentService.module(id)))) {
                        if (!own.containsKey(t.taskId())) bonus.add(t.taskId());
                    }
                }
            }
            if (!bonus.isEmpty()) result.put(userId, bonus);
        });
        return result;
    }

    private User student(String username) {
        User user = userService.byUsername(username);
        if (user.isTeacher()) throw ApiException.badRequest("Freigaben pro Person gibt es nur für Schülerinnen und Schüler.");
        return user;
    }

    private UserReleaseView view(UserRelease r, User user) {
        Module module;
        PublishedTask task = null;
        if (r.getTaskId() != null) {
            task = contentService.publishedTask(r.getTaskId());
            module = task.module();
        } else {
            module = contentService.module(r.getModuleId());
        }
        return new UserReleaseView(r.getId(), user == null ? null : user.getUsername(), user == null ? null : user.getVisibleName(),
                module.getId(), module.getSlug(), module.getTitle(), module.getIcon(),
                r.getTaskId(), task == null ? null : task.task().getSlug(), task == null ? null : task.version().getTitle(),
                r.getMode(), r.getAvailableUntil(), r.isBonus());
    }
}

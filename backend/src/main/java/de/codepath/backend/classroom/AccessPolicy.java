package de.codepath.backend.classroom;

import de.codepath.backend.content.Module;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Die eine Stelle, die entscheidet, ob jemand ein Modul sehen und darin abgeben darf:
 * Kursmitglied, Kurs aktiv, Modul für den Kurs freigeschaltet, Frist offen. Die Lehrkraft darf alles.
 */
@Service
@RequiredArgsConstructor
public class AccessPolicy {

    private final ModuleReleaseRepository releaseRepository;
    private final UserService userService;

    /**
     * @param visible  Modul erscheint in der Liste
     * @param open     Abgaben sind möglich
     * @param deadline Frist oder null
     */
    public record ModuleAccess(boolean visible, boolean open, LocalDateTime deadline, String reason) {
    }

    public ModuleAccess access(User user, Module module) {
        if (user.isTeacher()) return new ModuleAccess(true, true, null, null);
        Optional<Course> course = userService.courseOf(user.getId()).filter(Course::isActive);
        if (course.isEmpty()) return new ModuleAccess(false, false, null, "Du bist in keinem aktiven Kurs.");
        return access(releaseRepository.findById(new ModuleRelease.Key(course.get().getId(), module.getId())));
    }

    /** Zugriff für viele Module auf einmal (eine Abfrage). */
    public Map<Long, ModuleAccess> accessByModule(User user, Iterable<Module> modules) {
        Map<Long, ModuleRelease> releases = userService.courseOf(user.getId()).filter(Course::isActive)
                .map(c -> releaseRepository.findByCourseId(c.getId()).stream()
                        .collect(Collectors.toMap(ModuleRelease::getModuleId, Function.identity())))
                .orElse(Map.of());
        Map<Long, ModuleAccess> result = new java.util.HashMap<>();
        for (Module module : modules) {
            result.put(module.getId(), user.isTeacher()
                    ? new ModuleAccess(true, true, null, null)
                    : access(Optional.ofNullable(releases.get(module.getId()))));
        }
        return result;
    }

    private static ModuleAccess access(Optional<ModuleRelease> release) {
        if (release.isEmpty() || !release.get().isUnlocked()) {
            return new ModuleAccess(false, false, null, "Dieses Modul ist aktuell gesperrt.");
        }
        LocalDateTime deadline = release.get().getAvailableUntil();
        if (deadline != null && deadline.isBefore(LocalDateTime.now())) {
            return new ModuleAccess(true, false, deadline, "Die Bearbeitungszeit für dieses Modul ist abgelaufen.");
        }
        return new ModuleAccess(true, true, deadline, null);
    }

    public void requireVisible(User user, Module module) {
        ModuleAccess access = access(user, module);
        if (!access.visible()) throw ApiException.forbidden(access.reason());
    }

    public void requireOpen(User user, Module module) {
        ModuleAccess access = access(user, module);
        if (!access.open()) throw ApiException.forbidden(access.reason());
    }
}

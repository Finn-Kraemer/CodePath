package de.codepath.backend.report;

import de.codepath.backend.classroom.AccessPolicy;
import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.Module;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.CourseService;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Zeugnis: bewertet werden nur die für den Kurs freigeschalteten Module. Punkte kommen aus dem Journal.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ContentService contentService;
    private final AccessPolicy accessPolicy;
    private final ProgressService progressService;
    private final UserService userService;
    private final CourseService courseService;
    private final AuditService auditService;
    private final ReportRenderer renderer = new ReportRenderer();

    public byte[] certificate(User user) {
        Optional<Course> course = userService.courseOf(user.getId());
        return renderer.render(user, course.map(Course::getName).orElse(null), results(user));
    }

    /** Ergebnis eines Moduls mit den Aufgaben, die die Person sieht. */
    public record ModuleScore(Module module, List<PublishedTask> tasks, int achieved, int max) {
        public int percent() {
            return max <= 0 ? 0 : (int) Math.round(100.0 * achieved / max);
        }
    }

    /**
     * Ergebnis je Modul für die Teilnahmebestätigung. Es zählen die Module und Aufgaben, die der Kurs
     * sieht – einzeln freigegebene Aufgaben kommen hinzu, einzeln gesperrte fallen aus Anzahl und Punkten.
     */
    public List<ReportRenderer.ModuleResult> results(User user) {
        return scores(user).stream().map(s -> new ReportRenderer.ModuleResult(s.module().getTitle(), s.achieved(), s.max())).toList();
    }

    public List<ModuleScore> scores(User user) {
        Optional<Course> course = userService.courseOf(user.getId());
        AccessPolicy.Releases releases = course.map(c -> accessPolicy.forCourse(c.getId())).orElseGet(() -> accessPolicy.releases(user));
        Map<Long, Long> points = progressService.pointsByTask(user.getId());
        List<ModuleScore> results = new ArrayList<>();
        for (Module module : contentService.modules()) {
            List<PublishedTask> all = contentService.publishedTasks(module);
            if (!user.isTeacher() && !releases.moduleAccess(module, all).visible()) continue;
            List<PublishedTask> tasks = user.isTeacher() ? all : releases.visible(all);
            if (tasks.isEmpty()) continue;
            int max = tasks.stream().mapToInt(t -> t.version().getPoints()).sum();
            long achieved = tasks.stream().mapToLong(t -> points.getOrDefault(t.taskId(), 0L)).sum();
            // Bonuspunkte können über 100 % heben; im Zeugnis zählt höchstens das Maximum
            results.add(new ModuleScore(module, tasks, (int) Math.clamp(achieved, 0, max), max));
        }
        return results;
    }

    /** Ein Berufsfeld im Kompass: so viel Prozent der Punkte des zugehörigen Moduls sind erreicht. */
    public record CompassEntry(String moduleSlug, String moduleTitle, String icon, String career, int percent, int achieved, int max) {
    }

    /** top: bis zu drei Berufsfelder, in denen die Person am besten abgeschnitten hat (mehr als 0 %). */
    public record Compass(List<CompassEntry> entries, List<CompassEntry> top) {
    }

    /**
     * Berufe-Kompass: Jedes Modul steht für ein Berufsfeld ({@code career} im Modul). Je größer der Anteil
     * der erreichten Punkte, desto eher zeigt der Kompass in diese Richtung. Module ohne Berufsfeld zählen nicht.
     */
    public Compass compass(User user) {
        return compass(scores(user));
    }

    static Compass compass(List<ModuleScore> scores) {
        List<CompassEntry> entries = scores.stream()
                .filter(s -> s.module().getCareer() != null && !s.module().getCareer().isBlank() && s.max() > 0)
                .map(s -> new CompassEntry(s.module().getSlug(), s.module().getTitle(), s.module().getIcon(), s.module().getCareer(),
                        s.percent(), s.achieved(), s.max()))
                .sorted(Comparator.comparingInt(CompassEntry::percent).reversed().thenComparing(Comparator.comparingInt(CompassEntry::achieved).reversed()))
                .toList();
        return new Compass(entries, entries.stream().filter(e -> e.percent() > 0).limit(3).toList());
    }

    /** Alle Zeugnisse eines Kurses als ZIP – vor dem Beenden des Kurses herunterladen. */
    public byte[] courseCertificates(Long courseId) {
        Course course = courseService.get(courseId);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Long userId : userService.memberIds(course.getId())) {
                Optional<User> user = userService.byId(userId);
                if (user.isEmpty()) continue;
                zip.putNextEntry(new ZipEntry("Teilnahmebestaetigung_" + user.get().getUsername() + ".pdf"));
                zip.write(certificate(user.get()));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw ApiException.badRequest("ZIP konnte nicht erstellt werden.");
        }
        auditService.log("CERTIFICATES_EXPORTED", "course:" + courseId, Map.of());
        return out.toByteArray();
    }
}

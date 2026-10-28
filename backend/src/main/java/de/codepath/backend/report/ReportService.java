package de.codepath.backend.report;

import de.codepath.backend.classroom.ReleaseService;
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
    private final ReleaseService releaseService;
    private final ProgressService progressService;
    private final UserService userService;
    private final CourseService courseService;
    private final AuditService auditService;
    private final ReportRenderer renderer = new ReportRenderer();

    public byte[] certificate(User user) {
        Optional<Course> course = userService.courseOf(user.getId());
        Set<Long> released = course.map(c -> new HashSet<>(releaseService.unlockedModuleIds(c.getId()))).orElse(new HashSet<>());
        Map<Long, Long> points = progressService.pointsByTask(user.getId());
        List<ReportRenderer.ModuleResult> results = new ArrayList<>();
        for (Module module : contentService.modules()) {
            if (!user.isTeacher() && !released.contains(module.getId())) continue;
            List<PublishedTask> tasks = contentService.publishedTasks(module);
            int max = tasks.stream().mapToInt(t -> t.version().getPoints()).sum();
            long achieved = tasks.stream().mapToLong(t -> points.getOrDefault(t.taskId(), 0L)).sum();
            // Bonuspunkte können über 100 % heben; im Zeugnis zählt höchstens das Maximum
            results.add(new ReportRenderer.ModuleResult(module.getTitle(), (int) Math.clamp(achieved, 0, max), max));
        }
        return renderer.render(user, course.map(Course::getName).orElse(null), results);
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

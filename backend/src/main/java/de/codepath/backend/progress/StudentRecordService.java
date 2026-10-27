package de.codepath.backend.progress;

import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Schülerakte und Lernanalyse für die Lehrkraft. */
@Service
@RequiredArgsConstructor
public class StudentRecordService {

    private final UserService userService;
    private final ContentService contentService;
    private final ProgressRepository progressRepository;
    private final AttemptRepository attemptRepository;
    private final PointsService pointsService;
    private final AuditService auditService;

    public record StudentOverview(String username, String displayName, String courseName, Long courseId,
                                  long totalPoints, long completedTasks) {
    }

    public record StudentTask(Long id, String slug, String title, String type, String moduleTitle, int points,
                              String status, boolean isCompleted, boolean isLocked, boolean supportUsed, int hintsUsed,
                              boolean solutionRevealed, int failedAttempts, long timeSpentSeconds, long pointsEarned) {
    }

    public record TaskStatistic(Long taskId, String moduleTitle, String title, String type, long attempts,
                                long failedAttempts, long students, long solved, double averageSeconds) {
    }

    public List<StudentOverview> students(Long courseId) {
        List<User> students = courseId != null
                ? userService.memberIds(courseId).stream().map(userService::byId).flatMap(Optional::stream).toList()
                : userService.students();
        List<Long> ids = students.stream().map(User::getId).toList();
        Map<Long, Long> totals = pointsService.totals(ids);
        Map<Long, Long> solved = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : progressRepository.countSolvedByUser(ids)) solved.put((Long) row[0], (Long) row[1]);
        }
        return students.stream().map(u -> {
            Optional<Course> course = userService.courseOf(u.getId());
            return new StudentOverview(u.getUsername(), u.getVisibleName(), course.map(Course::getName).orElse(null),
                    course.map(Course::getId).orElse(null), totals.getOrDefault(u.getId(), 0L), solved.getOrDefault(u.getId(), 0L));
        }).sorted(Comparator.comparing(StudentOverview::username)).toList();
    }

    public List<StudentTask> tasks(String username) {
        User user = userService.byUsername(username);
        Map<Long, Progress> progress = progressRepository.findByUserId(user.getId()).stream()
                .collect(Collectors.toMap(Progress::getTaskId, Function.identity()));
        Map<Long, Long> points = pointsService.byTask(user.getId());
        return contentService.allPublishedTasks().stream().map(t -> {
            Progress p = progress.get(t.taskId());
            ProgressStatus status = p != null ? p.getStatus() : ProgressStatus.OPEN;
            return new StudentTask(t.taskId(), t.task().getSlug(), t.version().getTitle(), t.type().name(), t.module().getTitle(),
                    t.version().getPoints(), status.name(), status == ProgressStatus.SOLVED, p != null && p.isLocked(),
                    p != null && p.getHintsUsed() > 0, p != null ? p.getHintsUsed() : 0, p != null && p.isSolutionRevealed(),
                    p != null ? p.getFailedAttempts() : 0, p != null ? p.getTimeSpentSeconds() : 0,
                    points.getOrDefault(t.taskId(), 0L));
        }).toList();
    }

    public List<ProgressService.AttemptView> attempts(String username, Long taskId) {
        User user = userService.byUsername(username);
        return attemptRepository.findByUserIdAndTaskIdOrderByCreatedAtDesc(user.getId(), taskId).stream()
                .map(ProgressService.AttemptView::of).toList();
    }

    /**
     * Lehrkraft setzt eine Aufgabe auf gelöst (volle oder halbe Punkte) oder nimmt das zurück. Die
     * Differenz wird als Korrektur gebucht; bestehende Buchungen bleiben stehen.
     */
    @Transactional
    public void toggleCompletion(String username, Long taskId, boolean halfPoints, User teacher) {
        User user = userService.byUsername(username);
        PublishedTask task = contentService.publishedTask(taskId);
        userService.lockForUpdate(user.getId());
        Progress progress = progressRepository.findById(new Progress.Key(user.getId(), taskId)).orElseGet(() -> new Progress(user.getId(), taskId));
        long current = pointsService.forTask(user.getId(), taskId);
        int target = halfPoints ? Math.max(1, task.version().getPoints() / 2) : task.version().getPoints();

        if (progress.getStatus() == ProgressStatus.SOLVED && current == target) {
            pointsService.correct(user.getId(), taskId, (int) -current, "Von der Lehrkraft zurückgesetzt", teacher.getId());
            progress.setStatus(ProgressStatus.OPEN);
            progress.setCompletedAt(null);
        } else {
            pointsService.correct(user.getId(), taskId, (int) (target - current),
                    halfPoints ? "Von der Lehrkraft mit halben Punkten gewertet" : "Von der Lehrkraft als gelöst gewertet", teacher.getId());
            progress.setStatus(ProgressStatus.SOLVED);
            progress.setCompletedAt(LocalDateTime.now());
        }
        progressRepository.save(progress);
    }

    @Transactional
    public boolean toggleLock(String username, Long taskId) {
        User user = userService.byUsername(username);
        contentService.publishedTask(taskId);
        Progress progress = progressRepository.findById(new Progress.Key(user.getId(), taskId)).orElseGet(() -> new Progress(user.getId(), taskId));
        progress.setLocked(!progress.isLocked());
        progressRepository.save(progress);
        auditService.log(progress.isLocked() ? "TASK_LOCKED" : "TASK_UNLOCKED", "user:" + user.getId(), Map.of("task", taskId));
        return progress.isLocked();
    }

    /** Welche Aufgaben die meisten Fehlversuche haben und wie lange sie dauern. */
    public List<TaskStatistic> statistics() {
        Map<Long, Object[]> attempts = new HashMap<>();
        for (Object[] row : attemptRepository.statisticsByTask()) attempts.put((Long) row[0], row);
        List<TaskStatistic> result = new ArrayList<>();
        for (PublishedTask t : contentService.allPublishedTasks()) {
            Object[] row = attempts.get(t.taskId());
            List<Progress> progress = progressRepository.findByTaskId(t.taskId());
            List<Progress> solved = progress.stream().filter(p -> p.getStatus() == ProgressStatus.SOLVED).toList();
            double avg = solved.stream().mapToLong(Progress::getTimeSpentSeconds).filter(s -> s > 0).average().orElse(0);
            result.add(new TaskStatistic(t.taskId(), t.module().getTitle(), t.version().getTitle(), t.type().name(),
                    row != null ? (Long) row[1] : 0, row != null ? ((Number) row[2]).longValue() : 0,
                    row != null ? (Long) row[3] : 0, solved.size(), Math.round(avg)));
        }
        result.sort(Comparator.comparingLong(TaskStatistic::failedAttempts).reversed());
        return result;
    }

    public void requireStudent(String username) {
        if (userService.byUsername(username).isTeacher()) throw ApiException.badRequest("Das ist kein Schülerkonto.");
    }
}

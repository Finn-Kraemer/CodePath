package de.codepath.backend.help;

import de.codepath.backend.classroom.AccessPolicy;
import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.CourseService;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.events.RealtimeUpdateService;
import de.codepath.backend.progress.ProgressCompletedEvent;
import de.codepath.backend.progress.ProgressService;
import de.codepath.backend.progress.ProgressStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stiller Hilfe-Ruf „Ich hänge fest“ (#38). Schüler rufen für eine Aufgabe ihres Kurses und schicken den
 * aktuellen Stand ihrer Antwort mit – bewertet oder als Versuch gezählt wird dabei nichts. Nur Lehrkräfte
 * sehen die Warteschlange mit Entwürfen; Live-Updates gehen nur an Lehrkräfte und die Betroffenen.
 */
@Service
@RequiredArgsConstructor
public class HelpService {

    public static final int MAX_NOTE = 280;
    public static final int MAX_DRAFT = 20_000;
    static final Set<HelpRequest.Status> ACTIVE = EnumSet.of(HelpRequest.Status.OPEN, HelpRequest.Status.CLAIMED);
    static final Set<HelpRequest.Status> CLOSED = EnumSet.of(HelpRequest.Status.RESOLVED, HelpRequest.Status.CANCELLED);
    /** So lange bleiben erledigte Rufe in der Liste der Lehrkraft sichtbar („hat sich selbst gelöst“) */
    static final Duration RECENT = Duration.ofMinutes(30);
    /** Danach werden Entwürfe erledigter Rufe geleert (Datensparsamkeit) */
    static final Duration KEEP_DRAFTS = Duration.ofHours(24);

    private final HelpRequestRepository repository;
    private final UserService userService;
    private final CourseService courseService;
    private final ContentService contentService;
    private final AccessPolicy accessPolicy;
    private final ProgressService progressService;
    private final RealtimeUpdateService realtime;

    /** Rate-Limit je Konto gegen versehentliches Dauerklicken */
    @Value("${codepath.help.min-interval-seconds:30}")
    private long minIntervalSeconds = 30;
    private final Map<Long, Instant> lastCall = new ConcurrentHashMap<>();

    public record HelpCall(String moduleSlug, String taskSlug, String category, String note, String draft,
                           String lastOutput) {
    }

    /** Sicht der rufenden Person: ohne Entwurf, ohne Namen anderer, nur die Zahl der Rufe vor ihr. */
    public record MyHelpRequest(Long id, HelpRequest.Status status, String moduleSlug, String taskSlug, String taskTitle,
                                HelpRequest.Category category, String note, int ahead, String teacherMessage,
                                LocalDateTime createdAt) {
    }

    public record HelpState(MyHelpRequest request) {
    }

    public record QueueEntry(Long id, HelpRequest.Status status, String username, String displayName, String courseName,
                             String moduleSlug, String moduleTitle, String taskSlug, String taskTitle, String taskType,
                             HelpRequest.Category category, String note, String draft, String lastOutput,
                             LocalDateTime createdAt, long waitingSeconds, LocalDateTime updatedAt, String claimedBy, LocalDateTime claimedAt,
                             LocalDateTime resolvedAt, HelpRequest.Resolution resolution, String teacherMessage,
                             int hintsUsed, int failedAttempts, String lastOutcome, String lastFeedback) {
    }

    public record Queue(List<QueueEntry> open, List<QueueEntry> recent) {
    }

    /** Wie oft wurde bei einer Aufgabe Hilfe gerufen? Zeigt zu schwere oder missverständliche Aufgaben. */
    public record HelpStatistic(Long taskId, long calls, long selfSolved, long averageWaitSeconds) {
    }

    // ---------------------------------------------------------------- Schüler

    @Transactional(readOnly = true)
    public HelpState mine(User user) {
        return new HelpState(repository.findFirstByUserIdAndStatusIn(user.getId(), ACTIVE).map(this::mine).orElse(null));
    }

    /** Ruft Hilfe oder aktualisiert den offenen Ruf (Aufgabe, Notiz, Entwurf). */
    @Transactional
    public HelpState call(User user, HelpCall call) {
        if (user.isTeacher()) throw ApiException.badRequest("Hilfe rufen können nur Schülerinnen und Schüler.");
        if (call == null || call.moduleSlug() == null || call.taskSlug() == null) {
            throw ApiException.badRequest("Für welche Aufgabe brauchst du Hilfe?");
        }
        PublishedTask task = contentService.publishedTask(call.moduleSlug(), call.taskSlug());
        accessPolicy.requireTaskVisible(user, task);
        String note = call.note() == null ? null : call.note().strip();
        if (note != null && note.length() > MAX_NOTE) {
            throw ApiException.badRequest("Die Notiz darf höchstens " + MAX_NOTE + " Zeichen haben.");
        }
        HelpRequest.Category category = parseCategory(call.category());
        throttle(user.getId());

        HelpRequest request = repository.findFirstByUserIdAndStatusIn(user.getId(), ACTIVE).orElseGet(() -> {
            HelpRequest created = new HelpRequest();
            created.setUserId(user.getId());
            return created;
        });
        if (request.getId() != null && !request.getTaskId().equals(task.taskId())) {
            // Neue Aufgabe: zurück in die Warteschlange, die Lehrkraft muss neu entscheiden
            request.setStatus(HelpRequest.Status.OPEN);
            request.setClaimedBy(null);
            request.setClaimedAt(null);
            request.setTeacherMessage(null);
        }
        request.setCourseId(userService.courseOf(user.getId()).map(Course::getId).orElse(null));
        request.setTaskId(task.taskId());
        request.setCategory(category);
        request.setNote(note == null || note.isEmpty() ? null : note);
        request.setDraft(limit(call.draft()));
        request.setLastOutput(limit(call.lastOutput()));
        request.setUpdatedAt(LocalDateTime.now());
        repository.saveAndFlush(request);
        publish(request);
        return new HelpState(mine(request));
    }

    /** „Hat sich erledigt“ */
    @Transactional
    public HelpState cancel(User user) {
        repository.findFirstByUserIdAndStatusIn(user.getId(), ACTIVE).ifPresent(request -> {
            close(request, HelpRequest.Status.CANCELLED, HelpRequest.Resolution.CANCELLED);
            publish(request);
        });
        return new HelpState(null);
    }

    // ---------------------------------------------------------------- Lehrkraft

    @Transactional(readOnly = true)
    public Queue queue() {
        Map<Long, String> courseNames = new HashMap<>();
        List<QueueEntry> open = repository.findByStatusInOrderByCreatedAtAsc(ACTIVE).stream()
                .map(r -> entry(r, courseNames)).toList();
        List<QueueEntry> recent = repository.findByStatusInAndResolvedAtAfterOrderByResolvedAtDesc(CLOSED,
                        LocalDateTime.now().minus(RECENT)).stream()
                .map(r -> entry(r, courseNames)).toList();
        return new Queue(open, recent);
    }

    @Transactional(readOnly = true)
    public List<HelpStatistic> statistics() {
        return repository.statisticsByTask().stream()
                .map(row -> new HelpStatistic(((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(), Math.round(((Number) row[3]).doubleValue())))
                .sorted(Comparator.comparingLong(HelpStatistic::calls).reversed())
                .toList();
    }

    /** „Ich komme“ – andere Lehrkräfte sehen, wer sich kümmert. */
    @Transactional
    public QueueEntry claim(User teacher, Long id) {
        HelpRequest request = active(id);
        request.setStatus(HelpRequest.Status.CLAIMED);
        request.setClaimedBy(teacher.getId());
        request.setClaimedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        publish(request);
        return entry(request, new HashMap<>());
    }

    /** „Erledigt“ */
    @Transactional
    public QueueEntry resolve(User teacher, Long id) {
        HelpRequest request = active(id);
        if (request.getClaimedBy() == null) request.setClaimedBy(teacher.getId());
        close(request, HelpRequest.Status.RESOLVED, HelpRequest.Resolution.TEACHER);
        publish(request);
        return entry(request, new HashMap<>());
    }

    /** Kurze Nachricht nur an diese Person, z. B. „Schau dir Tipp 2 an“. */
    @Transactional
    public QueueEntry message(User teacher, Long id, String text) {
        HelpRequest request = active(id);
        String message = text == null ? "" : text.strip();
        if (message.isEmpty()) throw ApiException.badRequest("Die Nachricht ist leer.");
        if (message.length() > MAX_NOTE) throw ApiException.badRequest("Die Nachricht darf höchstens " + MAX_NOTE + " Zeichen haben.");
        request.setTeacherMessage(message);
        request.setUpdatedAt(LocalDateTime.now());
        publish(request);
        return entry(request, new HashMap<>());
    }

    // ---------------------------------------------------------------- Ereignisse

    /** Ein offener Ruf schließt sich, sobald die Person die Aufgabe löst. */
    @EventListener
    @Transactional
    public void onProgressCompleted(ProgressCompletedEvent event) {
        if (event.status() != ProgressStatus.SOLVED) return;
        repository.findFirstByUserIdAndStatusIn(event.userId(), ACTIVE)
                .filter(r -> r.getTaskId().equals(event.taskId()))
                .ifPresent(request -> {
                    close(request, HelpRequest.Status.RESOLVED, HelpRequest.Resolution.SELF_SOLVED);
                    publish(request);
                });
    }

    /** Kurs beendet: alle Rufe des Kurses und seiner Mitglieder löschen. */
    @EventListener
    @Transactional
    public void onCourseEnding(CourseService.CourseEndingEvent event) {
        repository.deleteByCourseOrUsers(event.courseId(), event.userIds().isEmpty() ? List.of(-1L) : event.userIds());
    }

    /** Entwürfe erledigter Rufe nach 24 Stunden leeren. */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void clearOldDrafts() {
        repository.clearDrafts(CLOSED, LocalDateTime.now().minus(KEEP_DRAFTS));
    }

    // ---------------------------------------------------------------- intern

    private HelpRequest active(Long id) {
        HelpRequest request = repository.findById(id).orElseThrow(() -> ApiException.notFound("Hilfe-Ruf nicht gefunden."));
        if (!request.isActive()) throw ApiException.conflict("Dieser Hilfe-Ruf ist schon erledigt.");
        return request;
    }

    private void close(HelpRequest request, HelpRequest.Status status, HelpRequest.Resolution resolution) {
        request.setStatus(status);
        request.setResolution(resolution);
        request.setResolvedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
    }

    private void throttle(Long userId) {
        Instant now = Instant.now();
        Instant previous = lastCall.get(userId);
        if (previous != null && Duration.between(previous, now).getSeconds() < minIntervalSeconds) {
            throw ApiException.tooManyRequests("Bitte warte einen Moment, bevor du erneut Hilfe rufst.");
        }
        lastCall.put(userId, now);
    }

    private static HelpRequest.Category parseCategory(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return HelpRequest.Category.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unbekannte Kategorie.");
        }
    }

    private static String limit(String text) {
        if (text == null || text.isEmpty()) return null;
        return text.length() <= MAX_DRAFT ? text : text.substring(0, MAX_DRAFT) + "\n… (gekürzt)";
    }

    /** Lehrkräfte laden die Warteschlange neu; Betroffene und alle Wartenden ihre Position. */
    private void publish(HelpRequest changed) {
        long open = repository.findByStatusInOrderByCreatedAtAsc(ACTIVE).size();
        realtime.publishToTeachers(RealtimeUpdateService.HELP, Map.of("open", open, "timestamp", System.currentTimeMillis()));
        Set<Long> userIds = new HashSet<>();
        userIds.add(changed.getUserId());
        repository.findByStatusInOrderByCreatedAtAsc(ACTIVE).forEach(r -> userIds.add(r.getUserId()));
        List<String> usernames = userIds.stream().map(userService::byId).flatMap(Optional::stream).map(User::getUsername).toList();
        realtime.publishToUsers(usernames, RealtimeUpdateService.HELP, Map.of("timestamp", System.currentTimeMillis()));
    }

    private MyHelpRequest mine(HelpRequest request) {
        Optional<PublishedTask> task = task(request.getTaskId());
        int ahead = (int) repository.findByStatusInOrderByCreatedAtAsc(ACTIVE).stream()
                .filter(r -> Objects.equals(r.getCourseId(), request.getCourseId()))
                .filter(r -> r.getCreatedAt().isBefore(request.getCreatedAt()))
                .count();
        return new MyHelpRequest(request.getId(), request.getStatus(),
                task.map(t -> t.module().getSlug()).orElse(null), task.map(t -> t.task().getSlug()).orElse(null),
                task.map(t -> t.version().getTitle()).orElse("Aufgabe"), request.getCategory(), request.getNote(),
                ahead, request.getTeacherMessage(), request.getCreatedAt());
    }

    private QueueEntry entry(HelpRequest request, Map<Long, String> courseNames) {
        Optional<PublishedTask> task = task(request.getTaskId());
        Optional<User> student = userService.byId(request.getUserId());
        String claimedBy = request.getClaimedBy() == null ? null
                : userService.byId(request.getClaimedBy()).map(User::getVisibleName).orElse(null);
        String courseName = request.getCourseId() == null ? null
                : courseNames.computeIfAbsent(request.getCourseId(), id -> courseService.get(id).getName());
        ProgressService.TaskProgressView progress = progressService.taskProgressByTask(request.getUserId()).get(request.getTaskId());
        Optional<ProgressService.AttemptView> last = progressService.attempts(request.getUserId(), request.getTaskId()).stream().findFirst();
        return new QueueEntry(request.getId(), request.getStatus(),
                student.map(User::getUsername).orElse("?"), student.map(User::getVisibleName).orElse("?"), courseName,
                task.map(t -> t.module().getSlug()).orElse(null), task.map(t -> t.module().getTitle()).orElse(null),
                task.map(t -> t.task().getSlug()).orElse(null), task.map(t -> t.version().getTitle()).orElse("Aufgabe"),
                task.map(t -> t.type().name()).orElse(null),
                request.getCategory(), request.getNote(), request.getDraft(), request.getLastOutput(),
                request.getCreatedAt(), waitingSeconds(request), request.getUpdatedAt(), claimedBy, request.getClaimedAt(), request.getResolvedAt(),
                request.getResolution(), request.getTeacherMessage(),
                progress == null ? 0 : progress.hintsUsed(), progress == null ? 0 : progress.failedAttempts(),
                last.map(ProgressService.AttemptView::outcome).orElse(null), last.map(ProgressService.AttemptView::feedback).orElse(null));
    }

    /** Wartezeit bis jetzt bzw. bis zum Erledigen – vom Server gerechnet, damit Zeitzonen keine Rolle spielen */
    private static long waitingSeconds(HelpRequest request) {
        LocalDateTime end = request.getResolvedAt() != null ? request.getResolvedAt() : LocalDateTime.now();
        return Math.max(0, Duration.between(request.getCreatedAt(), end).getSeconds());
    }

    private Optional<PublishedTask> task(Long taskId) {
        try {
            return Optional.of(contentService.publishedTask(taskId));
        } catch (ApiException e) {
            return Optional.empty();
        }
    }
}

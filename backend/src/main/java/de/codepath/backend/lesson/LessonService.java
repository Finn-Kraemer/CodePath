package de.codepath.backend.lesson;

import de.codepath.backend.classroom.AnnouncementDisplayMode;
import de.codepath.backend.classroom.ClassroomService;
import de.codepath.backend.classroom.ReleaseService;
import de.codepath.backend.classroom.TaskReleaseMode;
import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.Module;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.evaluation.EvaluationService;
import de.codepath.backend.evaluation.RoundStatus;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.CourseService;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.quiz.QuizService;
import de.codepath.backend.worksheet.WorksheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Stundenabläufe: Die Lehrkraft plant eine Stunde als Folge von Schritten und löst sie in der Stunde mit
 * „Starten“ und „Weiter“ nacheinander aus. Jeder Schritt läuft genau einmal (Zeilensperre und erwarteter
 * Schritt) und in einer eigenen Transaktion: Geht einer schief (z. B. Quiz läuft schon), wird das gemeldet
 * und protokolliert, der Ablauf geht weiter.
 */
@Service
@RequiredArgsConstructor
public class LessonService {

    static final int MAX_STEPS = 50;
    static final int MAX_TEXT = 2_000;

    private final LessonRepository repository;
    private final CourseService courseService;
    private final UserService userService;
    private final ContentService contentService;
    private final ReleaseService releaseService;
    private final QuizService quizService;
    private final EvaluationService evaluationService;
    private final ClassroomService classroomService;
    private final WorksheetService worksheetService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    /** Ergebnis eines ausgeführten Schritts (Protokoll). */
    public record StepResult(int index, LessonStep.Type type, String label, boolean ok, String message, LocalDateTime at) {
    }

    public record LessonSummary(Long id, Long courseId, String courseName, String title, LocalDate date, int steps,
                                Lesson.Status status, int currentStep, int position, LocalDateTime updatedAt) {
    }

    /** labels: lesbare Beschreibung je Schritt (für Liste und Ansicht „Stunde läuft“) */
    public record LessonDetail(Long id, Long courseId, String courseName, String title, LocalDate date, Lesson.Status status,
                               int currentStep, boolean restoreReleases, List<LessonStep> steps, List<String> labels,
                               List<StepResult> log, LocalDateTime startedAt, LocalDateTime stepStartedAt,
                               LocalDateTime finishedAt) {
    }

    public record LessonUpdate(String title, Long courseId, LocalDate date, Boolean restoreReleases, List<LessonStep> steps) {
    }

    // ------------------------------------------------------------------ Verwalten

    @Transactional(readOnly = true)
    public List<LessonSummary> list(Long courseId) {
        return repository.findAllByOrderByPositionAscIdAsc().stream()
                .filter(l -> courseId == null || courseId.equals(l.getCourseId()) || l.getCourseId() == null)
                .map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public LessonDetail get(Long id) {
        return detail(find(id));
    }

    @Transactional
    public LessonDetail create(LessonUpdate update) {
        Lesson lesson = new Lesson();
        lesson.setPosition(repository.findAllByOrderByPositionAscIdAsc().stream().mapToInt(Lesson::getPosition).max().orElse(-1) + 1);
        apply(lesson, update == null ? new LessonUpdate(null, null, null, null, null) : update);
        repository.save(lesson);
        auditService.log("LESSON_CREATED", "lesson:" + lesson.getId(), Map.of("title", lesson.getTitle()));
        return detail(lesson);
    }

    @Transactional
    public LessonDetail update(Long id, LessonUpdate update) {
        Lesson lesson = find(id);
        if (lesson.getStatus() == Lesson.Status.RUNNING && update.steps() != null) {
            throw ApiException.conflict("Der Ablauf läuft gerade – Schritte erst nach dem Beenden ändern.");
        }
        apply(lesson, update);
        return detail(lesson);
    }

    @Transactional
    public LessonDetail duplicate(Long id) {
        Lesson source = find(id);
        Lesson copy = new Lesson();
        copy.setTitle(trimTitle(source.getTitle() + " (Kopie)"));
        copy.setCourseId(source.getCourseId());
        copy.setDate(source.getDate());
        copy.setSteps(source.getSteps());
        copy.setRestoreReleases(source.isRestoreReleases());
        copy.setPosition(source.getPosition() + 1);
        for (Lesson l : repository.findAllByOrderByPositionAscIdAsc()) {
            if (l.getPosition() > source.getPosition()) l.setPosition(l.getPosition() + 1);
        }
        repository.save(copy);
        auditService.log("LESSON_CREATED", "lesson:" + copy.getId(), Map.of("title", copy.getTitle(), "copyOf", id));
        return detail(copy);
    }

    @Transactional
    public void delete(Long id) {
        Lesson lesson = find(id);
        repository.delete(lesson);
        auditService.log("LESSON_DELETED", "lesson:" + id, Map.of("title", lesson.getTitle()));
    }

    /** Neue Reihenfolge der Liste (IDs in gewünschter Reihenfolge). */
    @Transactional
    public List<LessonSummary> reorder(List<Long> ids) {
        Map<Long, Lesson> all = new HashMap<>();
        repository.findAllByOrderByPositionAscIdAsc().forEach(l -> all.put(l.getId(), l));
        int position = 0;
        for (Long id : ids) {
            Lesson l = all.remove(id);
            if (l != null) l.setPosition(position++);
        }
        for (Lesson rest : all.values()) rest.setPosition(position++);
        return repository.findAllByOrderByPositionAscIdAsc().stream().map(this::summary).toList();
    }

    // ------------------------------------------------------------------ Ablauf

    /** Startet den Ablauf und führt den ersten Schritt aus. */
    public LessonDetail start(Long id) {
        User teacher = userService.currentUser();
        int index = inTransaction(() -> {
            Lesson lesson = locked(id);
            if (lesson.getStatus() == Lesson.Status.RUNNING) throw ApiException.conflict("Der Ablauf läuft schon.");
            if (lesson.getCourseId() == null) throw ApiException.badRequest("Bitte zuerst einen Kurs wählen – eine Vorlage lässt sich nicht starten.");
            Course course = courseService.get(lesson.getCourseId());
            if (!course.isActive()) throw ApiException.badRequest("Der Kurs ist nicht aktiv.");
            if (steps(lesson).isEmpty()) throw ApiException.badRequest("Der Ablauf hat noch keine Schritte.");
            lesson.setStatus(Lesson.Status.RUNNING);
            lesson.setStartedAt(LocalDateTime.now());
            lesson.setFinishedAt(null);
            lesson.setRunLog("[]");
            lesson.setCurrentStep(0);
            lesson.setStepStartedAt(LocalDateTime.now());
            lesson.setReleasesSnapshot(lesson.isRestoreReleases()
                    ? objectMapper.writeValueAsString(releaseService.snapshot(lesson.getCourseId())) : null);
            auditService.log("LESSON_STARTED", "lesson:" + id, Map.of("title", lesson.getTitle(), "course", "course:" + lesson.getCourseId()));
            return 0;
        });
        execute(id, index, teacher);
        return get(id);
    }

    /**
     * Führt den nächsten Schritt aus. {@code expected} ist der Schritt, den die Oberfläche als nächsten zeigt –
     * ein doppelter Klick oder ein zweites Fenster löst ihn so nicht zweimal aus.
     */
    public LessonDetail next(Long id, Integer expected) {
        User teacher = userService.currentUser();
        int index = inTransaction(() -> {
            Lesson lesson = locked(id);
            if (lesson.getStatus() != Lesson.Status.RUNNING) throw ApiException.conflict("Der Ablauf läuft nicht.");
            int next = lesson.getCurrentStep() + 1;
            if (expected != null && expected != next) throw ApiException.conflict("Dieser Schritt wurde schon ausgeführt.");
            if (next >= steps(lesson).size()) throw ApiException.conflict("Alle Schritte sind ausgeführt – jetzt beenden.");
            lesson.setCurrentStep(next);
            lesson.setStepStartedAt(LocalDateTime.now());
            return next;
        });
        execute(id, index, teacher);
        return get(id);
    }

    /** Beendet den Ablauf; mit „Freigaben zurücksetzen“ gilt wieder der Stand vom Start. */
    @Transactional
    public LessonDetail finish(Long id) {
        Lesson lesson = locked(id);
        if (lesson.getStatus() != Lesson.Status.RUNNING) throw ApiException.conflict("Der Ablauf läuft nicht.");
        List<StepResult> log = log(lesson);
        if (lesson.isRestoreReleases() && lesson.getReleasesSnapshot() != null) {
            releaseService.restore(lesson.getCourseId(),
                    objectMapper.readValue(lesson.getReleasesSnapshot(), ReleaseService.Snapshot.class));
            log.add(new StepResult(-1, null, "Freigaben zurückgesetzt", true, "Stand vom Start wiederhergestellt", LocalDateTime.now()));
        }
        lesson.setRunLog(objectMapper.writeValueAsString(log));
        lesson.setStatus(Lesson.Status.DONE);
        lesson.setFinishedAt(LocalDateTime.now());
        lesson.setReleasesSnapshot(null);
        auditService.log("LESSON_FINISHED", "lesson:" + id, Map.of("title", lesson.getTitle(), "steps", lesson.getCurrentStep() + 1));
        return detail(lesson);
    }

    /** Zurück auf „geplant“, z. B. um den Ablauf in der nächsten Stunde erneut zu nutzen. */
    @Transactional
    public LessonDetail reset(Long id) {
        Lesson lesson = locked(id);
        if (lesson.getStatus() == Lesson.Status.RUNNING) throw ApiException.conflict("Erst beenden, dann zurücksetzen.");
        lesson.setStatus(Lesson.Status.DRAFT);
        lesson.setCurrentStep(-1);
        lesson.setRunLog("[]");
        lesson.setStartedAt(null);
        lesson.setFinishedAt(null);
        lesson.setStepStartedAt(null);
        return detail(lesson);
    }

    /** Führt Schritt {@code index} in eigener Transaktion aus und protokolliert das Ergebnis. */
    private void execute(Long id, int index, User teacher) {
        Lesson snapshot = inTransaction(() -> find(id));
        LessonStep step = steps(snapshot).get(index);
        String label = label(step);
        StepResult result;
        try {
            String message = inTransaction(() -> run(step, snapshot.getCourseId(), teacher));
            result = new StepResult(index, step.type(), label, true, message, LocalDateTime.now());
        } catch (ApiException e) {
            result = new StepResult(index, step.type(), label, false, e.getMessage(), LocalDateTime.now());
        } catch (RuntimeException e) {
            result = new StepResult(index, step.type(), label, false, "Unerwarteter Fehler: " + e.getClass().getSimpleName(), LocalDateTime.now());
        }
        StepResult done = result;
        inTransaction(() -> {
            Lesson lesson = locked(id);
            List<StepResult> log = log(lesson);
            log.add(done);
            lesson.setRunLog(objectMapper.writeValueAsString(log));
            Map<String, Object> details = new HashMap<>();
            details.put("step", index + 1);
            details.put("type", step.type().name());
            details.put("label", label);
            details.put("ok", done.ok());
            if (done.message() != null) details.put("message", done.message());
            auditService.log("LESSON_STEP_EXECUTED", "lesson:" + id, details);
            return null;
        });
    }

    /** @return kurze Rückmeldung für die Lehrkraft */
    private String run(LessonStep step, Long courseId, User teacher) {
        return switch (step.type()) {
            case NOTE -> null;
            case WORKSHEET -> step.worksheetId() == null ? "Kein Aufgabenblatt gewählt"
                    : "Aufgabenblatt „" + worksheetService.title(step.worksheetId()) + "“ bereit";
            case ANNOUNCEMENT -> {
                AnnouncementDisplayMode mode = step.displayMode() == null ? AnnouncementDisplayMode.HEADER
                        : AnnouncementDisplayMode.valueOf(step.displayMode());
                classroomService.updateAnnouncement(step.text(), mode, teacher);
                yield step.text() == null || step.text().isBlank() ? "Live-Mitteilung ausgeblendet" : "Live-Mitteilung gesendet";
            }
            case EVALUATION -> {
                RoundStatus status = "CLOSED".equals(step.action()) ? RoundStatus.CLOSED : RoundStatus.OPEN;
                var round = evaluationService.get(step.roundId());
                if (!courseId.equals(round.courseId())) throw ApiException.badRequest("Die Bewertungsrunde gehört zu einem anderen Kurs.");
                evaluationService.setStatus(step.roundId(), status);
                yield "Bewertungsrunde „" + round.title() + "“ " + (status == RoundStatus.OPEN ? "geöffnet" : "geschlossen");
            }
            case QUIZ -> {
                quizService.start(teacher, new QuizService.StartRequest(step.text(), courseId, step.taskIds()));
                yield "Quiz-Runde gestartet (" + step.taskIds().size() + (step.taskIds().size() == 1 ? " Frage)" : " Fragen)");
            }
            case RELEASE -> release(step, courseId);
        };
    }

    private String release(LessonStep step, Long courseId) {
        Module module = contentService.module(step.moduleSlug());
        List<String> done = new ArrayList<>();
        if (step.taskSlug() != null && !step.taskSlug().isBlank()) {
            PublishedTask task = contentService.publishedTask(step.moduleSlug(), step.taskSlug());
            TaskReleaseMode mode = switch (step.action() == null ? "SHOW" : step.action()) {
                case "HIDE" -> TaskReleaseMode.HIDDEN;
                case "MODULE" -> null;
                default -> TaskReleaseMode.SHOWN;
            };
            releaseService.setTaskMode(courseId, module.getId(), task.taskId(), mode);
            done.add("Aufgabe „" + task.version().getTitle() + "“ " + (mode == null ? "folgt dem Modul" : mode == TaskReleaseMode.SHOWN ? "freigegeben" : "gesperrt"));
        } else {
            boolean unlock = !"LOCK".equals(step.action());
            boolean changed = releaseService.setUnlocked(courseId, module.getId(), unlock);
            done.add(module.getTitle() + (unlock ? " freigegeben" : " gesperrt") + (changed ? "" : " (war es schon)"));
        }
        if (step.deadline() != null && !step.deadline().isBlank()) {
            LocalDateTime deadline = "-".equals(step.deadline().strip()) ? null : deadline(step.deadline());
            releaseService.setDeadline(courseId, module.getId(), deadline);
            done.add(deadline == null ? "Frist entfernt" : "Frist " + deadline.toLocalTime());
        }
        return String.join(", ", done);
    }

    /** „HH:mm“ = heute zu dieser Uhrzeit, sonst ISO-Zeitpunkt. */
    static LocalDateTime deadline(String value) {
        try {
            String v = value.strip();
            if (v.contains("T")) return LocalDateTime.parse(v);
            return LocalDateTime.of(LocalDate.now(), LocalTime.parse(v));
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Ungültige Frist „" + value + "“ (z. B. 10:30).");
        }
    }

    // ------------------------------------------------------------------ Prüfen und Anzeigen

    private void apply(Lesson lesson, LessonUpdate update) {
        if (update.title() != null || lesson.getTitle() == null) {
            lesson.setTitle(trimTitle(update.title() == null || update.title().isBlank() ? "Neue Stunde" : update.title()));
        }
        if (update.courseId() != null) {
            // 0 = ohne Kurs (Vorlage)
            if (update.courseId() == 0) {
                lesson.setCourseId(null);
            } else {
                courseService.get(update.courseId());
                lesson.setCourseId(update.courseId());
            }
        }
        if (update.date() != null) lesson.setDate(update.date());
        if (update.restoreReleases() != null) lesson.setRestoreReleases(update.restoreReleases());
        if (update.steps() != null) lesson.setSteps(objectMapper.writeValueAsString(validate(update.steps())));
        lesson.setUpdatedAt(LocalDateTime.now());
    }

    /** Prüft die Schritte (bekannte Typen, nötige Angaben, Längen) und vergibt fehlende IDs. */
    List<LessonStep> validate(List<LessonStep> steps) {
        if (steps.size() > MAX_STEPS) throw ApiException.badRequest("Höchstens " + MAX_STEPS + " Schritte.");
        List<LessonStep> result = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            LessonStep s = steps.get(i);
            String where = "Schritt " + (i + 1) + ": ";
            if (s == null || s.type() == null) throw ApiException.badRequest(where + "Art fehlt.");
            if (s.minutes() != null && (s.minutes() < 0 || s.minutes() > 240)) throw ApiException.badRequest(where + "Dauer 0 bis 240 Minuten.");
            if (s.text() != null && s.text().length() > MAX_TEXT) throw ApiException.badRequest(where + "Text zu lang.");
            if (s.title() != null && s.title().length() > 200) throw ApiException.badRequest(where + "Titel zu lang.");
            switch (s.type()) {
                case RELEASE -> {
                    if (s.moduleSlug() == null || s.moduleSlug().isBlank()) throw ApiException.badRequest(where + "Modul fehlt.");
                    contentService.module(s.moduleSlug());
                    if (s.taskSlug() != null && !s.taskSlug().isBlank()) {
                        contentService.publishedTask(s.moduleSlug(), s.taskSlug());
                        if (s.action() != null && !Set.of("SHOW", "HIDE", "MODULE").contains(s.action())) {
                            throw ApiException.badRequest(where + "Aufgabe: SHOW, HIDE oder MODULE.");
                        }
                    } else if (s.action() != null && !Set.of("UNLOCK", "LOCK").contains(s.action())) {
                        throw ApiException.badRequest(where + "Modul: UNLOCK oder LOCK.");
                    }
                    if (s.deadline() != null && !s.deadline().isBlank() && !"-".equals(s.deadline().strip())) deadline(s.deadline());
                }
                case QUIZ -> {
                    if (s.taskIds() == null || s.taskIds().isEmpty()) throw ApiException.badRequest(where + "Fragen für das Quiz fehlen.");
                }
                case EVALUATION -> {
                    if (s.roundId() == null) throw ApiException.badRequest(where + "Bewertungsrunde fehlt.");
                    if (s.action() != null && !Set.of("OPEN", "CLOSED").contains(s.action())) {
                        throw ApiException.badRequest(where + "OPEN oder CLOSED.");
                    }
                }
                case ANNOUNCEMENT -> {
                    if (s.displayMode() != null) {
                        try {
                            AnnouncementDisplayMode.valueOf(s.displayMode());
                        } catch (IllegalArgumentException e) {
                            throw ApiException.badRequest(where + "Unbekannte Anzeige.");
                        }
                    }
                }
                case WORKSHEET, NOTE -> {
                }
            }
            String id = s.id() == null || s.id().isBlank() ? UUID.randomUUID().toString().substring(0, 8) : s.id();
            result.add(new LessonStep(id, s.type(), s.title(), s.minutes(), s.moduleSlug(), s.taskSlug(), s.action(),
                    s.deadline(), s.taskIds(), s.roundId(), s.worksheetId(), s.text(), s.displayMode()));
        }
        return result;
    }

    /** Lesbare Beschreibung eines Schritts. */
    String label(LessonStep s) {
        if (s.title() != null && !s.title().isBlank()) return s.title();
        try {
            return switch (s.type()) {
                case NOTE -> "Notiz";
                case WORKSHEET -> s.worksheetId() == null ? "Aufgabenblatt" : "Aufgabenblatt „" + worksheetService.title(s.worksheetId()) + "“";
                case ANNOUNCEMENT -> s.text() == null || s.text().isBlank() ? "Live-Mitteilung ausblenden" : "Live-Mitteilung senden";
                case EVALUATION -> ("CLOSED".equals(s.action()) ? "Bewertungsrunde schließen" : "Bewertungsrunde öffnen")
                        + (s.roundId() == null ? "" : ": " + evaluationService.get(s.roundId()).title());
                case QUIZ -> {
                    int n = s.taskIds() == null ? 0 : s.taskIds().size();
                    yield "Quiz-Runde starten (" + n + (n == 1 ? " Frage)" : " Fragen)");
                }
                case RELEASE -> {
                    Module module = contentService.module(s.moduleSlug());
                    if (s.taskSlug() != null && !s.taskSlug().isBlank()) {
                        String task = contentService.publishedTask(s.moduleSlug(), s.taskSlug()).version().getTitle();
                        yield ("HIDE".equals(s.action()) ? "Aufgabe sperren: " : "MODULE".equals(s.action()) ? "Aufgabe wie Modul: " : "Aufgabe freigeben: ") + task;
                    }
                    yield ("LOCK".equals(s.action()) ? "Modul sperren: " : "Modul freigeben: ") + module.getTitle()
                            + (s.deadline() != null && !s.deadline().isBlank() ? " (Frist " + s.deadline() + ")" : "");
                }
            };
        } catch (ApiException e) {
            return s.type().name() + " (nicht mehr vorhanden)";
        }
    }

    private LessonDetail detail(Lesson lesson) {
        List<LessonStep> steps = steps(lesson);
        String courseName = lesson.getCourseId() == null ? null : courseService.get(lesson.getCourseId()).getName();
        return new LessonDetail(lesson.getId(), lesson.getCourseId(), courseName, lesson.getTitle(), lesson.getDate(),
                lesson.getStatus(), lesson.getCurrentStep(), lesson.isRestoreReleases(), steps,
                steps.stream().map(this::label).toList(), log(lesson), lesson.getStartedAt(), lesson.getStepStartedAt(),
                lesson.getFinishedAt());
    }

    private LessonSummary summary(Lesson l) {
        String courseName = l.getCourseId() == null ? null : courseService.get(l.getCourseId()).getName();
        return new LessonSummary(l.getId(), l.getCourseId(), courseName, l.getTitle(), l.getDate(), steps(l).size(),
                l.getStatus(), l.getCurrentStep(), l.getPosition(), l.getUpdatedAt());
    }

    List<LessonStep> steps(Lesson lesson) {
        return objectMapper.readValue(lesson.getSteps(), new TypeReference<List<LessonStep>>() {
        });
    }

    private List<StepResult> log(Lesson lesson) {
        return new ArrayList<>(objectMapper.readValue(lesson.getRunLog(), new TypeReference<List<StepResult>>() {
        }));
    }

    private Lesson find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Ablauf nicht gefunden."));
    }

    private Lesson locked(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Ablauf nicht gefunden."));
    }

    private static String trimTitle(String title) {
        String t = title.strip();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> work.get());
    }
}

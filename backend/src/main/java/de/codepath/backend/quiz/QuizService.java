package de.codepath.backend.quiz;

import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.engine.Cfg;
import de.codepath.backend.engine.TaskType;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.CourseService;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.events.RealtimeUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Quiz-Runde am Beamer (Kahoot-Stil): Die Lehrkraft startet eine Runde aus Auswahl-Aufgaben, Schüler
 * antworten am eigenen Gerät, der Beamer zeigt eine anonyme Verteilung und danach die Auflösung.
 * Die Runde vergibt keine Punkte und zählt nicht als Versuch – sie ist ein Spiel für die ganze Klasse.
 */
@Service
@RequiredArgsConstructor
public class QuizService {

    public static final int MAX_QUESTIONS = 30;

    private final QuizSessionRepository sessions;
    private final QuizAnswerRepository answers;
    private final ContentService contentService;
    private final UserService userService;
    private final CourseService courseService;
    private final RealtimeUpdateService realtime;

    public record Candidate(Long taskId, String moduleSlug, String moduleTitle, String title, String question) {
    }

    public record StartRequest(String title, Long courseId, List<Long> taskIds) {
    }

    public record Option(String id, String label, String text) {
    }

    /**
     * Sicht auf die laufende Runde. Lehrkräfte bekommen Verteilung und Lösung immer; Schüler erst bei
     * der Auflösung, und nie, wer was geantwortet hat.
     */
    public record QuizView(Long id, String title, String courseName, QuizSession.Phase phase, int index, int total,
                           String question, boolean multiple, List<Option> options, int answered, int participants,
                           List<String> correct, Map<String, Integer> distribution, List<String> myAnswer,
                           Boolean myAnswerCorrect, long myScore, List<QuestionResult> results) {
    }

    /** Anteil richtiger Antworten je Frage – für den Abschluss am Beamer. */
    public record QuestionResult(int index, String question, int answered, int correct) {
    }

    public record QuizState(QuizView quiz) {
    }

    // ---------------------------------------------------------------- Lehrkraft

    /** Alle Auswahl-Aufgaben, die als Quizfrage taugen. */
    @Transactional(readOnly = true)
    public List<Candidate> candidates() {
        return contentService.allPublishedTasks().stream()
                .filter(t -> t.type() == TaskType.MULTIPLE_CHOICE)
                .sorted(Comparator.comparing((PublishedTask t) -> t.module().getSortOrder() == null ? 0 : t.module().getSortOrder())
                        .thenComparing(t -> t.task().getSortOrder() == null ? 0 : t.task().getSortOrder()))
                .map(t -> new Candidate(t.taskId(), t.module().getSlug(), t.module().getTitle(), t.version().getTitle(), question(t)))
                .toList();
    }

    @Transactional
    public QuizState start(User teacher, StartRequest request) {
        List<Long> taskIds = request.taskIds() == null ? List.of() : request.taskIds().stream().distinct().toList();
        if (taskIds.isEmpty()) throw ApiException.badRequest("Wähle mindestens eine Frage aus.");
        if (taskIds.size() > MAX_QUESTIONS) throw ApiException.badRequest("Höchstens " + MAX_QUESTIONS + " Fragen je Runde.");
        for (Long taskId : taskIds) {
            if (contentService.publishedTask(taskId).type() != TaskType.MULTIPLE_CHOICE) {
                throw ApiException.badRequest("Im Quiz gehen nur Auswahl-Aufgaben.");
            }
        }
        if (request.courseId() != null) courseService.get(request.courseId());
        if (sessions.findFirstByPhaseNot(QuizSession.Phase.FINISHED).isPresent()) {
            throw ApiException.conflict("Es läuft schon eine Quiz-Runde. Beende sie zuerst.");
        }
        QuizSession session = new QuizSession();
        session.setTitle(request.title() == null || request.title().isBlank() ? "Quiz" : request.title().strip());
        session.setCourseId(request.courseId());
        session.setCreatedBy(teacher.getId());
        session.setTaskIdList(taskIds);
        try {
            sessions.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("Es läuft schon eine Quiz-Runde. Beende sie zuerst.");
        }
        changed(session);
        return new QuizState(view(session, null, true));
    }

    @Transactional(readOnly = true)
    public QuizState teacherView() {
        return new QuizState(sessions.findFirstByPhaseNot(QuizSession.Phase.FINISHED).map(s -> view(s, null, true)).orElse(null));
    }

    /** Nächste Frage zeigen (aus der Lobby oder nach der Auflösung); nach der letzten ist die Runde vorbei. */
    @Transactional
    public QuizState next() {
        QuizSession session = active();
        if (session.getPhase() == QuizSession.Phase.QUESTION) {
            throw ApiException.conflict("Erst auflösen, dann die nächste Frage.");
        }
        if (session.getCurrentIndex() + 1 >= session.taskIdList().size()) {
            return finish();
        }
        session.setCurrentIndex(session.getCurrentIndex() + 1);
        session.setPhase(QuizSession.Phase.QUESTION);
        session.setQuestionStartedAt(LocalDateTime.now());
        changed(session);
        return new QuizState(view(session, null, true));
    }

    @Transactional
    public QuizState reveal() {
        QuizSession session = active();
        if (session.getPhase() != QuizSession.Phase.QUESTION) throw ApiException.conflict("Gerade ist keine Frage offen.");
        session.setPhase(QuizSession.Phase.REVEAL);
        changed(session);
        return new QuizState(view(session, null, true));
    }

    @Transactional
    public QuizState finish() {
        QuizSession session = active();
        session.setPhase(QuizSession.Phase.FINISHED);
        session.setFinishedAt(LocalDateTime.now());
        changed(session);
        // Abschluss mit Ergebnissen je Frage, solange die Lehrkraft die Seite offen hat
        return new QuizState(view(session, null, true));
    }

    // ---------------------------------------------------------------- Schüler

    @Transactional(readOnly = true)
    public QuizState studentView(User user) {
        return new QuizState(sessions.findFirstByPhaseNot(QuizSession.Phase.FINISHED)
                .filter(s -> mayJoin(user, s))
                .map(s -> view(s, user, false))
                .orElse(null));
    }

    /** Antwort auf die aktuelle Frage; bis zur Auflösung darf man sie ändern. Keine Punkte, kein Versuch. */
    @Transactional
    public QuizState answer(User user, int index, List<String> selected) {
        if (user.isTeacher()) throw ApiException.badRequest("Lehrkräfte spielen nicht mit.");
        QuizSession session = sessions.findFirstByPhaseNot(QuizSession.Phase.FINISHED)
                .filter(s -> mayJoin(user, s))
                .orElseThrow(() -> ApiException.notFound("Gerade läuft kein Quiz."));
        if (session.getPhase() != QuizSession.Phase.QUESTION || session.getCurrentIndex() != index) {
            throw ApiException.conflict("Diese Frage ist schon vorbei.");
        }
        PublishedTask task = contentService.publishedTask(session.taskIdList().get(index));
        Map<String, Object> config = task.spec().config();
        Set<String> valid = new HashSet<>();
        Cfg.objects(config, "options").forEach(o -> valid.add(Cfg.string(o, "id")));
        List<String> chosen = selected == null ? List.of() : selected.stream().filter(valid::contains).distinct().sorted().toList();
        if (chosen.isEmpty()) throw ApiException.badRequest("Bitte wähle eine Antwort.");
        if (!Cfg.bool(config, "multiple") && chosen.size() > 1) throw ApiException.badRequest("Hier ist genau eine Antwort richtig.");

        QuizAnswer answer = answers.findBySessionIdAndUserIdAndQuestionIndex(session.getId(), user.getId(), index)
                .orElseGet(QuizAnswer::new);
        answer.setSessionId(session.getId());
        answer.setUserId(user.getId());
        answer.setQuestionIndex(index);
        answer.setSelected(String.join(",", chosen));
        answer.setCorrect(new HashSet<>(chosen).equals(new HashSet<>(Cfg.strings(config, "correct"))));
        answer.setAnsweredAt(LocalDateTime.now());
        answers.save(answer);

        // Nur die Lehrkraft (und der Beamer in ihrem Browser) erfährt, wie viele schon geantwortet haben
        long answered = answers.findBySessionIdAndQuestionIndex(session.getId(), index).size();
        realtime.publishToTeachers(RealtimeUpdateService.QUIZ, Map.of("answered", answered, "timestamp", System.currentTimeMillis()));
        return new QuizState(view(session, user, false));
    }

    // ---------------------------------------------------------------- intern

    private QuizSession active() {
        return sessions.findFirstByPhaseNot(QuizSession.Phase.FINISHED)
                .orElseThrow(() -> ApiException.notFound("Gerade läuft kein Quiz."));
    }

    private boolean mayJoin(User user, QuizSession session) {
        if (user.isTeacher()) return true;
        if (session.getCourseId() == null) return true;
        return userService.courseOf(user.getId()).map(Course::getId).filter(session.getCourseId()::equals).isPresent();
    }

    /** Alle Geräte laden neu; das Ereignis enthält keine Antworten oder Lösungen. */
    private void changed(QuizSession session) {
        realtime.publishToAll(RealtimeUpdateService.QUIZ, Map.of(
                "phase", session.getPhase().name(), "index", session.getCurrentIndex(), "timestamp", System.currentTimeMillis()));
    }

    private static String question(PublishedTask task) {
        String assignment = task.version().getAssignment();
        return assignment != null && !assignment.isBlank() ? assignment : task.version().getTitle();
    }

    private QuizView view(QuizSession session, User user, boolean teacher) {
        List<Long> taskIds = session.taskIdList();
        int index = session.getCurrentIndex();
        boolean showQuestion = index >= 0 && index < taskIds.size() && session.getPhase() != QuizSession.Phase.LOBBY;
        boolean revealed = session.getPhase() == QuizSession.Phase.REVEAL || session.getPhase() == QuizSession.Phase.FINISHED;
        List<QuizAnswer> all = answers.findBySessionId(session.getId());

        String question = null;
        boolean multiple = false;
        List<Option> options = List.of();
        List<String> correct = null;
        Map<String, Integer> distribution = null;
        int answered = 0;
        List<String> myAnswer = null;
        Boolean myAnswerCorrect = null;
        if (showQuestion) {
            PublishedTask task = contentService.publishedTask(taskIds.get(index));
            Map<String, Object> config = task.spec().config();
            question = question(task);
            multiple = Cfg.bool(config, "multiple");
            List<Option> list = new ArrayList<>();
            List<Map<String, Object>> raw = Cfg.objects(config, "options");
            for (int i = 0; i < raw.size(); i++) {
                list.add(new Option(Cfg.string(raw.get(i), "id"), String.valueOf((char) ('A' + i)), Cfg.string(raw.get(i), "text", "")));
            }
            options = list;
            List<QuizAnswer> current = all.stream().filter(a -> a.getQuestionIndex() == index).toList();
            answered = current.size();
            if (teacher || revealed) {
                correct = Cfg.strings(config, "correct");
                Map<String, Integer> counts = new LinkedHashMap<>();
                list.forEach(o -> counts.put(o.id(), 0));
                current.forEach(a -> a.selectedIds().forEach(id -> counts.merge(id, 1, Integer::sum)));
                distribution = counts;
            }
            if (user != null) {
                Optional<QuizAnswer> mine = current.stream().filter(a -> a.getUserId().equals(user.getId())).findFirst();
                myAnswer = mine.map(QuizAnswer::selectedIds).orElse(null);
                if (revealed) myAnswerCorrect = mine.map(QuizAnswer::isCorrect).orElse(false);
            }
        }
        // Eigene Punkte zählen nur abgeschlossene Fragen, damit sie vor der Auflösung nichts verraten
        long myScore = user == null ? 0 : all.stream()
                .filter(a -> a.getUserId().equals(user.getId()) && a.isCorrect())
                .filter(a -> a.getQuestionIndex() < index || (a.getQuestionIndex() == index && revealed))
                .count();
        List<QuestionResult> results = null;
        if (teacher && session.getPhase() == QuizSession.Phase.FINISHED) {
            results = new ArrayList<>();
            for (int i = 0; i < taskIds.size(); i++) {
                int q = i;
                List<QuizAnswer> forQuestion = all.stream().filter(a -> a.getQuestionIndex() == q).toList();
                results.add(new QuestionResult(i, question(contentService.publishedTask(taskIds.get(i))),
                        forQuestion.size(), (int) forQuestion.stream().filter(QuizAnswer::isCorrect).count()));
            }
        }
        int participants = (int) all.stream().map(QuizAnswer::getUserId).distinct().count();
        String courseName = session.getCourseId() == null ? null : courseService.get(session.getCourseId()).getName();
        return new QuizView(session.getId(), session.getTitle(), courseName, session.getPhase(), index, taskIds.size(),
                question, multiple, options, answered, participants, correct, distribution, myAnswer, myAnswerCorrect,
                myScore, results);
    }
}

package de.codepath.backend.evaluation;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.CourseService;
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

/**
 * Bewertungsrunden: Schülerinnen und Schüler schätzen sich selbst ein und bewerten den Unterricht,
 * die Lehrkraft bewertet jede Person. Ob die Schüler die Bewertung der Lehrkraft sehen, legt die Runde fest.
 * Bei anonymer Unterrichtsbewertung bekommt die Lehrkraft nie Namen zu den Antworten zu sehen.
 */
@Service
@RequiredArgsConstructor
public class EvaluationService {

    static final int MAX_TITLE = 200;
    static final int MAX_INTRO = 5000;
    private static final Set<RoundStatus> VISIBLE_TO_STUDENTS = EnumSet.of(RoundStatus.OPEN, RoundStatus.CLOSED);

    private final EvaluationRoundRepository rounds;
    private final EvaluationResponseRepository responses;
    private final EvaluationTemplateService templates;
    private final CourseService courseService;
    private final UserService userService;
    private final AuditService auditService;

    // ------------------------------------------------------------------ Sichten

    public record RoundRequest(Long courseId, String title, String intro, List<EvaluationCriterion> selfCriteria,
                               List<EvaluationCriterion> lessonCriteria, List<EvaluationCriterion> teacherCriteria,
                               Boolean teacherUsesSelfCriteria, Boolean lessonAnonymous,
                               TeacherVisibility teacherVisibility, Boolean allowEdit, LocalDateTime closesAt) {
    }

    public record Counts(long students, long self, long lesson, long teacher) {
    }

    public record RoundAdmin(Long id, Long courseId, String courseName, String title, String intro, RoundStatus status,
                             boolean acceptingAnswers, List<EvaluationCriterion> selfCriteria, List<EvaluationCriterion> lessonCriteria,
                             List<EvaluationCriterion> teacherCriteria, boolean teacherUsesSelfCriteria, boolean lessonAnonymous,
                             TeacherVisibility teacherVisibility, boolean allowEdit, LocalDateTime closesAt,
                             LocalDateTime createdAt, Counts counts) {
    }

    public record Bucket(String value, String label, long count) {
    }

    public record TextAnswer(String author, String text) {
    }

    public record CriterionStats(String id, CriterionType type, String label, long answered, Double average,
                                 List<Bucket> distribution, List<TextAnswer> texts) {
    }

    public record PartStats(long responses, List<CriterionStats> criteria) {
    }

    /** Eine Zeile der Klassenliste. Scores: Mittel der Skalen in Prozent (0 = niedrigste, 100 = höchste Stufe). */
    public record StudentRow(Long userId, String username, String displayName, LocalDateTime selfSubmittedAt,
                             boolean lessonSubmitted, LocalDateTime teacherRatedAt, Integer selfScore, Integer teacherScore,
                             Map<String, Object> selfAnswers, Map<String, Object> lessonAnswers,
                             Map<String, Object> teacherAnswers) {
    }

    /**
     * @param lessonAnswers alle Unterrichtsbewertungen ohne Namen und ohne zeitliche Reihenfolge (für den Export);
     *                      bei nicht anonymen Runden stehen sie zusätzlich bei der jeweiligen Person
     */
    public record RoundResults(RoundAdmin round, List<StudentRow> students, PartStats self, PartStats lesson,
                               PartStats teacher, List<Map<String, Object>> lessonAnswers) {
    }

    public record StudentEvaluation(Long userId, String username, String displayName,
                                    List<EvaluationCriterion> selfCriteria, Map<String, Object> selfAnswers, LocalDateTime selfSubmittedAt,
                                    List<EvaluationCriterion> lessonCriteria, boolean lessonSubmitted, Map<String, Object> lessonAnswers,
                                    List<EvaluationCriterion> teacherCriteria, Map<String, Object> teacherAnswers,
                                    LocalDateTime teacherRatedAt, String teacherRatedBy) {
    }

    public record EvaluationAnswers(Map<String, Object> answers) {
    }

    /** Was die Schülerin oder der Schüler von der Lehrerbewertung sieht. */
    public enum TeacherState {
        /** Die Runde hat keine Lehrerbewertung */
        NONE,
        /** Die Lehrkraft hält ihre Bewertung zurück */
        HIDDEN,
        /** Erst die eigene Selbsteinschätzung abschicken */
        AFTER_SELF,
        /** Freigegeben, aber noch nicht bewertet */
        PENDING,
        VISIBLE
    }

    public record MyPart(List<EvaluationCriterion> criteria, Map<String, Object> answers, LocalDateTime submittedAt, boolean canEdit) {
    }

    public record MyRound(Long id, String title, RoundStatus status, boolean acceptingAnswers, LocalDateTime closesAt,
                          boolean hasSelf, boolean selfDone, boolean hasLesson, boolean lessonDone, TeacherState teacherState) {
    }

    public record MyRoundDetail(Long id, String title, String intro, RoundStatus status, boolean acceptingAnswers,
                                LocalDateTime closesAt, boolean allowEdit, boolean lessonAnonymous, MyPart self,
                                MyPart lesson, TeacherState teacherState, MyPart teacher) {
    }

    // ------------------------------------------------------------------ Lehrkraft: Runden

    @Transactional(readOnly = true)
    public List<RoundAdmin> list(Long courseId) {
        List<EvaluationRound> list = courseId == null ? rounds.findAllByOrderByCreatedAtDesc()
                : rounds.findByCourseIdOrderByCreatedAtDesc(courseId);
        return list.stream().map(this::admin).toList();
    }

    @Transactional(readOnly = true)
    public RoundAdmin get(Long id) {
        return admin(round(id));
    }

    @Transactional
    public RoundAdmin create(RoundRequest request) {
        EvaluationRound round = new EvaluationRound();
        apply(round, request);
        rounds.save(round);
        auditService.log("EVALUATION_ROUND_CREATED", "evaluation:" + round.getId(), Map.of("title", round.getTitle()));
        return admin(round);
    }

    @Transactional
    public RoundAdmin update(Long id, RoundRequest request) {
        EvaluationRound round = round(id);
        apply(round, request);
        round.setUpdatedAt(LocalDateTime.now());
        return admin(round);
    }

    @Transactional
    public RoundAdmin setStatus(Long id, RoundStatus status) {
        if (status == null) throw ApiException.badRequest("Status: DRAFT, OPEN oder CLOSED");
        EvaluationRound round = round(id);
        if (status == RoundStatus.OPEN && round.getClosesAt() != null && !round.getClosesAt().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("Die Frist ist schon abgelaufen – bitte zuerst eine neue Frist setzen oder sie entfernen.");
        }
        round.setStatus(status);
        round.setUpdatedAt(LocalDateTime.now());
        auditService.log("EVALUATION_ROUND_" + status.name(), "evaluation:" + id, Map.of("title", round.getTitle()));
        return admin(round);
    }

    @Transactional
    public void delete(Long id) {
        EvaluationRound round = round(id);
        rounds.delete(round);
        auditService.log("EVALUATION_ROUND_DELETED", "evaluation:" + id, Map.of("title", round.getTitle()));
    }

    private void apply(EvaluationRound round, RoundRequest request) {
        if (request == null) throw ApiException.badRequest("Keine Angaben.");
        if (request.courseId() == null) throw ApiException.badRequest("Bitte einen Kurs wählen.");
        Course course = courseService.get(request.courseId());
        if (round.getId() == null && !course.isActive()) throw ApiException.badRequest("Der Kurs ist beendet.");
        if (request.title() == null || request.title().isBlank()) throw ApiException.badRequest("Die Runde braucht einen Titel.");
        String title = request.title().strip();
        if (title.length() > MAX_TITLE) throw ApiException.badRequest("Der Titel ist zu lang.");
        String intro = request.intro() == null || request.intro().isBlank() ? null : request.intro().strip();
        if (intro != null && intro.length() > MAX_INTRO) throw ApiException.badRequest("Die Einleitung ist zu lang.");

        List<EvaluationCriterion> self = Criteria.normalize(emptyToNull(request.selfCriteria()));
        List<EvaluationCriterion> lesson = Criteria.normalize(emptyToNull(request.lessonCriteria()));
        boolean usesSelf = Boolean.TRUE.equals(request.teacherUsesSelfCriteria());
        if (usesSelf && self == null) {
            throw ApiException.badRequest("Die Lehrkraft kann nur dann nach den Kriterien der Selbsteinschätzung bewerten, wenn es eine gibt.");
        }
        List<EvaluationCriterion> teacher = usesSelf ? null : Criteria.normalize(emptyToNull(request.teacherCriteria()));
        if (self == null && lesson == null && teacher == null && !usesSelf) {
            throw ApiException.badRequest("Die Runde braucht mindestens einen Bogen.");
        }

        round.setCourseId(course.getId());
        round.setTitle(title);
        round.setIntro(intro);
        round.setSelfCriteria(templates.toRaw(self));
        round.setLessonCriteria(templates.toRaw(lesson));
        round.setTeacherCriteria(templates.toRaw(teacher));
        round.setTeacherUsesSelfCriteria(usesSelf);
        round.setLessonAnonymous(request.lessonAnonymous() == null || request.lessonAnonymous());
        round.setTeacherVisibility(request.teacherVisibility() == null ? TeacherVisibility.HIDDEN : request.teacherVisibility());
        round.setAllowEdit(request.allowEdit() == null || request.allowEdit());
        round.setClosesAt(request.closesAt());
    }

    private static List<EvaluationCriterion> emptyToNull(List<EvaluationCriterion> criteria) {
        return criteria == null || criteria.isEmpty() ? null : criteria;
    }

    // ------------------------------------------------------------------ Lehrkraft: Ergebnisse und Bewertung

    @Transactional(readOnly = true)
    public RoundResults results(Long id) {
        EvaluationRound round = round(id);
        List<EvaluationResponse> all = responses.findByRoundId(id);
        Map<EvaluationPart, Map<Long, EvaluationResponse>> byPart = new EnumMap<>(EvaluationPart.class);
        for (EvaluationResponse r : all) {
            byPart.computeIfAbsent(r.getPart(), p -> new HashMap<>()).put(r.getSubjectId(), r);
        }
        Map<Long, User> users = new HashMap<>();
        Function<Long, User> user = uid -> users.computeIfAbsent(uid, k -> userService.byId(k).orElse(null));

        List<EvaluationCriterion> self = criteria(round, EvaluationPart.SELF);
        List<EvaluationCriterion> lesson = criteria(round, EvaluationPart.LESSON);
        List<EvaluationCriterion> teacher = criteria(round, EvaluationPart.TEACHER);
        Map<Long, EvaluationResponse> selfResponses = byPart.getOrDefault(EvaluationPart.SELF, Map.of());
        Map<Long, EvaluationResponse> lessonResponses = byPart.getOrDefault(EvaluationPart.LESSON, Map.of());
        Map<Long, EvaluationResponse> teacherResponses = byPart.getOrDefault(EvaluationPart.TEACHER, Map.of());

        List<StudentRow> students = students(round).stream().map(s -> {
            EvaluationResponse sr = selfResponses.get(s.getId());
            EvaluationResponse tr = teacherResponses.get(s.getId());
            EvaluationResponse lr = round.isLessonAnonymous() ? null : lessonResponses.get(s.getId());
            return new StudentRow(s.getId(), s.getUsername(), s.getVisibleName(),
                    sr == null ? null : sr.getSubmittedAt(), lessonResponses.containsKey(s.getId()),
                    tr == null ? null : tr.getUpdatedAt(),
                    sr == null ? null : score(self, sr.getAnswers()), tr == null ? null : score(teacher, tr.getAnswers()),
                    sr == null ? null : sr.getAnswers(), lr == null ? null : lr.getAnswers(), tr == null ? null : tr.getAnswers());
        }).toList();
        // Zufällige, aber stabile Reihenfolge: nichts lässt sich über die Abgabezeit zuordnen
        List<Map<String, Object>> lessonAnswers = lessonResponses.values().stream()
                .sorted(Comparator.comparing(r -> Long.hashCode(r.getId() * 0x9E3779B97F4A7C15L)))
                .map(EvaluationResponse::getAnswers).toList();

        Function<EvaluationResponse, String> named = r -> Optional.ofNullable(user.apply(r.getSubjectId()))
                .map(User::getVisibleName).orElse("gelöscht");
        return new RoundResults(admin(round), students,
                stats(self, selfResponses.values(), named),
                stats(lesson, lessonResponses.values(), round.isLessonAnonymous() ? null : named),
                stats(teacher, teacherResponses.values(), named), lessonAnswers);
    }

    @Transactional(readOnly = true)
    public StudentEvaluation student(Long roundId, Long userId) {
        EvaluationRound round = round(roundId);
        User student = member(round, userId);
        return studentView(round, student);
    }

    @Transactional
    public StudentEvaluation rate(Long roundId, Long userId, EvaluationAnswers request, User teacher) {
        EvaluationRound round = round(roundId);
        User student = member(round, userId);
        List<EvaluationCriterion> criteria = criteria(round, EvaluationPart.TEACHER);
        if (criteria == null) throw ApiException.badRequest("Diese Runde hat keine Bewertung durch die Lehrkraft.");
        Map<String, Object> answers = Criteria.answers(criteria, request == null ? null : request.answers());
        save(round, EvaluationPart.TEACHER, student.getId(), teacher.getId(), answers);
        return studentView(round, student);
    }

    private StudentEvaluation studentView(EvaluationRound round, User student) {
        Map<EvaluationPart, EvaluationResponse> mine = responses.findByRoundIdAndSubjectId(round.getId(), student.getId())
                .stream().collect(Collectors.toMap(EvaluationResponse::getPart, r -> r));
        EvaluationResponse self = mine.get(EvaluationPart.SELF);
        EvaluationResponse lesson = mine.get(EvaluationPart.LESSON);
        EvaluationResponse teacher = mine.get(EvaluationPart.TEACHER);
        boolean showLesson = !round.isLessonAnonymous() && lesson != null;
        String ratedBy = teacher == null || teacher.getAuthorId() == null ? null
                : userService.byId(teacher.getAuthorId()).map(User::getVisibleName).orElse(null);
        return new StudentEvaluation(student.getId(), student.getUsername(), student.getVisibleName(),
                criteria(round, EvaluationPart.SELF), self == null ? null : self.getAnswers(), self == null ? null : self.getSubmittedAt(),
                criteria(round, EvaluationPart.LESSON), lesson != null, showLesson ? lesson.getAnswers() : null,
                criteria(round, EvaluationPart.TEACHER), teacher == null ? null : teacher.getAnswers(),
                teacher == null ? null : teacher.getUpdatedAt(), ratedBy);
    }

    private User member(EvaluationRound round, Long userId) {
        User student = userService.byId(userId).filter(u -> !u.isTeacher())
                .orElseThrow(() -> ApiException.notFound("Schülerin oder Schüler nicht gefunden."));
        boolean inCourse = userService.courseOf(userId).map(c -> c.getId().equals(round.getCourseId())).orElse(false);
        if (!inCourse) throw ApiException.notFound("Die Person gehört nicht zum Kurs dieser Runde.");
        return student;
    }

    // ------------------------------------------------------------------ Schüler

    @Transactional(readOnly = true)
    public List<MyRound> mine(User user) {
        Optional<Course> course = userService.courseOf(user.getId());
        if (course.isEmpty()) return List.of();
        return rounds.findByCourseIdAndStatusInOrderByCreatedAtDesc(course.get().getId(), VISIBLE_TO_STUDENTS).stream()
                .map(round -> {
                    MyRoundDetail d = detail(round, user);
                    return new MyRound(round.getId(), round.getTitle(), round.getStatus(), round.isAcceptingAnswers(),
                            round.getClosesAt(), d.self() != null, d.self() != null && d.self().submittedAt() != null,
                            d.lesson() != null, d.lesson() != null && d.lesson().submittedAt() != null, d.teacherState());
                }).toList();
    }

    @Transactional(readOnly = true)
    public MyRoundDetail myRound(User user, Long id) {
        return detail(visibleRound(user, id), user);
    }

    @Transactional
    public MyRoundDetail answer(User user, Long id, EvaluationPart part, EvaluationAnswers request) {
        if (user.isTeacher()) throw ApiException.badRequest("Bögen füllen nur Schülerinnen und Schüler aus.");
        if (part == EvaluationPart.TEACHER) throw ApiException.forbidden("Die Lehrerbewertung füllt nur die Lehrkraft aus.");
        EvaluationRound round = visibleRound(user, id);
        if (!round.isAcceptingAnswers()) throw ApiException.conflict("Die Runde ist geschlossen.");
        List<EvaluationCriterion> criteria = criteria(round, part);
        if (criteria == null) throw ApiException.badRequest("Diesen Bogen gibt es in der Runde nicht.");
        boolean exists = responses.findByRoundIdAndPartAndSubjectId(round.getId(), part, user.getId()).isPresent();
        if (exists && !round.isAllowEdit()) throw ApiException.conflict("Du hast diesen Bogen bereits abgeschickt.");
        Map<String, Object> answers = Criteria.answers(criteria, request == null ? null : request.answers());
        save(round, part, user.getId(), user.getId(), answers);
        return detail(round, user);
    }

    private EvaluationRound visibleRound(User user, Long id) {
        EvaluationRound round = rounds.findById(id).orElseThrow(() -> ApiException.notFound("Runde nicht gefunden."));
        boolean inCourse = userService.courseOf(user.getId()).map(c -> c.getId().equals(round.getCourseId())).orElse(false);
        if (!inCourse || !VISIBLE_TO_STUDENTS.contains(round.getStatus())) throw ApiException.notFound("Runde nicht gefunden.");
        return round;
    }

    private MyRoundDetail detail(EvaluationRound round, User user) {
        Map<EvaluationPart, EvaluationResponse> mine = responses.findByRoundIdAndSubjectId(round.getId(), user.getId())
                .stream().collect(Collectors.toMap(EvaluationResponse::getPart, r -> r));
        boolean open = round.isAcceptingAnswers();
        MyPart self = part(round, EvaluationPart.SELF, mine.get(EvaluationPart.SELF), open);
        MyPart lesson = part(round, EvaluationPart.LESSON, mine.get(EvaluationPart.LESSON), open);

        List<EvaluationCriterion> teacherCriteria = criteria(round, EvaluationPart.TEACHER);
        EvaluationResponse teacherResponse = mine.get(EvaluationPart.TEACHER);
        TeacherState state;
        if (teacherCriteria == null) state = TeacherState.NONE;
        else if (round.getTeacherVisibility() == TeacherVisibility.HIDDEN) state = TeacherState.HIDDEN;
        else if (round.getTeacherVisibility() == TeacherVisibility.AFTER_SELF && self != null && self.submittedAt() == null) {
            state = TeacherState.AFTER_SELF;
        } else if (teacherResponse == null) state = TeacherState.PENDING;
        else state = TeacherState.VISIBLE;
        MyPart teacher = state == TeacherState.VISIBLE
                ? new MyPart(teacherCriteria, teacherResponse.getAnswers(), teacherResponse.getUpdatedAt(), false) : null;

        return new MyRoundDetail(round.getId(), round.getTitle(), round.getIntro(), round.getStatus(), open,
                round.getClosesAt(), round.isAllowEdit(), round.isLessonAnonymous(), self, lesson, state, teacher);
    }

    private MyPart part(EvaluationRound round, EvaluationPart part, EvaluationResponse response, boolean open) {
        List<EvaluationCriterion> criteria = criteria(round, part);
        if (criteria == null) return null;
        boolean canEdit = open && (response == null || round.isAllowEdit());
        return new MyPart(criteria, response == null ? null : response.getAnswers(),
                response == null ? null : response.getSubmittedAt(), canEdit);
    }

    // ------------------------------------------------------------------ Hilfsmethoden

    private void save(EvaluationRound round, EvaluationPart part, Long subjectId, Long authorId, Map<String, Object> answers) {
        EvaluationResponse response = responses.findByRoundIdAndPartAndSubjectId(round.getId(), part, subjectId)
                .orElseGet(() -> {
                    EvaluationResponse created = new EvaluationResponse();
                    created.setRoundId(round.getId());
                    created.setPart(part);
                    created.setSubjectId(subjectId);
                    return created;
                });
        response.setAuthorId(authorId);
        response.setAnswers(new LinkedHashMap<>(answers));
        response.setUpdatedAt(LocalDateTime.now());
        responses.save(response);
    }

    EvaluationRound round(Long id) {
        return rounds.findById(id).orElseThrow(() -> ApiException.notFound("Runde nicht gefunden."));
    }

    /** Kriterien eines Teils; bei der Lehrerbewertung ggf. die der Selbsteinschätzung. null = Teil fehlt. */
    List<EvaluationCriterion> criteria(EvaluationRound round, EvaluationPart part) {
        return switch (part) {
            case SELF -> templates.toCriteria(round.getSelfCriteria());
            case LESSON -> templates.toCriteria(round.getLessonCriteria());
            case TEACHER -> templates.toCriteria(round.isTeacherUsesSelfCriteria() ? round.getSelfCriteria() : round.getTeacherCriteria());
        };
    }

    private List<User> students(EvaluationRound round) {
        return userService.memberIds(round.getCourseId()).stream()
                .map(userService::byId).flatMap(Optional::stream)
                .filter(u -> !u.isTeacher())
                .sorted(Comparator.comparing(User::getVisibleName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private RoundAdmin admin(EvaluationRound round) {
        String courseName;
        try {
            courseName = courseService.get(round.getCourseId()).getName();
        } catch (ApiException e) {
            courseName = "?";
        }
        Counts counts = new Counts(students(round).size(),
                responses.countByRoundIdAndPart(round.getId(), EvaluationPart.SELF),
                responses.countByRoundIdAndPart(round.getId(), EvaluationPart.LESSON),
                responses.countByRoundIdAndPart(round.getId(), EvaluationPart.TEACHER));
        return new RoundAdmin(round.getId(), round.getCourseId(), courseName, round.getTitle(), round.getIntro(),
                round.getStatus(), round.isAcceptingAnswers(),
                templates.toCriteria(round.getSelfCriteria()), templates.toCriteria(round.getLessonCriteria()),
                templates.toCriteria(round.getTeacherCriteria()), round.isTeacherUsesSelfCriteria(),
                round.isLessonAnonymous(), round.getTeacherVisibility(), round.isAllowEdit(), round.getClosesAt(),
                round.getCreatedAt(), counts);
    }

    /** Mittel aller beantworteten Skalen, je Skala auf 0–100 % umgerechnet. */
    static Integer score(List<EvaluationCriterion> criteria, Map<String, Object> answers) {
        if (criteria == null || answers == null) return null;
        double sum = 0;
        int n = 0;
        for (EvaluationCriterion c : criteria) {
            if (c.type() != CriterionType.SCALE || !(answers.get(c.id()) instanceof Number v)) continue;
            sum += (v.doubleValue() - c.min()) / (c.max() - c.min());
            n++;
        }
        return n == 0 ? null : (int) Math.round(sum / n * 100);
    }

    /** @param author Name zur Antwort; null = anonym (dann auch keine zeitliche Reihenfolge, die Rückschlüsse erlaubt) */
    private static PartStats stats(List<EvaluationCriterion> criteria, Collection<EvaluationResponse> list,
                                   Function<EvaluationResponse, String> author) {
        if (criteria == null) return null;
        List<EvaluationResponse> sorted = list.stream().sorted(Comparator.comparing(EvaluationResponse::getSubmittedAt)).toList();
        List<CriterionStats> result = new ArrayList<>();
        for (EvaluationCriterion c : criteria) {
            if (!c.answerable()) {
                result.add(new CriterionStats(c.id(), c.type(), c.label(), 0, null, null, null));
                continue;
            }
            List<Object> values = new ArrayList<>();
            List<TextAnswer> texts = new ArrayList<>();
            for (EvaluationResponse r : sorted) {
                Object v = r.getAnswers() == null ? null : r.getAnswers().get(c.id());
                if (v == null) continue;
                values.add(v);
                if (c.type() == CriterionType.TEXT) {
                    texts.add(new TextAnswer(author == null ? null : author.apply(r), String.valueOf(v)));
                }
            }
            if (author == null) texts.sort(Comparator.comparing(TextAnswer::text, String.CASE_INSENSITIVE_ORDER));
            Double average = null;
            List<Bucket> distribution = null;
            switch (c.type()) {
                case SCALE -> {
                    distribution = new ArrayList<>();
                    double sum = 0;
                    int n = 0;
                    for (int step = c.min(); step <= c.max(); step++) {
                        final int s = step;
                        long count = values.stream().filter(v -> v instanceof Number num && num.intValue() == s).count();
                        String label = c.labels() != null ? c.labels().get(step - c.min()) : null;
                        distribution.add(new Bucket(String.valueOf(step), label, count));
                        sum += (double) step * count;
                        n += (int) count;
                    }
                    average = n == 0 ? null : Math.round(sum / n * 100) / 100.0;
                }
                case YES_NO -> distribution = List.of(
                        new Bucket("true", "Ja", values.stream().filter(Boolean.TRUE::equals).count()),
                        new Bucket("false", "Nein", values.stream().filter(Boolean.FALSE::equals).count()));
                case CHOICE -> {
                    distribution = new ArrayList<>();
                    for (String option : c.options()) {
                        long count = values.stream().filter(v -> v.equals(option)
                                || (v instanceof Collection<?> col && col.contains(option))).count();
                        distribution.add(new Bucket(option, option, count));
                    }
                }
                default -> {
                }
            }
            result.add(new CriterionStats(c.id(), c.type(), c.label(), values.size(), average, distribution,
                    c.type() == CriterionType.TEXT ? texts : null));
        }
        return new PartStats(list.size(), result);
    }
}

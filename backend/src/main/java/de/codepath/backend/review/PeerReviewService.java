package de.codepath.backend.review;

import de.codepath.backend.classroom.AccessPolicy;
import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.engine.Cfg;
import de.codepath.backend.engine.TaskType;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.progress.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Peer-Review für Praxisabgaben ({@code peerReview: true}): Wer selbst abgegeben hat, bewertet anonym
 * bis zu zwei Abgaben anderer aus dem eigenen Kurs nach derselben Rubrik. Das bringt keine Punkte und
 * ersetzt nicht die Bewertung der Lehrkraft – sie sieht die Peer-Bewertungen als Hilfe.
 */
@Service
@RequiredArgsConstructor
public class PeerReviewService {

    public static final int PER_PERSON = 2;
    static final int MAX_COMMENT = 1000;

    private final PeerReviewRepository peerReviews;
    private final PracticeReviewRepository practiceReviews;
    private final ProgressService progressService;
    private final ContentService contentService;
    private final UserService userService;
    private final AccessPolicy accessPolicy;

    /** Eine zugeteilte Abgabe – ohne Namen, ohne Bewertung der Lehrkraft. */
    public record Assignment(Long id, String content, String code, Map<String, Integer> scores, String comment,
                             boolean submitted) {
    }

    /** Eine erhaltene Peer-Bewertung, anonym. */
    public record Received(Map<String, Integer> scores, int total, String comment) {
    }

    public record PeerState(boolean enabled, boolean canReview, String reason, List<ReviewService.Criterion> rubric,
                            int maxPoints, List<Assignment> assignments, List<Received> received) {
    }

    /** Für die Lehrkraft: wer hat wie bewertet (zum Prüfen auf Fairness). */
    public record TeacherPeerReview(String reviewer, Map<String, Integer> scores, int total, String comment,
                                    LocalDateTime submittedAt) {
    }

    public record ScoreRequest(Map<String, Integer> scores, String comment) {
    }

    @Transactional(readOnly = true)
    public PeerState state(User user, String moduleSlug, String taskSlug) {
        PublishedTask task = task(user, moduleSlug, taskSlug);
        return state(user, task);
    }

    /** Teilt bis zu zwei Abgaben zu – bevorzugt solche, die bisher am wenigsten Rückmeldungen haben. */
    @Transactional
    public PeerState assign(User user, String moduleSlug, String taskSlug) {
        PublishedTask task = task(user, moduleSlug, taskSlug);
        PeerState current = state(user, task);
        if (!current.canReview()) throw ApiException.conflict(current.reason());
        List<PeerReview> mine = peerReviews.findByReviewerIdAndTaskIdOrderByIdAsc(user.getId(), task.taskId());
        Set<Long> already = new HashSet<>();
        mine.forEach(p -> already.add(p.getPracticeReviewId()));
        List<PracticeReview> pool = new ArrayList<>(classmatesSubmissions(user, task).stream()
                .filter(r -> !already.contains(r.getId())).toList());
        if (pool.isEmpty()) throw ApiException.conflict("Es gibt noch keine Abgaben anderer, die du bewerten kannst.");
        Map<Long, Integer> load = new HashMap<>();
        peerReviews.findByPracticeReviewIdIn(pool.stream().map(PracticeReview::getId).toList())
                .forEach(p -> load.merge(p.getPracticeReviewId(), 1, Integer::sum));
        Collections.shuffle(pool, new Random(user.getId() * 31 + task.taskId()));
        pool.sort(Comparator.comparingInt(r -> load.getOrDefault(r.getId(), 0)));
        for (PracticeReview review : pool.subList(0, Math.min(PER_PERSON - mine.size(), pool.size()))) {
            PeerReview peer = new PeerReview();
            peer.setPracticeReviewId(review.getId());
            peer.setReviewerId(user.getId());
            peer.setTaskId(task.taskId());
            peerReviews.save(peer);
        }
        return state(user, task);
    }

    @Transactional
    public PeerState submit(User user, Long assignmentId, ScoreRequest request) {
        PeerReview peer = peerReviews.findById(assignmentId)
                .filter(p -> p.getReviewerId().equals(user.getId()))
                .orElseThrow(() -> ApiException.notFound("Bewertung nicht gefunden."));
        PublishedTask task = contentService.publishedTask(peer.getTaskId());
        accessPolicy.requireTaskVisible(user, task);
        Map<String, Integer> scores = request == null || request.scores() == null ? Map.of() : request.scores();
        Map<String, Integer> given = new LinkedHashMap<>();
        for (ReviewService.Criterion c : ReviewService.rubric(task)) {
            Integer value = scores.get(c.id());
            if (value == null) throw ApiException.badRequest("Bitte bewerte „" + c.label() + "“.");
            if (value < 0 || value > c.points()) {
                throw ApiException.badRequest("Für „" + c.label() + "“ sind 0 bis " + c.points() + " Punkte möglich.");
            }
            given.put(c.id(), value);
        }
        String comment = request.comment() == null ? "" : request.comment().strip();
        if (comment.length() > MAX_COMMENT) throw ApiException.badRequest("Der Kommentar ist zu lang.");
        peer.setScores(given);
        peer.setComment(comment.isEmpty() ? null : comment);
        peer.setSubmittedAt(LocalDateTime.now());
        return state(user, task);
    }

    /** Peer-Bewertungen zu einer Abgabe für die Lehrkraft. */
    @Transactional(readOnly = true)
    public List<TeacherPeerReview> forSubmission(Long practiceReviewId) {
        return peerReviews.findByPracticeReviewId(practiceReviewId).stream()
                .filter(PeerReview::isSubmitted)
                .map(p -> new TeacherPeerReview(userService.byId(p.getReviewerId()).map(User::getVisibleName).orElse("?"),
                        p.getScores(), total(p), p.getComment(), p.getSubmittedAt()))
                .toList();
    }

    // ---------------------------------------------------------------- intern

    private PublishedTask task(User user, String moduleSlug, String taskSlug) {
        PublishedTask task = contentService.publishedTask(moduleSlug, taskSlug);
        accessPolicy.requireTaskVisible(user, task);
        return task;
    }

    private PeerState state(User user, PublishedTask task) {
        List<ReviewService.Criterion> rubric = ReviewService.rubric(task);
        int maxPoints = rubric.stream().mapToInt(ReviewService.Criterion::points).sum();
        boolean enabled = task.type() == TaskType.PRACTICE && Cfg.bool(task.spec().config(), "peerReview");
        if (!enabled) return new PeerState(false, false, null, rubric, maxPoints, List.of(), List.of());

        List<PeerReview> mine = peerReviews.findByReviewerIdAndTaskIdOrderByIdAsc(user.getId(), task.taskId());
        List<Assignment> assignments = new ArrayList<>();
        for (PeerReview peer : mine) {
            practiceReviews.findById(peer.getPracticeReviewId())
                    .flatMap(r -> progressService.attempt(r.getAttemptId()))
                    .ifPresent(attempt -> assignments.add(new Assignment(peer.getId(),
                            Cfg.string(attempt.answer(), "content", ""), Cfg.string(attempt.answer(), "code", ""),
                            peer.getScores(), peer.getComment(), peer.isSubmitted())));
        }

        List<Long> myReviewIds = ownSubmissions(user.getId(), task.taskId()).stream().map(PracticeReview::getId).toList();
        List<Received> received = myReviewIds.isEmpty() ? List.of() : peerReviews.findByPracticeReviewIdIn(myReviewIds).stream()
                .filter(PeerReview::isSubmitted)
                .map(p -> new Received(p.getScores(), total(p), p.getComment()))
                .toList();

        String reason = null;
        if (user.isTeacher()) reason = "Peer-Review ist für Schülerinnen und Schüler.";
        else if (myReviewIds.isEmpty()) reason = "Gib zuerst selbst ab – danach bewertest du zwei Abgaben anderer.";
        else if (mine.size() >= PER_PERSON) reason = "Du hast schon " + PER_PERSON + " Abgaben zugeteilt bekommen.";
        else if (classmatesSubmissions(user, task).stream().noneMatch(r -> mine.stream().noneMatch(p -> p.getPracticeReviewId().equals(r.getId())))) {
            reason = "Es gibt noch keine Abgaben anderer, die du bewerten kannst.";
        }
        return new PeerState(true, reason == null, reason, rubric, maxPoints, assignments, received);
    }

    private List<PracticeReview> ownSubmissions(Long userId, Long taskId) {
        List<Long> attemptIds = progressService.attempts(userId, taskId).stream().map(ProgressService.AttemptView::id).toList();
        return attemptIds.isEmpty() ? List.of() : practiceReviews.findByAttemptIdInOrderByIdDesc(attemptIds);
    }

    /** Die jeweils neueste Abgabe jedes anderen Kursmitglieds für diese Aufgabe. */
    private List<PracticeReview> classmatesSubmissions(User user, PublishedTask task) {
        Optional<Long> courseId = userService.courseOf(user.getId()).map(Course::getId);
        if (courseId.isEmpty()) return List.of();
        List<PracticeReview> result = new ArrayList<>();
        for (Long memberId : userService.memberIds(courseId.get())) {
            if (memberId.equals(user.getId())) continue;
            ownSubmissions(memberId, task.taskId()).stream().findFirst().ifPresent(result::add);
        }
        return result;
    }

    private static int total(PeerReview peer) {
        return peer.getScores() == null ? 0 : peer.getScores().values().stream().mapToInt(Integer::intValue).sum();
    }
}

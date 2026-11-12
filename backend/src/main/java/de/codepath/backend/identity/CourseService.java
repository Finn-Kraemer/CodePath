package de.codepath.backend.identity;

import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kurse mit Beitrittscode statt offener Registrierung. Die Lehrkraft kann Konten gesammelt anlegen und
 * einen Kurs beenden – dabei werden alle Schülerdaten des Kurses gelöscht (Datenschutz). Dazwischen gibt
 * es zwei Wege: <b>archivieren</b> (Schüler können sich nicht mehr anmelden, die Lehrkraft liest und
 * exportiert weiter, nach einer Frist wird gelöscht wie beim Beenden) und <b>übernehmen</b> (neuer Kurs
 * mit denselben Mitgliedern, z. B. ins nächste Schuljahr).
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    // Ohne leicht verwechselbare Zeichen (0/O, 1/I/l)
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String PASSWORD_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CourseRepository courseRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final SessionAdminService sessionAdminService;

    /** Standardfrist bis zur automatischen Löschung eines archivierten Kurses. */
    public static final int DEFAULT_ARCHIVE_MONTHS = 6;
    public static final int MAX_ARCHIVE_MONTHS = 36;

    /**
     * @param purgeAfter       archivierter Kurs: ab dann werden die Schülerdaten gelöscht
     * @param leaderboardSince Rangliste zählt nur Punkte ab diesem Zeitpunkt (null = alle)
     * @param successorId      übernommener Kurs: der Folgekurs
     */
    public record CourseSummary(Long id, String name, String joinCode, CourseStatus status, LocalDate endsOn, long members,
                                LocalDateTime archivedAt, LocalDateTime purgeAfter, LocalDateTime leaderboardSince,
                                Long successorId, String successorName) {
    }

    /**
     * Was beim Übernehmen in einen neuen Kurs mitkommt.
     *
     * @param keepProgress      Fortschritt, Versuche und Punkte behalten (sonst gelöscht)
     * @param keepReleases      Modul- und Aufgabenfreigaben übernehmen (ohne Fristen); sonst beginnt alles gesperrt
     * @param copyCodePaths     CodePaths des alten Kurses für den neuen kopieren
     * @param restartLeaderboard Rangliste zählt nur Punkte ab der Übernahme
     */
    public record PromoteOptions(String name, LocalDate endsOn, boolean keepProgress, boolean keepReleases,
                                 boolean copyCodePaths, boolean restartLeaderboard) {
    }

    /** Wird nach dem Umhängen der Mitgliedschaften veröffentlicht, damit andere Pakete nachziehen. */
    public record CoursePromotedEvent(Long sourceCourseId, Long targetCourseId, List<Long> userIds, PromoteOptions options) {
    }

    public record GeneratedAccount(String username, String password) {
    }

    /** Wird veröffentlicht, bevor die Schüler eines Kurses gelöscht werden. */
    public record CourseEndingEvent(Long courseId, List<Long> userIds) {
    }

    public List<CourseSummary> list() {
        return courseRepository.findAllByOrderByCreatedAtDesc().stream().map(this::summary).toList();
    }

    public Course get(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Kurs nicht gefunden."));
    }

    public List<Course> activeCourses() {
        return courseRepository.findAllByOrderByCreatedAtDesc().stream().filter(Course::isActive).toList();
    }

    public CourseSummary summary(Course course) {
        String successorName = course.getSuccessorId() == null ? null
                : courseRepository.findById(course.getSuccessorId()).map(Course::getName).orElse(null);
        return new CourseSummary(course.getId(), course.getName(), course.getJoinCode(), course.getStatus(),
                course.getEndsOn(), membershipRepository.countByCourseId(course.getId()), course.getArchivedAt(),
                course.getPurgeAfter(), course.getLeaderboardSince(), course.getSuccessorId(), successorName);
    }

    @Transactional
    public CourseSummary create(String name, LocalDate endsOn) {
        Course course = newCourse(name, endsOn);
        auditService.log("COURSE_CREATED", "course:" + course.getId(), Map.of("name", course.getName()));
        return summary(course);
    }

    private Course newCourse(String name, LocalDate endsOn) {
        if (name == null || name.isBlank()) throw ApiException.badRequest("Der Kurs braucht einen Namen.");
        if (name.strip().length() > 100) throw ApiException.badRequest("Der Kursname ist zu lang (höchstens 100 Zeichen).");
        Course course = new Course();
        course.setName(name.strip());
        course.setEndsOn(endsOn);
        course.setJoinCode(newJoinCode());
        courseRepository.save(course);
        events.publishEvent(new CourseCreatedEvent(course.getId()));
        return course;
    }

    /** Signal an andere Pakete, z. B. um Modulfreigaben anzulegen. */
    public record CourseCreatedEvent(Long courseId) {
    }

    @Transactional
    public CourseSummary regenerateJoinCode(Long courseId) {
        Course course = get(courseId);
        course.setJoinCode(newJoinCode());
        auditService.log("COURSE_JOIN_CODE_RENEWED", "course:" + courseId, Map.of());
        return summary(course);
    }

    /** Für die Registrierung: Kurs zum Code, nur wenn aktiv. */
    public Course activeCourseByJoinCode(String joinCode) {
        return courseRepository.findByJoinCodeIgnoreCase(joinCode == null ? "" : joinCode.strip())
                .filter(Course::isActive)
                .orElseThrow(() -> ApiException.badRequest("Der Beitrittscode ist ungültig oder der Kurs ist beendet bzw. archiviert."));
    }

    @Transactional
    public void join(User user, Course course) {
        membershipRepository.save(new Membership(user.getId(), course));
    }

    /**
     * Legt Konten gesammelt an. Benutzernamen entstehen aus dem Präfix und einer laufenden Nummer;
     * die Passwörter werden genau einmal angezeigt.
     */
    @Transactional
    public List<GeneratedAccount> createAccounts(Long courseId, String prefix, int count) {
        Course course = get(courseId);
        if (!course.isActive()) throw ApiException.badRequest("Konten lassen sich nur in einem aktiven Kurs anlegen.");
        if (count < 1 || count > 200) throw ApiException.badRequest("Zwischen 1 und 200 Konten auf einmal.");
        String base = (prefix == null || prefix.isBlank() ? "s" : prefix.strip().toLowerCase().replaceAll("[^a-z0-9]", ""));
        if (base.isEmpty()) base = "s";
        List<GeneratedAccount> accounts = new ArrayList<>();
        int number = 1;
        while (accounts.size() < count) {
            String username = base + "-" + String.format("%02d", number++);
            if (userRepository.existsByUsername(username)) continue;
            String password = randomString(PASSWORD_ALPHABET, 10);
            User user = new User();
            user.setUsername(username);
            user.setDisplayName(username);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(UserRole.STUDENT);
            userRepository.save(user);
            membershipRepository.save(new Membership(user.getId(), course));
            accounts.add(new GeneratedAccount(username, password));
        }
        auditService.log("ACCOUNTS_CREATED", "course:" + courseId, Map.of("count", count, "prefix", base));
        return accounts;
    }

    /**
     * Beendet den Kurs und löscht alle Schülerkonten des Kurses samt Fortschritt, Versuchen und Punkten.
     * Zeugnisse vorher exportieren.
     */
    @Transactional
    public int endCourse(Long courseId) {
        Course course = get(courseId);
        int deleted = deleteStudents(course);
        auditService.log("COURSE_ENDED", "course:" + courseId, Map.of("deletedAccounts", deleted));
        return deleted;
    }

    private int deleteStudents(Course course) {
        List<Long> userIds = membershipRepository.findUserIdsByCourseId(course.getId());
        events.publishEvent(new CourseEndingEvent(course.getId(), userIds));
        userRepository.deleteAllById(userIds);
        course.setStatus(CourseStatus.ENDED);
        course.setPurgeAfter(null);
        return userIds.size();
    }

    /**
     * Archiviert einen aktiven Kurs: Beitrittscode ungültig, Schüler können sich nicht mehr anmelden (laufende
     * Sitzungen enden), die Lehrkraft sieht und exportiert weiter alles. Nach {@code months} Monaten werden die
     * Daten gelöscht wie beim Beenden.
     */
    @Transactional
    public CourseSummary archive(Long courseId, Integer months) {
        Course course = get(courseId);
        if (!course.isActive()) throw ApiException.badRequest("Nur aktive Kurse lassen sich archivieren.");
        int keep = months == null ? DEFAULT_ARCHIVE_MONTHS : months;
        if (keep < 1 || keep > MAX_ARCHIVE_MONTHS) {
            throw ApiException.badRequest("Die Frist muss zwischen 1 und " + MAX_ARCHIVE_MONTHS + " Monaten liegen.");
        }
        LocalDateTime now = LocalDateTime.now();
        course.setStatus(CourseStatus.ARCHIVED);
        course.setArchivedAt(now);
        course.setPurgeAfter(now.plusMonths(keep));
        int sessions = logoutMembers(courseId);
        auditService.log("COURSE_ARCHIVED", "course:" + courseId,
                Map.of("months", keep, "purgeAfter", course.getPurgeAfter().toString(), "sessionsEnded", sessions));
        return summary(course);
    }

    /** Holt einen archivierten Kurs zurück: Anmeldung und Beitrittscode gelten wieder, keine Löschfrist mehr. */
    @Transactional
    public CourseSummary restore(Long courseId) {
        Course course = get(courseId);
        if (!course.isArchived()) throw ApiException.badRequest("Nur archivierte Kurse lassen sich wiederherstellen.");
        course.setStatus(CourseStatus.ACTIVE);
        course.setArchivedAt(null);
        course.setPurgeAfter(null);
        auditService.log("COURSE_RESTORED", "course:" + courseId, Map.of());
        return summary(course);
    }

    /** Löscht archivierte Kurse, deren Frist abgelaufen ist (Job, siehe {@link CourseArchiveJob}). */
    @Transactional
    public int purgeExpired(LocalDateTime now) {
        int count = 0;
        for (Course course : courseRepository.findByStatusAndPurgeAfterBefore(CourseStatus.ARCHIVED, now)) {
            int deleted = deleteStudents(course);
            auditService.log("COURSE_ARCHIVE_PURGED", "course:" + course.getId(), Map.of("deletedAccounts", deleted));
            count++;
        }
        return count;
    }

    /** Archivierte Kurse, die in den nächsten {@code days} Tagen gelöscht werden (Hinweis in der Oberfläche). */
    public List<CourseSummary> purgingSoon(int days) {
        LocalDateTime limit = LocalDateTime.now().plusDays(days);
        return courseRepository.findByStatusAndPurgeAfterBefore(CourseStatus.ARCHIVED, limit).stream().map(this::summary).toList();
    }

    /**
     * Übernimmt einen Kurs in einen neuen (z. B. „9a“ → „10a“): Die Mitglieder wechseln samt Konten in den
     * neuen Kurs, der alte gilt als beendet und verweist auf den neuen. Was mitkommt, steuern die Optionen;
     * die anderen Pakete ziehen über {@link CoursePromotedEvent} nach.
     */
    @Transactional
    public CourseSummary promote(Long courseId, PromoteOptions options) {
        Course source = get(courseId);
        if (!source.isReadable()) throw ApiException.badRequest("Ein beendeter Kurs lässt sich nicht mehr übernehmen.");
        Course target = newCourse(options.name(), options.endsOn());
        if (options.restartLeaderboard()) target.setLeaderboardSince(LocalDateTime.now());
        List<Long> userIds = membershipRepository.findUserIdsByCourseId(courseId);
        membershipRepository.moveAll(courseId, target.getId());
        source.setStatus(CourseStatus.ENDED);
        source.setPurgeAfter(null);
        source.setSuccessorId(target.getId());
        events.publishEvent(new CoursePromotedEvent(courseId, target.getId(), userIds, options));
        Map<String, Object> details = new java.util.HashMap<>();
        details.put("target", "course:" + target.getId());
        details.put("name", target.getName());
        details.put("members", userIds.size());
        details.put("keepProgress", options.keepProgress());
        details.put("keepReleases", options.keepReleases());
        details.put("copyCodePaths", options.copyCodePaths());
        details.put("restartLeaderboard", options.restartLeaderboard());
        auditService.log("COURSE_PROMOTED", "course:" + courseId, details);
        return summary(target);
    }

    private int logoutMembers(Long courseId) {
        int count = 0;
        for (Long userId : membershipRepository.findUserIdsByCourseId(courseId)) {
            var user = userRepository.findById(userId);
            if (user.isPresent()) count += sessionAdminService.logout(user.get().getUsername());
        }
        return count;
    }

    private String newJoinCode() {
        String code;
        do {
            code = randomString(CODE_ALPHABET, 6);
        } while (courseRepository.existsByJoinCode(code));
        return code;
    }

    static String randomString(String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return sb.toString();
    }
}

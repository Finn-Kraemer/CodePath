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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kurse mit Beitrittscode statt offener Registrierung. Die Lehrkraft kann Konten gesammelt anlegen
 * und einen Kurs beenden – dabei werden alle Schülerdaten des Kurses gelöscht (Datenschutz).
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

    public record CourseSummary(Long id, String name, String joinCode, CourseStatus status, LocalDate endsOn, long members) {
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
        return new CourseSummary(course.getId(), course.getName(), course.getJoinCode(), course.getStatus(),
                course.getEndsOn(), membershipRepository.countByCourseId(course.getId()));
    }

    @Transactional
    public CourseSummary create(String name, LocalDate endsOn) {
        if (name == null || name.isBlank()) throw ApiException.badRequest("Der Kurs braucht einen Namen.");
        Course course = new Course();
        course.setName(name.strip());
        course.setEndsOn(endsOn);
        course.setJoinCode(newJoinCode());
        courseRepository.save(course);
        events.publishEvent(new CourseCreatedEvent(course.getId()));
        auditService.log("COURSE_CREATED", "course:" + course.getId(), Map.of("name", course.getName()));
        return summary(course);
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
                .orElseThrow(() -> ApiException.badRequest("Der Beitrittscode ist ungültig oder der Kurs ist beendet."));
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
        List<Long> userIds = membershipRepository.findUserIdsByCourseId(courseId);
        events.publishEvent(new CourseEndingEvent(courseId, userIds));
        userRepository.deleteAllById(userIds);
        course.setStatus(CourseStatus.ENDED);
        auditService.log("COURSE_ENDED", "course:" + courseId, Map.of("deletedAccounts", userIds.size()));
        return userIds.size();
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

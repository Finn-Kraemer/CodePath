package de.codepath.backend.attendance;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.CourseService;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.platform.pdf.PdfFile;
import de.codepath.backend.platform.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Anwesenheit je Kurs und Termin. Ein neuer Termin trägt alle Mitglieder als anwesend ein – die Lehrkraft
 * klickt nur die Abweichungen an. Wer an dem Tag in CodePath gearbeitet hat (Abgaben), ist als Vorschlag
 * markiert; entscheiden tut die Lehrkraft. Nur Lehrkräfte sehen die Anwesenheit anderer.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceEntryRepository entryRepository;
    private final CourseService courseService;
    private final UserService userService;
    private final SettingsService settings;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;

    public record AttendanceSessionSummary(Long id, LocalDate date, String title, int present, int absent, int excused, int late) {
    }

    /**
     * @param activeThatDay an dem Tag Abgaben in CodePath (Vorschlag „anwesend“)
     */
    public record AttendanceEntryView(Long userId, String username, String displayName, AttendanceStatus status, String note,
                            boolean activeThatDay) {
    }

    public record AttendanceSessionDetail(AttendanceSessionSummary session, Long courseId, String courseName, List<AttendanceEntryView> entries) {
    }

    /**
     * @param attended PRESENT oder LATE
     * @param recorded Termine mit Eintrag für die Person
     */
    public record AttendanceRate(int attended, int recorded, int excused, int late, int absent) {
        @com.fasterxml.jackson.annotation.JsonProperty("text")
        public String text() {
            if (recorded == 0) return "Noch keine Termine erfasst";
            StringBuilder sb = new StringBuilder("an " + attended + " von " + recorded + (recorded == 1 ? " Termin" : " Terminen") + " teilgenommen");
            if (excused > 0) sb.append(", ").append(excused).append(" entschuldigt");
            return sb.toString();
        }
    }

    public record AttendanceStudentRow(Long userId, String username, String displayName, AttendanceRate rate) {
    }

    /** Matrix: Personen × Termine; cells[userId][sessionId] = Status. */
    public record AttendanceOverview(Long courseId, String courseName, List<AttendanceSessionSummary> sessions, List<AttendanceStudentRow> students,
                           Map<Long, Map<Long, AttendanceStatus>> cells) {
    }

    public record AttendancePersonView(String username, String displayName, AttendanceRate rate, List<AttendancePersonEntry> entries) {
    }

    public record AttendancePersonEntry(LocalDate date, String title, AttendanceStatus status, String note) {
    }

    public record AttendanceSettings(boolean onCertificate, boolean visibleToStudents) {
    }

    // ------------------------------------------------------------------ Termine

    @Transactional
    public AttendanceSessionDetail create(Long courseId, LocalDate date, String title) {
        Course course = courseService.get(courseId);
        if (!course.isReadable()) throw ApiException.badRequest("Der Kurs ist beendet.");
        AttendanceSession session = new AttendanceSession();
        session.setCourseId(courseId);
        session.setDate(date == null ? LocalDate.now() : date);
        session.setTitle(title(title));
        sessionRepository.save(session);
        for (Long userId : userService.memberIds(courseId)) {
            entryRepository.save(new AttendanceEntry(session.getId(), userId, AttendanceStatus.PRESENT));
        }
        auditService.log("ATTENDANCE_SESSION_CREATED", "course:" + courseId, Map.of("date", session.getDate().toString()));
        return detail(session.getId());
    }

    @Transactional
    public AttendanceSessionDetail update(Long sessionId, LocalDate date, String title) {
        AttendanceSession session = session(sessionId);
        if (date != null) session.setDate(date);
        session.setTitle(title(title));
        return detail(sessionId);
    }

    @Transactional
    public void delete(Long sessionId) {
        AttendanceSession session = session(sessionId);
        sessionRepository.delete(session);
        auditService.log("ATTENDANCE_SESSION_DELETED", "course:" + session.getCourseId(), Map.of("date", session.getDate().toString()));
    }

    @Transactional
    public AttendanceEntryView setEntry(Long sessionId, Long userId, AttendanceStatus status, String note) {
        AttendanceSession session = session(sessionId);
        if (status == null) throw ApiException.badRequest("Bitte einen Status angeben.");
        if (!userService.memberIds(session.getCourseId()).contains(userId)) {
            throw ApiException.notFound("Diese Person gehört nicht zum Kurs.");
        }
        if (note != null && note.length() > 500) throw ApiException.badRequest("Die Notiz ist zu lang (höchstens 500 Zeichen).");
        AttendanceEntry entry = entryRepository.findById(new AttendanceEntry.Key(sessionId, userId))
                .orElseGet(() -> new AttendanceEntry(sessionId, userId, status));
        entry.setStatus(status);
        entry.setNote(note == null || note.isBlank() ? null : note.strip());
        entryRepository.save(entry);
        User user = userService.byId(userId).orElseThrow();
        return new AttendanceEntryView(userId, user.getUsername(), user.getVisibleName(), entry.getStatus(), entry.getNote(),
                activeOn(session.getDate()).contains(userId));
    }

    @Transactional(readOnly = true)
    public AttendanceSessionDetail detail(Long sessionId) {
        AttendanceSession session = session(sessionId);
        Course course = courseService.get(session.getCourseId());
        Map<Long, AttendanceEntry> entries = new HashMap<>();
        for (AttendanceEntry e : entryRepository.findBySessionId(sessionId)) entries.put(e.getUserId(), e);
        Set<Long> active = activeOn(session.getDate());
        List<AttendanceEntryView> views = new ArrayList<>();
        for (User user : members(course.getId())) {
            AttendanceEntry e = entries.get(user.getId());
            views.add(new AttendanceEntryView(user.getId(), user.getUsername(), user.getVisibleName(),
                    e == null ? null : e.getStatus(), e == null ? null : e.getNote(), active.contains(user.getId())));
        }
        return new AttendanceSessionDetail(summary(session, entries.values()), course.getId(), course.getName(), views);
    }

    @Transactional(readOnly = true)
    public AttendanceOverview overview(Long courseId) {
        Course course = courseService.get(courseId);
        List<AttendanceSession> sessions = sessionRepository.findByCourseIdOrderByDateAscIdAsc(courseId);
        Map<Long, List<AttendanceEntry>> bySession = new HashMap<>();
        Map<Long, Map<Long, AttendanceStatus>> cells = new LinkedHashMap<>();
        List<AttendanceEntry> all = sessions.isEmpty() ? List.of()
                : entryRepository.findBySessionIdIn(sessions.stream().map(AttendanceSession::getId).toList());
        for (AttendanceEntry e : all) {
            bySession.computeIfAbsent(e.getSessionId(), k -> new ArrayList<>()).add(e);
            cells.computeIfAbsent(e.getUserId(), k -> new HashMap<>()).put(e.getSessionId(), e.getStatus());
        }
        List<AttendanceSessionSummary> summaries = sessions.stream().map(s -> summary(s, bySession.getOrDefault(s.getId(), List.of()))).toList();
        List<AttendanceStudentRow> rows = new ArrayList<>();
        for (User user : members(courseId)) {
            rows.add(new AttendanceStudentRow(user.getId(), user.getUsername(), user.getVisibleName(),
                    rate(cells.getOrDefault(user.getId(), Map.of()).values())));
        }
        return new AttendanceOverview(courseId, course.getName(), summaries, rows, cells);
    }

    // ------------------------------------------------------------------ Je Person

    /** Quote einer Person über alle Termine ihres Kurses. */
    @Transactional(readOnly = true)
    public AttendanceRate rate(User user) {
        return rate(person(user).values().stream().map(AttendanceEntry::getStatus).toList());
    }

    @Transactional(readOnly = true)
    public AttendancePersonView personView(User user) {
        Map<AttendanceSession, AttendanceEntry> entries = person(user);
        List<AttendancePersonEntry> list = entries.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<AttendanceSession, AttendanceEntry> e) -> e.getKey().getDate()))
                .map(e -> new AttendancePersonEntry(e.getKey().getDate(), e.getKey().getTitle(), e.getValue().getStatus(), e.getValue().getNote()))
                .toList();
        return new AttendancePersonView(user.getUsername(), user.getVisibleName(),
                rate(entries.values().stream().map(AttendanceEntry::getStatus).toList()), list);
    }

    /** Eigene Anwesenheit – nur wenn die Lehrkraft das für die Installation eingeschaltet hat. */
    @Transactional(readOnly = true)
    public AttendancePersonView own(User user) {
        if (!settings().visibleToStudents()) throw ApiException.forbidden("Die Anwesenheit ist nicht für Schülerinnen und Schüler sichtbar.");
        AttendancePersonView view = personView(user);
        // Notizen der Lehrkraft bleiben bei ihr
        return new AttendancePersonView(view.username(), view.displayName(), view.rate(),
                view.entries().stream().map(e -> new AttendancePersonEntry(e.date(), e.title(), e.status(), null)).toList());
    }

    /** Zeile für die Teilnahmebestätigung, wenn eingeschaltet und Termine erfasst sind; sonst null. */
    @Transactional(readOnly = true)
    public String certificateLine(User user) {
        if (!settings().onCertificate()) return null;
        AttendanceRate rate = rate(user);
        return rate.recorded() == 0 ? null : "Anwesenheit: " + rate.text();
    }

    private Map<AttendanceSession, AttendanceEntry> person(User user) {
        Map<AttendanceSession, AttendanceEntry> result = new HashMap<>();
        Map<Long, AttendanceSession> sessions = new HashMap<>();
        userService.courseOf(user.getId()).ifPresent(c ->
                sessionRepository.findByCourseIdOrderByDateAscIdAsc(c.getId()).forEach(s -> sessions.put(s.getId(), s)));
        for (AttendanceEntry e : entryRepository.findByUserId(user.getId())) {
            AttendanceSession s = sessions.get(e.getSessionId());
            if (s != null) result.put(s, e);
        }
        return result;
    }

    static AttendanceRate rate(Collection<AttendanceStatus> statuses) {
        int attended = 0, excused = 0, late = 0, absent = 0;
        for (AttendanceStatus s : statuses) {
            if (s.attended()) attended++;
            if (s == AttendanceStatus.EXCUSED) excused++;
            if (s == AttendanceStatus.LATE) late++;
            if (s == AttendanceStatus.ABSENT) absent++;
        }
        return new AttendanceRate(attended, statuses.size(), excused, late, absent);
    }

    // ------------------------------------------------------------------ Exporte

    /** CSV (Semikolon, UTF-8 mit BOM für Excel): eine Zeile je Person, eine Spalte je Termin, dazu die Quote. */
    @Transactional
    public String csv(Long courseId) {
        AttendanceOverview overview = overview(courseId);
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Benutzername;Name");
        for (AttendanceSessionSummary s : overview.sessions()) {
            sb.append(';').append(csvCell(s.date().format(DATE) + (s.title() != null ? " " + s.title() : "")));
        }
        sb.append(";Teilgenommen;Termine;Entschuldigt\n");
        for (AttendanceStudentRow row : overview.students()) {
            sb.append(csvCell(row.username())).append(';').append(csvCell(row.displayName()));
            Map<Long, AttendanceStatus> cells = overview.cells().getOrDefault(row.userId(), Map.of());
            for (AttendanceSessionSummary s : overview.sessions()) {
                AttendanceStatus status = cells.get(s.id());
                sb.append(';').append(status == null ? "" : label(status));
            }
            sb.append(';').append(row.rate().attended()).append(';').append(row.rate().recorded())
                    .append(';').append(row.rate().excused()).append('\n');
        }
        auditService.log("ATTENDANCE_EXPORTED", "course:" + courseId, Map.of("format", "csv"));
        return sb.toString();
    }

    /** Je Person ein PDF mit allen Terminen (für die Sammel-Exporte). */
    @Transactional
    public List<PdfFile> pdfs(Long courseId) {
        Course course = courseService.get(courseId);
        List<PdfFile> files = new ArrayList<>();
        for (User user : members(courseId)) {
            files.add(new PdfFile("Anwesenheit_" + user.getUsername() + ".pdf", user.getVisibleName(),
                    AttendancePdf.render(course.getName(), personView(user))));
        }
        auditService.log("ATTENDANCE_EXPORTED", "course:" + courseId, Map.of("format", "pdf", "files", files.size()));
        return files;
    }

    static String label(AttendanceStatus status) {
        return switch (status) {
            case PRESENT -> "anwesend";
            case ABSENT -> "fehlt";
            case EXCUSED -> "entschuldigt";
            case LATE -> "verspätet";
        };
    }

    private static String csvCell(String value) {
        if (value == null) return "";
        String v = value.replace("\"", "\"\"");
        // Keine Formeln in Tabellenprogrammen auslösen
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return v.contains(";") || v.contains("\n") || v.contains("\"") ? "\"" + v + "\"" : v;
    }

    // ------------------------------------------------------------------ Einstellungen

    public AttendanceSettings settings() {
        return new AttendanceSettings(settings.isEnabled(SettingsService.ATTENDANCE_ON_CERTIFICATE),
                settings.isEnabled(SettingsService.ATTENDANCE_VISIBLE_TO_STUDENTS));
    }

    @Transactional
    public AttendanceSettings updateSettings(Boolean onCertificate, Boolean visibleToStudents) {
        if (onCertificate != null) settings.set(SettingsService.ATTENDANCE_ON_CERTIFICATE, onCertificate);
        if (visibleToStudents != null) settings.set(SettingsService.ATTENDANCE_VISIBLE_TO_STUDENTS, visibleToStudents);
        return settings();
    }

    // ------------------------------------------------------------------ Kurs beenden

    @EventListener
    @Transactional
    public void onCourseEnding(CourseService.CourseEndingEvent event) {
        sessionRepository.deleteByCourseId(event.courseId());
    }

    // ------------------------------------------------------------------ Hilfen

    private AttendanceSession session(Long id) {
        return sessionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Termin nicht gefunden."));
    }

    private List<User> members(Long courseId) {
        List<User> users = new ArrayList<>();
        for (Long id : userService.memberIds(courseId)) userService.byId(id).ifPresent(users::add);
        users.sort(Comparator.comparing(User::getVisibleName, String.CASE_INSENSITIVE_ORDER));
        return users;
    }

    /** Wer an dem Tag Abgaben gemacht hat – nur ein Vorschlag für die Lehrkraft. */
    private Set<Long> activeOn(LocalDate date) {
        return new HashSet<>(jdbc.queryForList(
                "select distinct user_id from attempts where created_at >= ? and created_at < ?",
                Long.class, date.atStartOfDay(), date.plusDays(1).atStartOfDay()));
    }

    private static AttendanceSessionSummary summary(AttendanceSession s, Collection<AttendanceEntry> entries) {
        int present = 0, absent = 0, excused = 0, late = 0;
        for (AttendanceEntry e : entries) {
            switch (e.getStatus()) {
                case PRESENT -> present++;
                case ABSENT -> absent++;
                case EXCUSED -> excused++;
                case LATE -> late++;
            }
        }
        return new AttendanceSessionSummary(s.getId(), s.getDate(), s.getTitle(), present, absent, excused, late);
    }

    private static String title(String title) {
        if (title == null || title.isBlank()) return null;
        if (title.strip().length() > 200) throw ApiException.badRequest("Der Titel ist zu lang (höchstens 200 Zeichen).");
        return title.strip();
    }
}

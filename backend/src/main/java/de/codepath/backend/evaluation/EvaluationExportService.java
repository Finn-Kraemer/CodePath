package de.codepath.backend.evaluation;

import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.platform.pdf.PdfFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Bewertungsbögen als PDF: der Bogen einer Person (für die Lehrkraft oder die Person selbst), die
 * Klassenauswertung und alles zusammen als ZIP. Die PDFs entstehen beim Abruf und werden nicht gespeichert.
 * Die Sichtbarkeitsregeln gelten wie in der Oberfläche: Schüler sehen die Lehrerbewertung nur, wenn sie
 * freigegeben ist; anonyme Unterrichtsbewertungen stehen nie in einem Bogen für die Lehrkraft.
 */
@Service
@RequiredArgsConstructor
public class EvaluationExportService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

    private final EvaluationService service;
    private final UserService userService;
    private final AuditService auditService;

    /** Bogen einer Person für die Lehrkraft. */
    public PdfFile studentSheet(Long roundId, Long userId) {
        EvaluationService.RoundAdmin round = service.get(roundId);
        EvaluationService.StudentEvaluation student = service.student(roundId, userId);
        return new PdfFile(fileName("Bewertung", round.title(), student.username()), student.displayName(),
                EvaluationPdf.sheet(teacherSheet(round, student)));
    }

    /** Eigener Bogen der angemeldeten Person. */
    public PdfFile mySheet(User user, Long roundId) {
        EvaluationService.MyRoundDetail d = service.myRound(user, roundId);
        String course = userService.courseOf(user.getId()).map(Course::getName).orElse("");
        List<EvaluationPdf.Section> sections = new ArrayList<>();
        if (d.self() != null) {
            sections.add(section(EvaluationPart.SELF, d.self().criteria(), d.self().answers(), d.self().submittedAt(), null, null, null));
        }
        if (d.lesson() != null) {
            EvaluationPdf.Section lesson = section(EvaluationPart.LESSON, d.lesson().criteria(), d.lesson().answers(),
                    d.lesson().submittedAt(), null, null, null);
            if (d.lessonAnonymous()) {
                lesson = new EvaluationPdf.Section(lesson.title(), join(lesson.note(), "Anonym abgegeben – die Lehrkraft sieht diese Antworten ohne deinen Namen."),
                        null, lesson.rows());
            }
            sections.add(lesson);
        }
        switch (d.teacherState()) {
            case NONE -> {
            }
            case VISIBLE -> sections.add(section(EvaluationPart.TEACHER, d.teacher().criteria(), d.teacher().answers(),
                    d.teacher().submittedAt(), d.self() == null ? null : d.self().criteria(),
                    d.self() == null ? null : d.self().answers(), "Deine Einschätzung"));
            default -> sections.add(new EvaluationPdf.Section(title(EvaluationPart.TEACHER), switch (d.teacherState()) {
                case HIDDEN -> "Die Lehrkraft hat ihre Bewertung nicht freigegeben.";
                case AFTER_SELF -> "Sichtbar, sobald du deine Selbsteinschätzung abgeschickt hast.";
                default -> "Die Lehrkraft hat dich noch nicht bewertet.";
            }, null, List.of()));
        }
        EvaluationPdf.Sheet sheet = new EvaluationPdf.Sheet(d.title(), course, user.getVisibleName(), d.intro(), LocalDateTime.now(), sections);
        return new PdfFile(fileName("Bewertung", d.title(), user.getUsername()), user.getVisibleName(), EvaluationPdf.sheet(sheet));
    }

    /** Klassenauswertung als PDF. */
    public PdfFile summary(Long roundId) {
        EvaluationService.RoundResults results = service.results(roundId);
        return new PdfFile(fileName("Klassenauswertung", results.round().title(), null), "Klassenauswertung",
                EvaluationPdf.summary(summaryOf(results)));
    }

    /**
     * Klassenauswertung und die Bögen aller Personen des Kurses – für ZIP oder Sammel-PDF.
     * Die Klassenauswertung steht vorne ({@code 00_…}), danach die Personen nach Namen.
     */
    public List<PdfFile> classFiles(Long roundId) {
        EvaluationService.RoundResults results = service.results(roundId);
        EvaluationService.RoundAdmin round = results.round();
        List<PdfFile> files = new ArrayList<>();
        files.add(new PdfFile("00_Klassenauswertung.pdf", "Klassenauswertung", EvaluationPdf.summary(summaryOf(results))));
        for (EvaluationService.StudentRow row : results.students()) {
            EvaluationService.StudentEvaluation student = service.student(roundId, row.userId());
            files.add(new PdfFile("Bewertung_" + safe(student.username()) + ".pdf", student.displayName(),
                    EvaluationPdf.sheet(teacherSheet(round, student))));
        }
        auditService.log("EVALUATION_ROUND_EXPORTED", "evaluation:" + roundId, Map.of("students", results.students().size()));
        return files;
    }

    /** Dateiname ohne Endung für ZIP und Sammel-PDF */
    public String bundleName(Long roundId) {
        return "Bewertungen_" + safe(service.get(roundId).title());
    }

    // ------------------------------------------------------------------ Aufbau

    private EvaluationPdf.Sheet teacherSheet(EvaluationService.RoundAdmin round, EvaluationService.StudentEvaluation s) {
        List<EvaluationPdf.Section> sections = new ArrayList<>();
        if (s.selfCriteria() != null) {
            sections.add(section(EvaluationPart.SELF, s.selfCriteria(), s.selfAnswers(), s.selfSubmittedAt(), null, null, null));
        }
        if (s.lessonCriteria() != null) {
            if (round.lessonAnonymous()) {
                sections.add(new EvaluationPdf.Section(title(EvaluationPart.LESSON),
                        (s.lessonSubmitted() ? "Abgegeben" : "Noch nicht ausgefüllt")
                                + " – anonym, die Antworten stehen nur in der Klassenauswertung.", null, List.of()));
            } else {
                sections.add(section(EvaluationPart.LESSON, s.lessonCriteria(), s.lessonAnswers(), null, null, null, null));
            }
        }
        if (s.teacherCriteria() != null) {
            EvaluationPdf.Section teacher = section(EvaluationPart.TEACHER, s.teacherCriteria(), s.teacherAnswers(), s.teacherRatedAt(),
                    s.selfCriteria(), s.selfAnswers(), "Selbsteinschätzung");
            String visibility = switch (round.teacherVisibility()) {
                case HIDDEN -> "Für die Person nicht sichtbar.";
                case VISIBLE -> "Für die Person sichtbar.";
                case AFTER_SELF -> "Für die Person sichtbar, sobald sie sich selbst eingeschätzt hat.";
            };
            String by = s.teacherRatedBy() == null ? null : "Bewertet von " + s.teacherRatedBy() + ".";
            sections.add(new EvaluationPdf.Section(teacher.title(), join(join(teacher.note(), by), visibility),
                    teacher.compareTitle(), teacher.rows()));
        }
        return new EvaluationPdf.Sheet(round.title(), round.courseName(), s.displayName() + " (@" + s.username() + ")",
                null, LocalDateTime.now(), sections);
    }

    private static EvaluationPdf.Section section(EvaluationPart part, List<EvaluationCriterion> criteria, Map<String, Object> answers,
                                                 LocalDateTime when, List<EvaluationCriterion> compareCriteria,
                                                 Map<String, Object> compareAnswers, String compareTitle) {
        if (answers == null) {
            return new EvaluationPdf.Section(title(part), part == EvaluationPart.TEACHER ? "Noch nicht bewertet." : "Noch nicht ausgefüllt.",
                    null, List.of());
        }
        Map<String, EvaluationCriterion> others = new HashMap<>();
        if (compareCriteria != null) compareCriteria.forEach(c -> others.put(c.id(), c));
        boolean compare = compareTitle != null && compareAnswers != null
                && criteria.stream().anyMatch(c -> c.answerable() && others.containsKey(c.id()));
        List<EvaluationPdf.Row> rows = new ArrayList<>();
        for (EvaluationCriterion c : criteria) {
            if (!c.answerable()) {
                rows.add(EvaluationPdf.Row.heading(c.label()));
                continue;
            }
            Object value = answers.get(c.id());
            EvaluationCriterion other = compare ? others.get(c.id()) : null;
            Object otherValue = other == null ? null : compareAnswers.get(c.id());
            rows.add(new EvaluationPdf.Row(false, c.label(), format(c, value), percent(c, value),
                    other == null ? (compare ? "" : null) : format(other, otherValue), other == null ? null : percent(other, otherValue)));
        }
        String note = when == null ? null : (part == EvaluationPart.TEACHER ? "Bewertet am " : "Abgeschickt am ") + when.format(WHEN) + ".";
        return new EvaluationPdf.Section(title(part), note, compare ? compareTitle : null, rows);
    }

    private static EvaluationPdf.Summary summaryOf(EvaluationService.RoundResults r) {
        EvaluationService.RoundAdmin round = r.round();
        boolean hasSelf = round.selfCriteria() != null;
        boolean hasLesson = round.lessonCriteria() != null;
        boolean hasTeacher = round.teacherCriteria() != null || round.teacherUsesSelfCriteria();
        List<EvaluationPdf.ClassRow> rows = r.students().stream().map(s -> new EvaluationPdf.ClassRow(s.displayName(), s.username(),
                s.selfSubmittedAt() == null ? "–" : "ja", s.lessonSubmitted() ? "ja" : "–", s.teacherRatedAt() == null ? "–" : "ja",
                s.selfScore(), s.teacherScore())).toList();
        List<EvaluationPdf.StatsBlock> blocks = new ArrayList<>();
        if (r.self() != null) blocks.add(new EvaluationPdf.StatsBlock(title(EvaluationPart.SELF), r.self().responses() + " ausgefüllt", r.self().criteria()));
        if (r.lesson() != null) {
            blocks.add(new EvaluationPdf.StatsBlock(title(EvaluationPart.LESSON),
                    r.lesson().responses() + " ausgefüllt" + (round.lessonAnonymous() ? " · anonym" : ""), r.lesson().criteria()));
        }
        if (r.teacher() != null) blocks.add(new EvaluationPdf.StatsBlock(title(EvaluationPart.TEACHER), r.teacher().responses() + " bewertet", r.teacher().criteria()));
        return new EvaluationPdf.Summary(round.title(), round.courseName(), LocalDateTime.now(), hasSelf, hasLesson, hasTeacher, rows, blocks);
    }

    static String format(EvaluationCriterion c, Object value) {
        if (value == null || (value instanceof String s && s.isBlank()) || (value instanceof Collection<?> l && l.isEmpty())) return "–";
        return switch (c.type()) {
            case SCALE -> {
                int n = ((Number) value).intValue();
                String label = c.labels() == null ? null : c.labels().get(n - c.min());
                yield label != null && !label.isBlank() ? n + " – " + label : n + " von " + c.max();
            }
            case YES_NO -> Boolean.TRUE.equals(value) ? "Ja" : "Nein";
            case CHOICE -> value instanceof Collection<?> list ? String.join(", ", list.stream().map(String::valueOf).toList()) : String.valueOf(value);
            default -> String.valueOf(value);
        };
    }

    static Integer percent(EvaluationCriterion c, Object value) {
        if (c.type() != CriterionType.SCALE || !(value instanceof Number n)) return null;
        return (int) Math.round((n.doubleValue() - c.min()) * 100.0 / (c.max() - c.min()));
    }

    private static String title(EvaluationPart part) {
        return switch (part) {
            case SELF -> "SELBSTEINSCHÄTZUNG";
            case LESSON -> "UNTERRICHTSBEWERTUNG";
            case TEACHER -> "BEWERTUNG DURCH DIE LEHRKRAFT";
        };
    }

    private static String join(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + " " + b;
    }

    static String safe(String text) {
        String s = text == null ? "" : text.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss")
                .replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("(^_+|_+$)", "");
        if (s.length() > 60) s = s.substring(0, 60);
        return s.isEmpty() ? "bogen" : s;
    }

    private static String fileName(String prefix, String title, String username) {
        return prefix + "_" + safe(title) + (username == null ? "" : "_" + safe(username)) + ".pdf";
    }
}

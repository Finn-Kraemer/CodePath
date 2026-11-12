package de.codepath.backend.worksheet;

import de.codepath.backend.content.MediaFile;
import de.codepath.backend.content.MediaService;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.CourseService;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.pdf.PdfFile;
import de.codepath.backend.platform.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Aufgabenblätter der Lehrkraft: anlegen, bearbeiten, kopieren, löschen und als PDF erzeugen
 * (Schülerfassung oder Lösung). Die PDFs entstehen bei jedem Abruf neu und werden nicht gespeichert.
 */
@Service
@RequiredArgsConstructor
public class WorksheetService {

    public record WorksheetSummary(Long id, String title, String kind, String subject, int blocks, int tasks, double points,
                                   LocalDateTime updatedAt) {
    }

    public record WorksheetDetail(Long id, String title, JsonNode content, LocalDateTime updatedAt) {
    }

    public record WorksheetUpdate(String title, JsonNode content) {
    }

    public record Pdf(String title, byte[] data) {
    }

    private final WorksheetRepository repository;
    private final WorksheetContent worksheetContent;
    private final MediaService mediaService;
    private final AuditService auditService;
    private final CourseService courseService;
    private final UserService userService;

    static final int MAX_VARIANTS = 6;

    @Transactional(readOnly = true)
    public List<WorksheetSummary> all() {
        return repository.findAllByOrderByUpdatedAtDescIdDesc().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public WorksheetDetail detail(Long id) {
        Worksheet sheet = get(id);
        return new WorksheetDetail(sheet.getId(), sheet.getTitle(), worksheetContent.read(sheet.getContent()), sheet.getUpdatedAt());
    }

    @Transactional
    public WorksheetSummary create(String title) {
        Worksheet sheet = new Worksheet();
        sheet.setTitle(WorksheetContent.title(title));
        sheet.setContent(worksheetContent.starter());
        repository.save(sheet);
        auditService.log("WORKSHEET_CREATED", sheet.getTitle(), Map.of("id", sheet.getId()));
        return summary(sheet);
    }

    @Transactional
    public WorksheetDetail update(Long id, WorksheetUpdate update) {
        Worksheet sheet = get(id);
        if (update.title() != null) sheet.setTitle(WorksheetContent.title(update.title()));
        if (update.content() != null) sheet.setContent(worksheetContent.validate(update.content()));
        sheet.setUpdatedAt(LocalDateTime.now());
        return detail(sheet.getId());
    }

    /** Kopie als Vorlage für ein neues Blatt. */
    @Transactional
    public WorksheetSummary duplicate(Long id) {
        Worksheet original = get(id);
        Worksheet copy = new Worksheet();
        String title = original.getTitle() + " (Kopie)";
        copy.setTitle(title.length() > WorksheetContent.MAX_TITLE ? title.substring(0, WorksheetContent.MAX_TITLE) : title);
        copy.setContent(original.getContent());
        repository.save(copy);
        auditService.log("WORKSHEET_CREATED", copy.getTitle(), Map.of("id", copy.getId(), "copyOf", original.getId()));
        return summary(copy);
    }

    @Transactional
    public void delete(Long id) {
        Worksheet sheet = get(id);
        repository.delete(sheet);
        auditService.log("WORKSHEET_DELETED", sheet.getTitle(), Map.of("id", id));
    }

    /** PDF des gespeicherten Blatts. */
    @Transactional(readOnly = true)
    public Pdf pdf(Long id, WorksheetRenderer.Variant variant) {
        Worksheet sheet = get(id);
        return new Pdf(sheet.getTitle(), render(sheet.getTitle(), worksheetContent.read(sheet.getContent()), variant));
    }

    /** Vorschau eines noch nicht gespeicherten Stands aus dem Editor. */
    @Transactional(readOnly = true)
    public Pdf preview(WorksheetUpdate draft, WorksheetRenderer.Variant variant) {
        String title = draft.title() == null || draft.title().isBlank() ? "Aufgabenblatt" : WorksheetContent.title(draft.title());
        worksheetContent.validate(draft.content());
        return new Pdf(title, render(title, draft.content(), variant));
    }

    private byte[] render(String title, JsonNode content, WorksheetRenderer.Variant variant) {
        return render(title, content, variant, null);
    }

    private byte[] render(String title, JsonNode content, WorksheetRenderer.Variant variant, WorksheetRenderer.Personalization person) {
        return WorksheetRenderer.render(title, content, variant,
                key -> mediaService.get(key).map(MediaFile::getData).orElse(null), person);
    }

    /**
     * Ein eigenes Blatt je Person eines Kurses: Name, Kurs und heutiges Datum im Kopf, eigene Mischung
     * (Seed aus Person und Blatt, bei jedem Export gleich). Mit {@code solution} die jeweils passende Lösung.
     */
    @Transactional
    public List<PdfFile> forStudents(Long id, Long courseId, boolean solution) {
        Worksheet sheet = get(id);
        Course course = courseService.get(courseId);
        JsonNode content = worksheetContent.read(sheet.getContent());
        WorksheetRenderer.Variant variant = solution ? WorksheetRenderer.Variant.SOLUTION : WorksheetRenderer.Variant.STUDENT;
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        List<User> members = new java.util.ArrayList<>();
        for (Long userId : userService.memberIds(courseId)) userService.byId(userId).ifPresent(members::add);
        members.sort(java.util.Comparator.comparing(User::getVisibleName, String.CASE_INSENSITIVE_ORDER));
        String base = WorksheetController.fileName(sheet.getTitle());
        List<PdfFile> files = new java.util.ArrayList<>();
        for (User user : members) {
            var person = new WorksheetRenderer.Personalization(user.getVisibleName(), course.getName(), today, null,
                    WorksheetRenderer.personSeed(user.getId(), sheet.getId()));
            files.add(new PdfFile(base + "_" + user.getUsername() + (solution ? "_Loesung" : "") + ".pdf",
                    user.getVisibleName(), render(sheet.getTitle(), content, variant, person)));
        }
        auditService.log("WORKSHEET_EXPORTED", sheet.getTitle(), Map.of("id", id, "course", "course:" + courseId,
                "files", files.size(), "solution", solution));
        return files;
    }

    /** {@code count} Varianten A, B, C … mit eigener Mischung, für Klassen ohne Konten. */
    @Transactional(readOnly = true)
    public List<PdfFile> variants(Long id, int count, boolean solution) {
        if (count < 2 || count > MAX_VARIANTS) throw ApiException.badRequest("Zwischen 2 und " + MAX_VARIANTS + " Varianten.");
        Worksheet sheet = get(id);
        JsonNode content = worksheetContent.read(sheet.getContent());
        WorksheetRenderer.Variant variant = solution ? WorksheetRenderer.Variant.SOLUTION : WorksheetRenderer.Variant.STUDENT;
        String base = WorksheetController.fileName(sheet.getTitle());
        List<PdfFile> files = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            String letter = String.valueOf((char) ('A' + i));
            var person = new WorksheetRenderer.Personalization(null, null, null, "Variante " + letter,
                    WorksheetRenderer.personSeed(-(i + 1), sheet.getId()));
            files.add(new PdfFile(base + "_Variante_" + letter + (solution ? "_Loesung" : "") + ".pdf",
                    "Variante " + letter, render(sheet.getTitle(), content, variant, person)));
        }
        return files;
    }

    public String title(Long id) {
        return get(id).getTitle();
    }

    private Worksheet get(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Aufgabenblatt nicht gefunden."));
    }

    private WorksheetSummary summary(Worksheet sheet) {
        WorksheetContent.Stats stats = worksheetContent.stats(worksheetContent.read(sheet.getContent()));
        return new WorksheetSummary(sheet.getId(), sheet.getTitle(), stats.kind(), stats.subject(), stats.blocks(), stats.tasks(),
                stats.points(), sheet.getUpdatedAt());
    }
}

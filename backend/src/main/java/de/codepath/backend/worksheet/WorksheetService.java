package de.codepath.backend.worksheet;

import de.codepath.backend.content.MediaFile;
import de.codepath.backend.content.MediaService;
import de.codepath.backend.platform.ApiException;
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
        return WorksheetRenderer.render(title, content, variant,
                key -> mediaService.get(key).map(MediaFile::getData).orElse(null));
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

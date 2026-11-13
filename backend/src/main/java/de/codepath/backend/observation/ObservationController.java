package de.codepath.backend.observation;

import de.codepath.backend.identity.CourseService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.pdf.PdfBundles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Klassenraum und Beobachtungsbogen – nur für Lehrkräfte ({@code /api/admin/**}). Schülerinnen und Schüler
 * sehen weder Bewertungen noch Notizen; es gibt keinen Endpunkt für sie.
 */
@RestController
@RequestMapping("/api/admin/observations")
@RequiredArgsConstructor
public class ObservationController {

    private final ObservationService service;
    private final ObservationExchange exchange;
    private final CourseService courseService;

    public record NameRequest(String name) {
    }

    public record SeatRequest(String seatId) {
    }

    public record NoteRequest(String note) {
    }

    public record ValueRequest(String value) {
    }

    public record OrderRequest(List<Long> ids) {
    }

    public record ApplyRequest(Long sourceId, boolean room, boolean criteria) {
    }

    @GetMapping
    public ObservationService.BoardView observationBoard(@RequestParam(name = "courseId", required = false) Long courseId) {
        return service.board(resolveCourse(courseId));
    }

    @PutMapping("/{courseId}/layout")
    public ObservationService.BoardView observationLayout(@PathVariable("courseId") Long courseId, @RequestBody RoomLayout layout) {
        return service.updateLayout(courseId, layout);
    }

    // ------------------------------------------------------------------ Kriterien

    @PostMapping("/{courseId}/criteria")
    public ObservationService.BoardView observationCreateCriterion(@PathVariable("courseId") Long courseId,
                                                        @RequestBody ObservationService.CriterionRequest request) {
        return service.createCriterion(courseId, request);
    }

    @PutMapping("/{courseId}/criteria/{id}")
    public ObservationService.BoardView observationUpdateCriterion(@PathVariable("courseId") Long courseId, @PathVariable("id") Long id,
                                                        @RequestBody ObservationService.CriterionRequest request) {
        return service.updateCriterion(courseId, id, request);
    }

    @DeleteMapping("/{courseId}/criteria/{id}")
    public ObservationService.BoardView observationDeleteCriterion(@PathVariable("courseId") Long courseId, @PathVariable("id") Long id) {
        return service.deleteCriterion(courseId, id);
    }

    @PutMapping("/{courseId}/criteria-order")
    public ObservationService.BoardView observationReorder(@PathVariable("courseId") Long courseId, @RequestBody OrderRequest request) {
        return service.reorderCriteria(courseId, request.ids());
    }

    // ------------------------------------------------------------------ Personen

    @PostMapping("/{courseId}/people")
    public ObservationService.BoardView observationAddPerson(@PathVariable("courseId") Long courseId, @RequestBody NameRequest request) {
        return service.addPerson(courseId, request.name());
    }

    @PutMapping("/{courseId}/people/{id}")
    public ObservationService.BoardView observationRenamePerson(@PathVariable("courseId") Long courseId, @PathVariable("id") Long id,
                                                     @RequestBody NameRequest request) {
        return service.renamePerson(courseId, id, request.name());
    }

    @DeleteMapping("/{courseId}/people/{id}")
    public ObservationService.BoardView observationDeletePerson(@PathVariable("courseId") Long courseId, @PathVariable("id") Long id) {
        return service.deletePerson(courseId, id);
    }

    @PutMapping("/{courseId}/people/{id}/seat")
    public ObservationService.BoardView observationSeat(@PathVariable("courseId") Long courseId, @PathVariable("id") Long id,
                                             @RequestBody SeatRequest request) {
        return service.setSeat(courseId, id, request.seatId());
    }

    @PutMapping("/{courseId}/people/{id}/note")
    public ObservationService.PersonView observationNote(@PathVariable("courseId") Long courseId, @PathVariable("id") Long id,
                                              @RequestBody NoteRequest request) {
        return service.setNote(courseId, id, request.note());
    }

    @PutMapping("/{courseId}/people/{id}/ratings/{criterionId}")
    public ObservationService.PersonView observationRate(@PathVariable("courseId") Long courseId, @PathVariable("id") Long id,
                                              @PathVariable("criterionId") Long criterionId, @RequestBody ValueRequest request) {
        return service.setRating(courseId, id, criterionId, request.value());
    }

    // ------------------------------------------------------------------ Vorlagen

    @GetMapping("/templates")
    public List<ObservationService.TemplateSummary> observationTemplates() {
        return service.templates();
    }

    @PostMapping("/{courseId}/templates")
    public ObservationService.TemplateSummary observationSaveTemplate(@PathVariable("courseId") Long courseId, @RequestBody NameRequest request) {
        return service.saveTemplate(courseId, request.name());
    }

    @PostMapping("/{courseId}/apply")
    public ObservationService.BoardView observationApply(@PathVariable("courseId") Long courseId, @RequestBody ApplyRequest request) {
        if (request.sourceId() == null) throw ApiException.badRequest("Bitte eine Vorlage wählen.");
        return service.applyTemplate(courseId, request.sourceId(), request.room(), request.criteria());
    }

    @DeleteMapping("/templates/{id}")
    public void observationDeleteTemplate(@PathVariable("id") Long id) {
        service.deleteTemplate(id);
    }

    // ------------------------------------------------------------------ Export / Import

    @GetMapping("/{courseId}/export.json")
    public ResponseEntity<ObservationExchange.BoardExport> observationExportJson(@PathVariable("courseId") Long courseId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Beobachtungsbogen_Kurs_" + courseId + ".json", StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(exchange.export(courseId));
    }

    @PostMapping("/{courseId}/import")
    public ObservationExchange.ImportResult observationImportJson(@PathVariable("courseId") Long courseId,
                                                       @RequestBody ObservationExchange.BoardExport data) {
        return exchange.importBoard(courseId, data);
    }

    @GetMapping("/{courseId}/export.csv")
    public ResponseEntity<byte[]> observationCsv(@PathVariable("courseId") Long courseId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Beobachtungsbogen_Kurs_" + courseId + ".csv", StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(exchange.csv(courseId).getBytes(StandardCharsets.UTF_8));
    }

    /** Sitzplan und Tabelle; {@code perPerson=true} hängt eine Seite je Person an. */
    @GetMapping("/{courseId}/pdf")
    public ResponseEntity<byte[]> observationPdf(@PathVariable("courseId") Long courseId,
                                      @RequestParam(name = "perPerson", defaultValue = "false") boolean perPerson,
                                      @RequestParam(name = "inline", defaultValue = "false") boolean inline) {
        return PdfBundles.respond(exchange.pdf(courseId, perPerson), inline);
    }

    private Long resolveCourse(Long courseId) {
        if (courseId != null) return courseId;
        return courseService.activeCourses().stream().findFirst()
                .orElseThrow(() -> ApiException.badRequest("Bitte zuerst einen Kurs anlegen."))
                .getId();
    }
}

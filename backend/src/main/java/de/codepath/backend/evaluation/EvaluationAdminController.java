package de.codepath.backend.evaluation;

import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.pdf.PdfBundles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Vorlagen, Runden, Auswertung und Bewertung der Schüler durch die Lehrkraft. */
@RestController
@RequestMapping("/api/admin/evaluations")
@RequiredArgsConstructor
public class EvaluationAdminController {

    private final EvaluationService service;
    private final EvaluationTemplateService templateService;
    private final UserService userService;
    private final EvaluationExportService exportService;

    @GetMapping("/templates")
    public List<EvaluationTemplateService.TemplateView> templates() {
        return templateService.list();
    }

    @PostMapping("/templates")
    public EvaluationTemplateService.TemplateView createTemplate(@RequestBody EvaluationTemplateService.TemplateRequest request) {
        return templateService.create(request);
    }

    @PutMapping("/templates/{id}")
    public EvaluationTemplateService.TemplateView updateTemplate(@PathVariable("id") Long id,
                                                                 @RequestBody EvaluationTemplateService.TemplateRequest request) {
        return templateService.update(id, request);
    }

    @DeleteMapping("/templates/{id}")
    public void deleteTemplate(@PathVariable("id") Long id) {
        templateService.delete(id);
    }

    @GetMapping("/rounds")
    public List<EvaluationService.RoundAdmin> rounds(@RequestParam(name = "courseId", required = false) Long courseId) {
        return service.list(courseId);
    }

    @PostMapping("/rounds")
    public EvaluationService.RoundAdmin createRound(@RequestBody EvaluationService.RoundRequest request) {
        return service.create(request);
    }

    @GetMapping("/rounds/{id}")
    public EvaluationService.RoundAdmin round(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PutMapping("/rounds/{id}")
    public EvaluationService.RoundAdmin updateRound(@PathVariable("id") Long id, @RequestBody EvaluationService.RoundRequest request) {
        return service.update(id, request);
    }

    /** status: DRAFT, OPEN oder CLOSED */
    @PutMapping("/rounds/{id}/status")
    public EvaluationService.RoundAdmin status(@PathVariable("id") Long id, @RequestParam("status") String status) {
        try {
            return service.setStatus(id, RoundStatus.valueOf(status));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Status: DRAFT, OPEN oder CLOSED");
        }
    }

    @DeleteMapping("/rounds/{id}")
    public void deleteRound(@PathVariable("id") Long id) {
        service.delete(id);
    }

    @GetMapping("/rounds/{id}/results")
    public EvaluationService.RoundResults results(@PathVariable("id") Long id) {
        return service.results(id);
    }

    @GetMapping("/rounds/{id}/students/{userId}")
    public EvaluationService.StudentEvaluation student(@PathVariable("id") Long id, @PathVariable("userId") Long userId) {
        return service.student(id, userId);
    }

    @PutMapping("/rounds/{id}/students/{userId}")
    public EvaluationService.StudentEvaluation rate(@PathVariable("id") Long id, @PathVariable("userId") Long userId,
                                                    @RequestBody EvaluationService.EvaluationAnswers request) {
        return service.rate(id, userId, request, userService.currentUser());
    }

    /** Bogen einer Person als PDF (Selbsteinschätzung, nicht anonyme Unterrichtsbewertung, Lehrerbewertung) */
    @GetMapping("/rounds/{id}/students/{userId}/pdf")
    public ResponseEntity<byte[]> studentPdf(@PathVariable("id") Long id, @PathVariable("userId") Long userId,
                                             @RequestParam(name = "inline", defaultValue = "false") boolean inline) {
        return PdfBundles.respond(exportService.studentSheet(id, userId), inline);
    }

    /** Klassenauswertung als PDF */
    @GetMapping("/rounds/{id}/summary.pdf")
    public ResponseEntity<byte[]> summaryPdf(@PathVariable("id") Long id,
                                             @RequestParam(name = "inline", defaultValue = "false") boolean inline) {
        return PdfBundles.respond(exportService.summary(id), inline);
    }

    /** Klassenauswertung und alle Bögen: format=zip (Standard) oder pdf (eine Datei zum Drucken) */
    @GetMapping("/rounds/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable("id") Long id,
                                         @RequestParam(name = "format", required = false) String format,
                                         @RequestParam(name = "duplex", defaultValue = "false") boolean duplex,
                                         @RequestParam(name = "inline", defaultValue = "false") boolean inline) {
        return PdfBundles.respond(exportService.bundleName(id), exportService.classFiles(id), PdfBundles.Format.parse(format), duplex, inline);
    }

    @GetMapping("/rounds/{id}/export.zip")
    public ResponseEntity<byte[]> classZip(@PathVariable("id") Long id) {
        return export(id, "zip", false, false);
    }
}

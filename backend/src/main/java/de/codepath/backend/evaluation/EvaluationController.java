package de.codepath.backend.evaluation;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import de.codepath.backend.platform.pdf.PdfBundles;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Bewertungsbögen aus Sicht der Schülerinnen und Schüler. */
@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService service;
    private final UserService userService;
    private final EvaluationExportService exportService;

    @GetMapping
    public List<EvaluationService.MyRound> mine() {
        return service.mine(userService.currentUser());
    }

    @GetMapping("/{id}")
    public EvaluationService.MyRoundDetail round(@PathVariable("id") Long id) {
        return service.myRound(userService.currentUser(), id);
    }

    @PutMapping("/{id}/self")
    public EvaluationService.MyRoundDetail self(@PathVariable("id") Long id, @RequestBody EvaluationService.EvaluationAnswers request) {
        return service.answer(userService.currentUser(), id, EvaluationPart.SELF, request);
    }

    @PutMapping("/{id}/lesson")
    public EvaluationService.MyRoundDetail lesson(@PathVariable("id") Long id, @RequestBody EvaluationService.EvaluationAnswers request) {
        return service.answer(userService.currentUser(), id, EvaluationPart.LESSON, request);
    }

    /** Eigener Bogen als PDF; die Lehrerbewertung nur, wenn sie freigegeben ist */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable("id") Long id,
                                      @RequestParam(name = "inline", defaultValue = "false") boolean inline) {
        return PdfBundles.respond(exportService.mySheet(userService.currentUser(), id), inline);
    }
}

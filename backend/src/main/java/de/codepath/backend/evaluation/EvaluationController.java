package de.codepath.backend.evaluation;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Bewertungsbögen aus Sicht der Schülerinnen und Schüler. */
@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService service;
    private final UserService userService;

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
}

package de.codepath.backend.quiz;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Quiz-Runde: Steuerung unter /api/admin/quiz, Mitspielen unter /api/quiz. */
@RestController
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;
    private final UserService userService;

    public record AnswerRequest(int index, List<String> selected) {
    }

    @GetMapping("/api/quiz/current")
    public QuizService.QuizState current() {
        return quizService.studentView(userService.currentUser());
    }

    @PostMapping("/api/quiz/current/answer")
    public QuizService.QuizState answer(@RequestBody AnswerRequest request) {
        return quizService.answer(userService.currentUser(), request.index(), request.selected());
    }

    @GetMapping("/api/admin/quiz/candidates")
    public List<QuizService.Candidate> candidates() {
        return quizService.candidates();
    }

    @GetMapping("/api/admin/quiz/current")
    public QuizService.QuizState teacherView() {
        return quizService.teacherView();
    }

    @PostMapping("/api/admin/quiz")
    public QuizService.QuizState start(@RequestBody QuizService.StartRequest request) {
        return quizService.start(userService.currentUser(), request);
    }

    @PostMapping("/api/admin/quiz/current/next")
    public QuizService.QuizState next() {
        return quizService.next();
    }

    @PostMapping("/api/admin/quiz/current/reveal")
    public QuizService.QuizState reveal() {
        return quizService.reveal();
    }

    @PostMapping("/api/admin/quiz/current/finish")
    public QuizService.QuizState finish() {
        return quizService.finish();
    }
}

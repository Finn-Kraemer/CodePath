package de.codepath.backend.progress;

import de.codepath.backend.content.ContentEditorService;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.engine.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Vorschau im Editor: der Entwurf so, wie Schüler ihn sehen, und eine Probebewertung beliebiger
 * Antworten – nichts davon wird gespeichert.
 */
@RestController
@RequestMapping("/api/admin/content/tasks/{id}/preview")
@RequiredArgsConstructor
public class PreviewController {

    private final ContentEditorService editor;
    private final TaskHandlerRegistry registry;

    private static final TaskContext PREVIEW = new TaskContext(0);

    @GetMapping
    public TaskViews.TaskDetail preview(@PathVariable("id") Long taskId) {
        PublishedTask task = editor.previewTask(taskId);
        TaskHandler handler = registry.get(task.type());
        TaskSpec spec = task.spec();
        AttemptRules rules = task.rules();
        List<String> hints = task.version().getHints();
        return new TaskViews.TaskDetail(task.taskId(), task.task().getSlug(), task.version().getTitle(), task.type().name(),
                task.version().getDifficulty().name(), task.version().getPoints(), task.version().getVersion(),
                task.module().getSlug(), task.module().getTitle(),
                task.version().getLearningGoal(), task.version().getStory(), task.version().getAssignment(),
                task.version().getKnowledgeArticle(),
                handler.publicView(spec, PREVIEW), ProgressStatus.OPEN.name(), false, false, true,
                0, rules.maxAttempts(), rules.maxAttempts(), hints.size(), List.of(), rules.hintPenaltyPercent(),
                rules.firstTryBonusPercent(), rules.revealAllowed() && handler.autoGraded(), false, null, null,
                0, null, null, null, null);
    }

    @PostMapping("/evaluate")
    public SubmitResult evaluate(@PathVariable("id") Long taskId, @RequestBody Map<String, Object> answer) {
        PublishedTask task = editor.previewTask(taskId);
        TaskSpec spec = task.spec();
        Evaluation evaluation = registry.get(task.type()).evaluate(spec, answer, PREVIEW);
        int points = evaluation.outcome() == Evaluation.Outcome.CORRECT
                ? ScoreCalculator.points(spec.points(), 1.0, 0, task.rules(), true, false) : 0;
        return new SubmitResult(evaluation.outcome(), evaluation.isCorrect(), evaluation.score(),
                evaluation.feedback() + " (Vorschau – nichts gespeichert)", evaluation.details(), points,
                ProgressStatus.OPEN, 0, task.rules().maxAttempts());
    }

    /** Lösungssicht, damit die Lehrkraft prüfen kann, was Schüler nach dem Lösen sehen. */
    @GetMapping("/solution")
    public Map<String, Object> solution(@PathVariable("id") Long taskId) {
        PublishedTask task = editor.previewTask(taskId);
        return Map.of("solution", registry.get(task.type()).solutionView(task.spec(), PREVIEW),
                "explanation", task.version().getExplanation() == null ? "" : task.version().getExplanation());
    }
}

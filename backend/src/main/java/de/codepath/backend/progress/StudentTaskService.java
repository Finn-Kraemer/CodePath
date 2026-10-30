package de.codepath.backend.progress;

import de.codepath.backend.classroom.AccessPolicy;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.Module;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.engine.AttemptRules;
import de.codepath.backend.engine.TaskContext;
import de.codepath.backend.engine.TaskHandler;
import de.codepath.backend.engine.TaskHandlerRegistry;
import de.codepath.backend.identity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Was Schüler (und die Lehrkraft in der Vorschau) von Modulen und Aufgaben sehen. */
@Service
@RequiredArgsConstructor
public class StudentTaskService {

    private final ContentService contentService;
    private final AccessPolicy accessPolicy;
    private final TaskHandlerRegistry registry;
    private final ProgressRepository progressRepository;
    private final AttemptRepository attemptRepository;
    private final PointsService pointsService;
    private final SubmissionService submissionService;

    @Transactional(readOnly = true)
    public List<TaskViews.ModuleSummary> modules(User user) {
        List<Module> modules = contentService.modules();
        AccessPolicy.Releases releases = accessPolicy.releases(user);
        Map<Long, Progress> progress = progressRepository.findByUserId(user.getId()).stream()
                .collect(Collectors.toMap(Progress::getTaskId, Function.identity()));
        Map<Long, Long> points = pointsService.byTask(user.getId());
        List<TaskViews.ModuleSummary> result = new ArrayList<>();
        for (Module module : modules) {
            List<PublishedTask> tasks = contentService.publishedTasks(module);
            AccessPolicy.ModuleAccess a = releases.moduleAccess(module, tasks);
            if (!a.visible()) continue;
            // Anzahl und Punkte zählen nur, was dieser Kurs sieht
            result.add(summary(module, a, releases.visible(tasks), progress, points));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public TaskViews.ModuleDetail module(User user, String slug) {
        Module module = contentService.module(slug);
        AccessPolicy.Releases releases = accessPolicy.releases(user);
        List<PublishedTask> all = contentService.publishedTasks(module);
        AccessPolicy.ModuleAccess access = releases.moduleAccess(module, all);
        if (!access.visible()) throw ApiException.forbidden(access.reason());
        List<PublishedTask> tasks = releases.visible(all);
        Map<Long, Progress> progress = progressRepository.findByUserIdAndTaskIdIn(user.getId(),
                tasks.stream().map(PublishedTask::taskId).toList()).stream()
                .collect(Collectors.toMap(Progress::getTaskId, Function.identity()));
        Map<Long, Long> points = pointsService.byTask(user.getId());
        List<TaskViews.TaskSummary> summaries = tasks.stream().map(t -> {
            Progress p = progress.get(t.taskId());
            ProgressStatus status = p != null ? p.getStatus() : ProgressStatus.OPEN;
            AccessPolicy.ModuleAccess taskAccess = releases.taskAccess(t);
            boolean blocked = taskAccess.blocked();
            return new TaskViews.TaskSummary(t.taskId(), t.task().getSlug(), t.version().getTitle(), t.type().name(),
                    t.version().getDifficulty().name(), t.version().getPoints(), status.name(),
                    status == ProgressStatus.SOLVED, p != null && (p.isLocked() || status == ProgressStatus.FAILED),
                    blocked, blocked ? taskAccess.reason() : null,
                    points.getOrDefault(t.taskId(), 0L));
        }).toList();
        return new TaskViews.ModuleDetail(summary(module, access, tasks, progress, points), summaries);
    }

    @Transactional
    public TaskViews.TaskDetail task(User user, String moduleSlug, String taskSlug) {
        PublishedTask task = contentService.publishedTask(moduleSlug, taskSlug);
        AccessPolicy.ModuleAccess access = accessPolicy.requireTaskVisible(user, task);
        Progress progress = submissionService.open(user, task);

        TaskHandler handler = registry.get(task.type());
        TaskContext context = TaskContext.of(user.getId(), task.taskId());
        AttemptRules rules = task.rules();
        List<String> hints = task.version().getHints();

        boolean finished = progress.isFinished();
        boolean showSolution = progress.isSolutionRevealed() || finished;
        Map<String, Object> solution = showSolution && handler.autoGraded() ? handler.solutionView(task.spec(), context) : null;
        int hintsVisible = finished ? hints.size() : Math.min(progress.getHintsUsed(), hints.size());
        Optional<Attempt> last = attemptRepository.findFirstByUserIdAndTaskIdOrderByCreatedAtDesc(user.getId(), task.taskId());
        Integer left = rules.maxAttempts() == null ? null : Math.max(0, rules.maxAttempts() - progress.getFailedAttempts());

        return new TaskViews.TaskDetail(
                task.taskId(), task.task().getSlug(), task.version().getTitle(), task.type().name(),
                task.version().getDifficulty().name(), task.version().getPoints(), task.version().getVersion(),
                task.module().getSlug(), task.module().getTitle(),
                task.version().getLearningGoal(), task.version().getStory(), task.version().getAssignment(),
                task.version().getKnowledgeArticle(),
                handler.publicView(task.spec(), context),
                progress.getStatus().name(), progress.getStatus() == ProgressStatus.SOLVED,
                progress.isLocked() || progress.getStatus() == ProgressStatus.FAILED, access.open(),
                progress.getFailedAttempts(), rules.maxAttempts(), left,
                hints.size(), hints.subList(0, hintsVisible), rules.hintPenaltyPercent(), rules.firstTryBonusPercent(),
                rules.revealAllowed() && handler.autoGraded(), progress.isSolutionRevealed(),
                solution, showSolution ? task.version().getExplanation() : null,
                pointsService.forTask(user.getId(), task.taskId()),
                last.map(Attempt::getAnswer).orElse(null), last.map(Attempt::getFeedback).orElse(null),
                progress.getReviewComment(),
                access.deadline());
    }

    private static TaskViews.ModuleSummary summary(Module module, AccessPolicy.ModuleAccess access, List<PublishedTask> tasks,
                                                   Map<Long, Progress> progress, Map<Long, Long> points) {
        int completed = 0;
        long earned = 0;
        for (PublishedTask t : tasks) {
            Progress p = progress.get(t.taskId());
            if (p != null && p.getStatus() == ProgressStatus.SOLVED) completed++;
            earned += points.getOrDefault(t.taskId(), 0L);
        }
        return new TaskViews.ModuleSummary(module.getId(), module.getSlug(), module.getTitle(), module.getDescription(),
                module.getIcon(), access.visible(), access.open(), access.deadline(), tasks.size(), completed, earned,
                tasks.stream().mapToInt(t -> t.version().getPoints()).sum());
    }
}

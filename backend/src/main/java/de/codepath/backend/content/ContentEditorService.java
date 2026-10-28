package de.codepath.backend.content;

import de.codepath.backend.engine.*;
import de.codepath.backend.engine.handlers.CodeHandler;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.platform.events.RealtimeUpdateService;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Inhalte im Admin-Bereich pflegen (ADR-5). Ein Entwurf ist eine noch nicht veröffentlichte Version;
 * Veröffentlichen friert ihn ein und macht ihn zur aktuellen Version. Bisherige Versuche bleiben an
 * ihrer alten Version hängen.
 */
@Service
@RequiredArgsConstructor
public class ContentEditorService {

    private final ModuleRepository moduleRepository;
    private final TaskRepository taskRepository;
    private final TaskVersionRepository versionRepository;
    private final TaskHandlerRegistry registry;
    private final ContentImporter importer;
    private final ContentPackageReader reader;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final CodeHandler codeHandler;
    private final MediaService mediaService;
    private final MediaRepository mediaRepository;
    private final JdbcTemplate jdbc;
    private final RealtimeUpdateService realtime;

    /** Was beim endgültigen Löschen mit verloren geht. */
    public record DeleteImpact(String slug, String title, boolean archived, long attempts, long students,
                               long points, long pendingReviews) {
    }

    public record RunSolutionRequest(String code, List<Map<String, Object>> tests) {
    }

    public record TestOutput(String status, String stdout, String stderr) {
    }

    public record SolutionRun(boolean available, List<TestOutput> outputs) {
    }

    public record TaskRow(Long id, String slug, TaskType type, String title, Integer currentVersion, boolean hasDraft,
                          boolean archived, int sortOrder, int points) {
    }

    public record ModuleRow(Long id, ContentPackage.ModuleFile module, List<TaskRow> tasks) {
    }

    public record VersionRow(Long id, int version, String title, int points, LocalDateTime publishedAt, Long createdBy) {
    }

    public record TaskEditorView(Long taskId, Long moduleId, String moduleSlug, TaskType type, boolean archived,
                                 Integer currentVersion, ContentPackage.TaskFile draft, ContentPackage.TaskFile published,
                                 List<VersionRow> versions) {
    }

    public record DryRun(String sampleOutcome, String sampleFeedback, String wrongOutcome, String wrongFeedback, boolean passed) {
    }

    public record CheckResult(boolean valid, List<String> problems, DryRun dryRun) {
    }

    public record CreateTaskRequest(String slug, TaskType type, String title) {
    }

    @Transactional(readOnly = true)
    public List<ModuleRow> overview() {
        List<ModuleRow> rows = new ArrayList<>();
        for (Module m : moduleRepository.findAllByOrderBySortOrderAsc()) {
            List<TaskRow> tasks = new ArrayList<>();
            for (Task t : taskRepository.findByModuleWithVersion(m.getId())) {
                Optional<TaskVersion> draft = versionRepository.findFirstByTaskIdAndPublishedAtIsNullOrderByVersionDesc(t.getId());
                TaskVersion shown = draft.orElse(t.getCurrentVersion());
                tasks.add(new TaskRow(t.getId(), t.getSlug(), t.getType(), shown != null ? shown.getTitle() : t.getSlug(),
                        t.getCurrentVersion() != null ? t.getCurrentVersion().getVersion() : null, draft.isPresent(),
                        t.isArchived(), t.getSortOrder(), shown != null ? shown.getPoints() : 0));
            }
            rows.add(new ModuleRow(m.getId(), moduleFile(m), tasks));
        }
        return rows;
    }

    @Transactional
    public ModuleRow saveModule(Long id, ContentPackage.ModuleFile file) {
        if (file.slug() == null || !file.slug().matches("[a-z0-9][a-z0-9-]{0,49}")) throw ApiException.badRequest("Ungültiger Slug (a-z, 0-9, -).");
        if (file.title() == null || file.title().isBlank()) throw ApiException.badRequest("Das Modul braucht einen Titel.");
        Module module = id == null ? new Module() : moduleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Modul nicht gefunden."));
        moduleRepository.findBySlug(file.slug()).filter(other -> !other.getId().equals(module.getId()))
                .ifPresent(other -> { throw ApiException.conflict("Es gibt schon ein Modul mit diesem Slug."); });
        List<String> problems = ModuleFiles.problems(file);
        if (!problems.isEmpty()) throw ApiException.badRequest(String.join(" ", problems));
        ModuleFiles.apply(module, file);
        moduleRepository.save(module);
        auditService.log(id == null ? "MODULE_CREATED" : "MODULE_UPDATED", "module:" + module.getSlug(), Map.of());
        return new ModuleRow(module.getId(), moduleFile(module), List.of());
    }

    @Transactional
    public TaskEditorView createTask(Long moduleId, CreateTaskRequest request, Long actorId) {
        Module module = moduleRepository.findById(moduleId).orElseThrow(() -> ApiException.notFound("Modul nicht gefunden."));
        if (request.type() == null || !registry.supports(request.type())) throw ApiException.badRequest("Unbekannter Aufgabentyp.");
        if (request.slug() == null || !request.slug().matches("[a-z0-9][a-z0-9-]{0,49}")) throw ApiException.badRequest("Ungültiger Slug (a-z, 0-9, -).");
        if (taskRepository.findByModuleIdAndSlug(moduleId, request.slug()).isPresent()) throw ApiException.conflict("Den Slug gibt es in diesem Modul schon.");

        Task task = new Task();
        task.setModule(module);
        task.setSlug(request.slug());
        task.setType(request.type());
        task.setSortOrder(taskRepository.findByModuleWithVersion(moduleId).size() + 1);
        taskRepository.save(task);

        TaskVersion draft = new TaskVersion();
        draft.setTask(task);
        draft.setVersion(1);
        draft.setTitle(request.title() != null && !request.title().isBlank() ? request.title() : "Neue Aufgabe");
        draft.setPoints(10);
        draft.setDifficulty(Difficulty.EASY);
        draft.setConfig(new LinkedHashMap<>(registry.get(request.type()).template()));
        draft.setCreatedBy(actorId);
        versionRepository.save(draft);
        auditService.log("TASK_CREATED", "task:" + task.getId(), Map.of("module", module.getSlug(), "type", request.type().name()));
        return view(task.getId());
    }

    @Transactional(readOnly = true)
    public TaskEditorView view(Long taskId) {
        Task task = task(taskId);
        Optional<TaskVersion> draft = versionRepository.findFirstByTaskIdAndPublishedAtIsNullOrderByVersionDesc(taskId);
        List<VersionRow> versions = versionRepository.findByTaskIdOrderByVersionDesc(taskId).stream()
                .filter(TaskVersion::isPublished)
                .map(v -> new VersionRow(v.getId(), v.getVersion(), v.getTitle(), v.getPoints(), v.getPublishedAt(), v.getCreatedBy()))
                .toList();
        return new TaskEditorView(task.getId(), task.getModule().getId(), task.getModule().getSlug(), task.getType(), task.isArchived(),
                task.getCurrentVersion() != null ? task.getCurrentVersion().getVersion() : null,
                draft.map(v -> TaskFiles.fromVersion(task, v)).orElse(null),
                task.getCurrentVersion() != null ? TaskFiles.fromVersion(task, task.getCurrentVersion()) : null,
                versions);
    }

    /** Speichert den Entwurf; ungültige Zwischenstände sind erlaubt, geprüft wird beim Veröffentlichen. */
    @Transactional
    public TaskEditorView saveDraft(Long taskId, ContentPackage.TaskFile file, Long actorId) {
        Task task = task(taskId);
        TaskVersion draft = versionRepository.findFirstByTaskIdAndPublishedAtIsNullOrderByVersionDesc(taskId).orElseGet(() -> {
            TaskVersion v = new TaskVersion();
            v.setTask(task);
            v.setVersion(versionRepository.maxVersion(taskId) + 1);
            return v;
        });
        TaskVersion values = ContentImporter.toVersion(withDefaults(file));
        draft.setTitle(values.getTitle());
        draft.setLearningGoal(values.getLearningGoal());
        draft.setStory(values.getStory());
        draft.setKnowledgeArticle(values.getKnowledgeArticle());
        draft.setAssignment(values.getAssignment());
        draft.setExplanation(values.getExplanation());
        draft.setHints(values.getHints().stream().filter(h -> h != null && !h.isBlank()).toList());
        draft.setPoints(values.getPoints());
        draft.setDifficulty(values.getDifficulty());
        draft.setConfig(values.getConfig());
        draft.setRules(values.getRules());
        draft.setCreatedBy(actorId);
        draft.setCreatedAt(LocalDateTime.now());
        versionRepository.save(draft);
        if (file.sortOrder() != null) task.setSortOrder(file.sortOrder());
        return view(taskId);
    }

    /** Schema-Prüfung und Probelauf: Musterlösung muss richtig, eine Falschantwort falsch bewertet werden. */
    @Transactional(readOnly = true)
    public CheckResult check(Long taskId) {
        Task task = task(taskId);
        TaskVersion version = editable(task);
        ContentPackage.TaskFile file = TaskFiles.fromVersion(task, version);
        List<String> problems = new ArrayList<>();
        try {
            importer.validate(file);
        } catch (InvalidTaskConfigException e) {
            problems.addAll(e.problems());
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
        }
        Set<String> missingMedia = mediaService.missing(importer.mediaKeys(file));
        if (!missingMedia.isEmpty()) problems.add("Bild fehlt: " + String.join(", ", missingMedia));
        if (!problems.isEmpty()) return new CheckResult(false, problems, null);

        TaskHandler handler = registry.get(task.getType());
        TaskSpec spec = ContentService.spec(task.getType(), version);
        TaskContext context = new TaskContext(0);
        Evaluation sample = handler.evaluate(spec, handler.sampleAnswer(spec, context), context);
        Evaluation wrong = handler.evaluate(spec, handler.wrongAnswer(spec, context), context);
        Evaluation.Outcome expected = !handler.autoGraded() ? Evaluation.Outcome.MANUAL : Evaluation.Outcome.CORRECT;
        boolean sampleOk = sample.outcome() == expected || sample.outcome() == Evaluation.Outcome.UNGRADED;
        boolean passed = sampleOk && !wrong.isCorrect();
        if (!sampleOk) problems.add("Probelauf: Die Musterlösung wird als " + sample.outcome() + " bewertet (" + sample.feedback() + ")");
        if (wrong.isCorrect()) problems.add("Probelauf: Eine falsche Antwort wird als richtig bewertet");
        return new CheckResult(passed, problems,
                new DryRun(sample.outcome().name(), sample.feedback(), wrong.outcome().name(), wrong.feedback(), passed));
    }

    /** Musterlösung gegen jeden Testfall laufen lassen, damit der Editor die erwarteten Ausgaben übernehmen kann. */
    public SolutionRun runSolution(RunSolutionRequest request) {
        if (request.code() == null || request.code().isBlank()) throw ApiException.badRequest("Bitte zuerst eine Musterlösung eintragen.");
        List<Map<String, Object>> tests = request.tests() == null ? List.of() : request.tests();
        if (tests.size() > 20) throw ApiException.badRequest("Höchstens 20 Testfälle.");
        if (!codeHandler.graded()) return new SolutionRun(false, List.of());
        List<TestOutput> outputs = tests.stream().map(test -> {
            CodeRunner.RunResult result = codeHandler.runAsTest(request.code(), test);
            return new TestOutput(result.status().name(), result.stdout(), result.stderr());
        }).toList();
        return new SolutionRun(true, outputs);
    }

    @Transactional
    public TaskEditorView publish(Long taskId, Long actorId) {
        Task task = task(taskId);
        TaskVersion draft = versionRepository.findFirstByTaskIdAndPublishedAtIsNullOrderByVersionDesc(taskId)
                .orElseThrow(() -> ApiException.badRequest("Es gibt keinen Entwurf zum Veröffentlichen."));
        CheckResult check = check(taskId);
        if (!check.valid()) throw new InvalidTaskConfigException(check.problems());
        draft.setPublishedAt(LocalDateTime.now());
        draft.setCreatedBy(actorId);
        task.setCurrentVersion(draft);
        auditService.log("TASK_PUBLISHED", "task:" + taskId, Map.of("version", draft.getVersion()));
        return view(taskId);
    }

    @Transactional
    public TaskEditorView discardDraft(Long taskId) {
        Task task = task(taskId);
        versionRepository.findFirstByTaskIdAndPublishedAtIsNullOrderByVersionDesc(taskId).ifPresent(draft -> {
            if (task.getCurrentVersion() == null) throw ApiException.badRequest("Die Aufgabe hat noch keine veröffentlichte Version – archivieren statt verwerfen.");
            versionRepository.delete(draft);
        });
        return view(taskId);
    }

    /** Ältere Version als neuen Entwurf übernehmen. */
    @Transactional
    public TaskEditorView restore(Long taskId, Long versionId, Long actorId) {
        Task task = task(taskId);
        TaskVersion old = versionRepository.findById(versionId).filter(v -> v.getTask().getId().equals(taskId))
                .orElseThrow(() -> ApiException.notFound("Version nicht gefunden."));
        return saveDraft(taskId, TaskFiles.fromVersion(task, old), actorId);
    }

    @Transactional
    public TaskEditorView archive(Long taskId, boolean archived) {
        Task task = task(taskId);
        task.setArchived(archived);
        auditService.log(archived ? "TASK_ARCHIVED" : "TASK_RESTORED", "task:" + taskId, Map.of());
        return view(taskId);
    }

    /** Neue Reihenfolge der Aufgaben eines Moduls; die Liste muss jede Aufgabe des Moduls genau einmal enthalten. */
    @Transactional
    public List<ModuleRow> reorder(Long moduleId, List<Long> taskIds) {
        Module module = moduleRepository.findById(moduleId).orElseThrow(() -> ApiException.notFound("Modul nicht gefunden."));
        List<Task> tasks = taskRepository.findByModuleWithVersion(moduleId);
        Set<Long> expected = new HashSet<>();
        tasks.forEach(t -> expected.add(t.getId()));
        if (taskIds == null || taskIds.size() != expected.size() || !expected.equals(new HashSet<>(taskIds))) {
            throw ApiException.badRequest("Die Reihenfolge muss jede Aufgabe des Moduls genau einmal enthalten – bitte neu laden.");
        }
        Map<Long, Task> byId = new HashMap<>();
        tasks.forEach(t -> byId.put(t.getId(), t));
        for (int i = 0; i < taskIds.size(); i++) byId.get(taskIds.get(i)).setSortOrder(i + 1);
        auditService.log("TASKS_REORDERED", "module:" + module.getSlug(), Map.of("tasks", taskIds.size()));
        return overview();
    }

    @Transactional(readOnly = true)
    public DeleteImpact deleteImpact(Long taskId) {
        Task task = task(taskId);
        String title = Optional.ofNullable(task.getCurrentVersion()).map(TaskVersion::getTitle)
                .orElseGet(() -> editable(task).getTitle());
        return new DeleteImpact(task.getSlug(), title, task.isArchived(),
                count("select count(*) from attempts where task_id = ?", taskId),
                count("select count(distinct user_id) from progress where task_id = ?", taskId),
                count("select coalesce(sum(amount), 0) from points_entries where task_id = ?", taskId),
                count("select count(*) from practice_reviews r join attempts a on a.id = r.attempt_id "
                        + "where a.task_id = ? and r.status = 'PENDING'", taskId));
    }

    /**
     * Löscht eine archivierte Aufgabe endgültig – mit allen Versionen, Versuchen, Fortschritten und Punkten
     * (Fremdschlüssel mit ON DELETE CASCADE). Zur Sicherheit muss der Slug bestätigt werden. Die Aufgabe
     * wird gemerkt, damit mitgelieferte Inhalte sie nicht wieder anlegen.
     */
    @Transactional
    public void delete(Long taskId, String confirmSlug, Long actorId) {
        Task task = task(taskId);
        if (!task.isArchived()) throw ApiException.conflict("Nur archivierte Aufgaben lassen sich endgültig löschen.");
        if (!task.getSlug().equals(confirmSlug)) throw ApiException.badRequest("Zur Bestätigung den Slug der Aufgabe eingeben.");
        DeleteImpact impact = deleteImpact(taskId);
        String moduleSlug = task.getModule().getSlug();
        jdbc.update("insert into deleted_tasks (module_slug, task_slug, deleted_by) values (?, ?, ?) on conflict do nothing",
                moduleSlug, task.getSlug(), actorId);
        jdbc.update("delete from tasks where id = ?", taskId);
        auditService.log("TASK_DELETED", moduleSlug + "/" + task.getSlug(), Map.of("attempts", impact.attempts(),
                "students", impact.students(), "points", impact.points()));
        if (impact.points() > 0) realtime.publishLeaderboardUpdate();
    }

    private long count(String sql, Long taskId) {
        Long value = jdbc.queryForObject(sql, Long.class, taskId);
        return value == null ? 0 : value;
    }

    /** Version für die Vorschau: Entwurf, sonst die aktuelle Version. */
    @Transactional(readOnly = true)
    public PublishedTask previewTask(Long taskId) {
        Task task = task(taskId);
        return new PublishedTask(task, task.getModule(), editable(task));
    }

    /** Alle Module mit ihren aktuellen Versionen als ZIP im Content-Paket-Format. */
    @Transactional
    public byte[] exportZip() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Module m : moduleRepository.findAllByOrderBySortOrderAsc()) {
                write(zip, "content/" + m.getSlug() + "/module.json", moduleFile(m));
                Set<String> media = new TreeSet<>();
                for (Task t : taskRepository.findByModuleWithVersion(m.getId())) {
                    if (t.getCurrentVersion() == null || t.isArchived()) continue;
                    ContentPackage.TaskFile file = TaskFiles.fromVersion(t, t.getCurrentVersion());
                    write(zip, String.format("content/%s/tasks/%02d-%s.json", m.getSlug(), t.getSortOrder(), t.getSlug()), file);
                    media.addAll(importer.mediaKeys(file));
                }
                // Bilder liegen beim Modul, das sie verwendet
                for (String key : media) {
                    var image = mediaRepository.findById(key);
                    if (image.isEmpty()) continue;
                    zip.putNextEntry(new ZipEntry("content/" + m.getSlug() + "/media/" + key));
                    zip.write(image.get().getData());
                    zip.closeEntry();
                }
            }
        } catch (IOException e) {
            throw ApiException.badRequest("Export fehlgeschlagen.");
        }
        auditService.log("CONTENT_EXPORTED", "-", Map.of());
        return out.toByteArray();
    }

    @Transactional
    public ContentImporter.ImportReport importZip(InputStream input, Long actorId) {
        // Ein ZIP der Lehrkraft ist eine bewusste Entscheidung: auch gelöschte Aufgaben zurückholen
        return importer.importBundles(reader.fromZip(input), ContentImporter.Mode.UPDATE, actorId, true);
    }

    /** Mitgelieferte Content-Pakete (aus dem JAR) übernehmen; Geändertes wird zu neuen Versionen. */
    @Transactional
    public ContentImporter.ImportReport importBundled(Long actorId) {
        try {
            return importer.importBundles(reader.fromClasspath("content"), ContentImporter.Mode.UPDATE, actorId);
        } catch (IOException e) {
            throw ApiException.badRequest("Mitgelieferte Inhalte konnten nicht gelesen werden.");
        }
    }

    private void write(ZipOutputStream zip, String name, Object value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write((objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n").getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private TaskVersion editable(Task task) {
        return versionRepository.findFirstByTaskIdAndPublishedAtIsNullOrderByVersionDesc(task.getId())
                .orElseGet(() -> Optional.ofNullable(task.getCurrentVersion())
                        .orElseThrow(() -> ApiException.badRequest("Die Aufgabe hat keine Version.")));
    }

    private Task task(Long taskId) {
        return taskRepository.findById(taskId).orElseThrow(() -> ApiException.notFound("Aufgabe nicht gefunden."));
    }

    private static ContentPackage.ModuleFile moduleFile(Module m) {
        return ModuleFiles.toFile(m);
    }

    private static ContentPackage.TaskFile withDefaults(ContentPackage.TaskFile f) {
        return new ContentPackage.TaskFile(f.slug(), f.type(), f.title() == null || f.title().isBlank() ? "Ohne Titel" : f.title(),
                f.difficulty() != null ? f.difficulty() : Difficulty.EASY, f.points() != null ? Math.max(0, f.points()) : 0,
                f.sortOrder(), f.learningGoal(), f.story(), f.assignment(), f.hints(), f.explanation(), f.rules(), f.config(),
                f.knowledgeArticle());
    }
}

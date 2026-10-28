package de.codepath.backend.content;

import de.codepath.backend.engine.InvalidTaskConfigException;
import de.codepath.backend.engine.MediaKeys;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.engine.TaskHandlerRegistry;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.platform.transfer.TransferItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Übernimmt Content-Pakete in die Datenbank (ADR-5). Jede Änderung erzeugt eine neue,
 * unveränderliche Version; ungültige Aufgaben werden mit Begründung übersprungen.
 */
@Service
@RequiredArgsConstructor
public class ContentImporter {

    public enum Mode {
        /** Nur Fehlendes anlegen; Änderungen aus dem Editor bleiben unangetastet (Start der Anwendung). */
        FILL_MISSING,
        /** Zusätzlich neue Versionen für geänderte Aufgaben veröffentlichen (Import durch die Lehrkraft). */
        UPDATE
    }

    /** items: jedes Modul und jede Aufgabe mit Ergebnis, für den Bericht des zentralen Imports */
    public record ImportReport(int modulesCreated, int tasksCreated, int versionsPublished, int unchanged, List<String> errors,
                               List<TransferItem> items) {
    }

    static final String MODULES = "Module";
    static final String TASKS = "Aufgaben";
    static final String MEDIA = "Bilder";

    private final ModuleRepository moduleRepository;
    private final TaskRepository taskRepository;
    private final TaskVersionRepository versionRepository;
    private final TaskHandlerRegistry registry;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final MediaService mediaService;
    private final JdbcTemplate jdbc;

    @Transactional
    public ImportReport importBundles(List<ContentPackage.ModuleBundle> bundles, Mode mode, Long actorId) {
        return importBundles(bundles, mode, actorId, false);
    }

    /**
     * @param revive true für einen bewussten ZIP-Import der Lehrkraft: holt endgültig gelöschte Aufgaben
     *               zurück und übernimmt die Reihenfolge aus dem Paket. Sonst bleiben gelöschte Aufgaben
     *               gelöscht und die im Editor festgelegte Reihenfolge erhalten.
     */
    @Transactional
    public ImportReport importBundles(List<ContentPackage.ModuleBundle> bundles, Mode mode, Long actorId, boolean revive) {
        int modulesCreated = 0, tasksCreated = 0, versions = 0, unchanged = 0;
        List<String> errors = new ArrayList<>();
        List<TransferItem> items = new ArrayList<>();

        for (ContentPackage.ModuleBundle bundle : bundles) {
            ContentPackage.ModuleFile mf = bundle.module();
            if (mf == null || mf.slug() == null || mf.title() == null) {
                errors.add("Modul ohne slug oder title übersprungen");
                items.add(TransferItem.error(MODULES, mf != null && mf.slug() != null ? mf.slug() : "?", "slug oder title fehlt"));
                continue;
            }
            List<String> moduleProblems = ModuleFiles.problems(mf);
            if (!moduleProblems.isEmpty()) {
                errors.add(mf.slug() + ": " + String.join(" ", moduleProblems));
                items.add(TransferItem.error(MODULES, mf.slug(), String.join(" ", moduleProblems)));
                continue;
            }
            Optional<Module> existing = moduleRepository.findBySlug(mf.slug());
            Module module = existing.orElseGet(Module::new);
            if (existing.isEmpty() || mode == Mode.UPDATE) {
                boolean same = existing.isPresent() && ModuleFiles.same(module, mf);
                ModuleFiles.apply(module, mf);
                moduleRepository.save(module);
                items.add(existing.isEmpty() ? TransferItem.created(MODULES, mf.slug())
                        : same ? TransferItem.unchanged(MODULES, mf.slug()) : TransferItem.updated(MODULES, mf.slug()));
            } else if (module.getCareer() == null && mf.career() != null) {
                // Ältere Installationen: Berufsfeld und Links für den Kompass nachtragen, sonst nichts anfassen
                module.setCareer(mf.career());
                if (module.getLearnMore() == null || module.getLearnMore().isEmpty()) module.setLearnMore(ModuleFiles.links(mf));
                moduleRepository.save(module);
            }
            if (existing.isEmpty()) modulesCreated++;

            // Bilder zuerst, damit die Aufgaben sie finden
            for (var media : bundle.media().entrySet()) {
                String where = mf.slug() + "/media/" + media.getKey();
                try {
                    MediaService.Inspected inspected = mediaService.inspect(media.getValue());
                    // Der Name muss zum Inhalt passen, sonst zeigt ein Schlüssel auf ein fremdes Bild
                    if (!inspected.key().equals(media.getKey())) {
                        errors.add(where + ": Inhalt passt nicht zum Namen (erwartet " + inspected.key() + ")");
                        items.add(TransferItem.error(MEDIA, media.getKey(), "Inhalt passt nicht zum Namen"));
                        continue;
                    }
                    boolean isNew = !mediaService.missing(List.of(media.getKey())).isEmpty();
                    mediaService.save(inspected, media.getValue(), media.getKey(), actorId);
                    items.add(isNew ? TransferItem.created(MEDIA, media.getKey()) : TransferItem.unchanged(MEDIA, media.getKey()));
                } catch (ApiException e) {
                    errors.add(where + ": " + e.getMessage());
                    items.add(TransferItem.error(MEDIA, media.getKey(), e.getMessage()));
                }
            }

            for (ContentPackage.TaskFile tf : bundle.tasks()) {
                String where = mf.slug() + "/" + tf.slug();
                try {
                    validate(tf);
                    Set<String> missing = mediaService.missing(mediaKeys(tf));
                    if (!missing.isEmpty()) throw new IllegalArgumentException("Bild fehlt: " + String.join(", ", missing));
                } catch (InvalidTaskConfigException | IllegalArgumentException e) {
                    errors.add(where + ": " + e.getMessage());
                    items.add(TransferItem.error(TASKS, where, e.getMessage()));
                    continue;
                }
                Optional<Task> existingTask = taskRepository.findByModuleIdAndSlug(module.getId(), tf.slug());
                if (existingTask.isEmpty() && isDeleted(mf.slug(), tf.slug())) {
                    if (!revive) {
                        unchanged++;
                        items.add(new TransferItem(TASKS, where, TransferItem.Action.UNCHANGED, "endgültig gelöscht – nicht wieder angelegt"));
                        continue;
                    }
                    jdbc.update("delete from deleted_tasks where module_slug = ? and task_slug = ?", mf.slug(), tf.slug());
                }
                Task task = existingTask.orElseGet(Task::new);
                if (existingTask.isPresent() && task.getType() != tf.type()) {
                    errors.add(where + ": Der Typ einer bestehenden Aufgabe kann nicht geändert werden");
                    items.add(TransferItem.error(TASKS, where, "Der Typ einer bestehenden Aufgabe kann nicht geändert werden"));
                    continue;
                }
                if (existingTask.isEmpty()) {
                    task.setModule(module);
                    task.setSlug(tf.slug());
                    task.setType(tf.type());
                    tasksCreated++;
                }
                // Die Reihenfolge, die die Lehrkraft im Editor festgelegt hat, überschreiben nur ihre eigenen
                // ZIP-Importe – nicht die mitgelieferten Inhalte bei einem Update
                if (existingTask.isEmpty() || (mode == Mode.UPDATE && revive)) {
                    task.setSortOrder(tf.sortOrder() != null ? tf.sortOrder() : 0);
                }
                taskRepository.save(task);

                TaskVersion current = task.getCurrentVersion();
                boolean needsVersion = current == null || (mode == Mode.UPDATE && !sameContent(current, tf));
                if (!needsVersion) {
                    unchanged++;
                    items.add(TransferItem.unchanged(TASKS, where));
                    continue;
                }
                publish(task, toVersion(tf), actorId);
                versions++;
                items.add(existingTask.isEmpty() ? TransferItem.created(TASKS, where)
                        : new TransferItem(TASKS, where, TransferItem.Action.UPDATED, "neue Version veröffentlicht"));
            }
        }
        if (versions > 0 || tasksCreated > 0) {
            auditService.log("CONTENT_IMPORTED", mode.name(), Map.of("modules", modulesCreated, "tasks", tasksCreated, "versions", versions, "errors", errors.size()));
        }
        return new ImportReport(modulesCreated, tasksCreated, versions, unchanged, errors, items);
    }

    private boolean isDeleted(String moduleSlug, String taskSlug) {
        Integer count = jdbc.queryForObject("select count(*) from deleted_tasks where module_slug = ? and task_slug = ?",
                Integer.class, moduleSlug, taskSlug);
        return count != null && count > 0;
    }

    /** Veröffentlicht eine neue Version und macht sie zur aktuellen. */
    TaskVersion publish(Task task, TaskVersion version, Long actorId) {
        version.setTask(task);
        version.setVersion(versionRepository.maxVersion(task.getId() == null ? -1L : task.getId()) + 1);
        version.setPublishedAt(LocalDateTime.now());
        version.setCreatedBy(actorId);
        versionRepository.save(version);
        task.setCurrentVersion(version);
        taskRepository.save(task);
        return version;
    }

    void validate(ContentPackage.TaskFile tf) {
        List<String> problems = new ArrayList<>();
        if (tf.slug() == null || !tf.slug().matches("[a-z0-9][a-z0-9-]{0,49}")) problems.add("slug fehlt oder ist ungültig (a-z, 0-9, -)");
        if (tf.type() == null) problems.add("type fehlt");
        if (tf.title() == null || tf.title().isBlank()) problems.add("title fehlt");
        if (tf.points() == null || tf.points() < 0) problems.add("points fehlt");
        if (tf.difficulty() == null) problems.add("difficulty fehlt");
        if (tf.hints() != null && tf.hints().size() > 3) problems.add("höchstens drei Hinweise");
        if (tf.knowledgeArticle() != null && !tf.knowledgeArticle().isBlank()
                && !tf.knowledgeArticle().matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            problems.add("knowledgeArticle muss die Adresse eines Wissensartikels sein (Kleinbuchstaben, Ziffern, Bindestriche)");
        }
        if (!problems.isEmpty()) throw new InvalidTaskConfigException(problems);
        if (!registry.supports(tf.type())) throw new IllegalArgumentException("Aufgabentyp " + tf.type() + " wird nicht unterstützt");
        registry.requireValid(tf.type(), tf.config() != null ? tf.config() : Map.of(), tf.points());
    }

    /** Schlüssel aller Bilder, auf die eine Aufgabe in Konfiguration oder Texten verweist. */
    Set<String> mediaKeys(ContentPackage.TaskFile tf) {
        return MediaKeys.find(objectMapper.writeValueAsString(tf));
    }

    static TaskVersion toVersion(ContentPackage.TaskFile tf) {
        TaskVersion v = new TaskVersion();
        v.setTitle(tf.title());
        v.setLearningGoal(blankToNull(tf.learningGoal()));
        v.setStory(blankToNull(tf.story()));
        v.setKnowledgeArticle(blankToNull(tf.knowledgeArticle()));
        v.setAssignment(blankToNull(tf.assignment()));
        v.setExplanation(blankToNull(tf.explanation()));
        v.setHints(tf.hints() != null ? new ArrayList<>(tf.hints()) : new ArrayList<>());
        v.setPoints(tf.points());
        v.setDifficulty(tf.difficulty());
        v.setConfig(tf.config() != null ? new LinkedHashMap<>(tf.config()) : new LinkedHashMap<>());
        v.setRules(tf.rules() != null ? new LinkedHashMap<>(tf.rules()) : new LinkedHashMap<>());
        return v;
    }

    /** Vergleich über normalisiertes JSON, damit Zahlentypen und Schlüsselreihenfolge keine Rolle spielen. */
    boolean sameContent(TaskVersion current, ContentPackage.TaskFile tf) {
        // Die Position gehört zur Aufgabe, nicht zur Version: umsortieren erzeugt keine neue Version
        return json(TaskFiles.withoutOrder(TaskFiles.fromVersion(current.getTask(), current)))
                .equals(json(TaskFiles.withoutOrder(TaskFiles.normalize(tf))));
    }

    private Object json(Object value) {
        return objectMapper.readTree(objectMapper.writeValueAsString(value));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

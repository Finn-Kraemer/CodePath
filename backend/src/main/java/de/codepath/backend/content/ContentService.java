package de.codepath.backend.content;

import de.codepath.backend.engine.AttemptRules;
import de.codepath.backend.engine.TaskSpec;
import de.codepath.backend.engine.TaskType;
import de.codepath.backend.platform.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.*;

/**
 * Lesende Schnittstelle des content-Pakets für alle anderen Pakete.
 *
 * <p>Module und veröffentlichte Aufgaben braucht fast jede Anfrage (Übersicht, Aufgabe, Abgabe,
 * Freigaben, CodePaths), ändern sich aber nur im Editor oder beim Import. Sie werden darum als ein
 * Schnappschuss im Speicher gehalten (zwei Abfragen statt einer je Modul und Anfrage). Jede Änderung an
 * Modulen, Aufgaben oder Versionen macht ihn ungültig ({@link ContentChanges}); zur Sicherheit wird er
 * außerdem nach {@link #MAX_AGE} neu geladen. Die gelieferten Entitäten sind losgelöst und nur zum Lesen da.
 *
 * <p>Geladen wird in einer eigenen Transaktion, damit die geteilten Objekte nie zum Persistenzkontext einer
 * Anfrage gehören. Während neu geladen wird, bekommen andere Anfragen den bisherigen Stand, statt zu warten
 * (kein Stau an Datenbankverbindungen, wenn die Lehrkraft mitten im Unterricht veröffentlicht). Eine
 * Transaktion, die selbst Inhalte geändert hat, liest am Cache vorbei ihren eigenen, unbestätigten Stand.
 */
@Service
public class ContentService {

    static final Duration MAX_AGE = Duration.ofMinutes(10);

    private final ModuleRepository moduleRepository;
    private final TaskRepository taskRepository;
    private final TaskVersionRepository versionRepository;
    private final TransactionTemplate separate;
    private final java.util.concurrent.locks.ReentrantLock loading = new java.util.concurrent.locks.ReentrantLock();

    private volatile Snapshot snapshot;

    public ContentService(ModuleRepository moduleRepository, TaskRepository taskRepository,
                          TaskVersionRepository versionRepository, PlatformTransactionManager transactionManager) {
        this.moduleRepository = moduleRepository;
        this.taskRepository = taskRepository;
        this.versionRepository = versionRepository;
        this.separate = new TransactionTemplate(transactionManager);
        this.separate.setReadOnly(true);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * @param withVersion alle Aufgaben mit veröffentlichter Version, auch archivierte (für Zeugnisse, Statistik)
     * @param bySlug      sichtbare Aufgaben unter „modul/aufgabe“
     */
    private record Snapshot(long generation, long loadedAt, List<Module> modules, Map<String, Module> modulesBySlug,
                            Map<Long, Module> modulesById, Map<Long, List<PublishedTask>> publishedByModule,
                            List<PublishedTask> allPublished, Map<Long, PublishedTask> withVersion,
                            Map<String, PublishedTask> bySlug) {
    }

    public static TaskSpec spec(TaskType type, TaskVersion version) {
        return new TaskSpec(type, version.getPoints(), version.getConfig(),
                AttemptRules.from(type, version.getRules()), version.getHints());
    }

    public List<Module> modules() {
        return new ArrayList<>(snapshot().modules());
    }

    public Module module(String slug) {
        Module module = slug == null ? null : snapshot().modulesBySlug().get(slug);
        if (module == null) throw ApiException.notFound("Modul nicht gefunden.");
        return module;
    }

    public Module module(Long id) {
        Module module = id == null ? null : snapshot().modulesById().get(id);
        if (module == null) throw ApiException.notFound("Modul nicht gefunden.");
        return module;
    }

    /** Sichtbare Aufgaben eines Moduls: veröffentlicht und nicht archiviert. */
    public List<PublishedTask> publishedTasks(Module module) {
        return new ArrayList<>(snapshot().publishedByModule().getOrDefault(module.getId(), List.of()));
    }

    public List<PublishedTask> allPublishedTasks() {
        return new ArrayList<>(snapshot().allPublished());
    }

    public PublishedTask publishedTask(String moduleSlug, String taskSlug) {
        Snapshot current = snapshot();
        Module module = module(moduleSlug);
        PublishedTask task = current.bySlug().get(module.getSlug() + "/" + taskSlug);
        if (task == null) throw ApiException.notFound("Aufgabe nicht gefunden.");
        return task;
    }

    public PublishedTask publishedTask(Long taskId) {
        PublishedTask task = taskId == null ? null : snapshot().withVersion().get(taskId);
        if (task == null) throw ApiException.notFound("Aufgabe nicht gefunden.");
        return task;
    }

    public Optional<TaskVersion> version(Long versionId) {
        return versionRepository.findById(versionId);
    }

    private Snapshot snapshot() {
        if (ContentChanges.changedInCurrentTransaction()) return read(ContentChanges.generation());
        Snapshot current = snapshot;
        if (fresh(current)) return current;
        // Lädt schon jemand neu, reicht bis dahin der bisherige Stand
        if (current != null && !loading.tryLock()) return current;
        if (current == null) loading.lock();
        try {
            current = snapshot;
            if (fresh(current)) return current;
            // Stand vor dem Lesen merken: Ändert sich währenddessen etwas, gilt der Schnappschuss sofort als alt
            long generation = ContentChanges.generation();
            current = separate.execute(status -> read(generation));
            snapshot = current;
            return current;
        } finally {
            loading.unlock();
        }
    }

    /** Für Tests und nach Änderungen an der Datenbank vorbei: beim nächsten Zugriff neu laden. */
    public void evictCache() {
        snapshot = null;
    }

    private static boolean fresh(Snapshot s) {
        return s != null && s.generation() == ContentChanges.generation()
                && System.nanoTime() - s.loadedAt() < MAX_AGE.toNanos();
    }

    private Snapshot read(long generation) {
        long loadedAt = System.nanoTime();
        List<Module> modules = moduleRepository.findAllByOrderBySortOrderAsc();
        Map<String, Module> bySlug = new HashMap<>();
        Map<Long, Module> byId = new HashMap<>();
        for (Module m : modules) {
            bySlug.put(m.getSlug(), m);
            byId.put(m.getId(), m);
        }
        Map<Long, List<PublishedTask>> byModule = new HashMap<>();
        List<PublishedTask> all = new ArrayList<>();
        Map<Long, PublishedTask> withVersion = new HashMap<>();
        Map<String, PublishedTask> tasksBySlug = new HashMap<>();
        for (Task t : taskRepository.findAllWithVersion()) {
            if (t.getCurrentVersion() == null) continue;
            Module module = byId.getOrDefault(t.getModule().getId(), t.getModule());
            PublishedTask published = new PublishedTask(t, module, t.getCurrentVersion());
            withVersion.put(t.getId(), published);
            if (t.isArchived()) continue;
            all.add(published);
            byModule.computeIfAbsent(module.getId(), k -> new ArrayList<>()).add(published);
            tasksBySlug.put(module.getSlug() + "/" + t.getSlug(), published);
        }
        byModule.replaceAll((k, v) -> List.copyOf(v));
        return new Snapshot(generation, loadedAt, List.copyOf(modules), Map.copyOf(bySlug), Map.copyOf(byId),
                Map.copyOf(byModule), List.copyOf(all), Map.copyOf(withVersion), Map.copyOf(tasksBySlug));
    }
}

package de.codepath.backend.pathway;

import de.codepath.backend.classroom.AccessPolicy;
import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.Module;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.identity.User;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.progress.ProgressService;
import de.codepath.backend.progress.ProgressStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lernpfade: Die Lehrkraft zeichnet eine Karte aus Modulen, Aufgaben und Meilensteinen; Schüler sehen darauf
 * ihren Stand und die empfohlene nächste Aufgabe. Ohne veröffentlichten Lernpfad empfiehlt CodePath die nächste
 * offene Aufgabe in der Reihenfolge der Module – bevorzugt in einem Modul, das schon begonnen ist.
 */
@Service
@RequiredArgsConstructor
public class LearningPathService {

    /**
     * Stand einer Station aus Sicht der Person: DONE erledigt, AVAILABLE als Nächstes dran (alle Vorgänger
     * erledigt), UPCOMING später (Vorgänger offen, trotzdem schon bearbeitbar), CLOSED Frist abgelaufen,
     * LOCKED (noch) nicht freigegeben.
     */
    public enum NodeState { DONE, AVAILABLE, UPCOMING, CLOSED, LOCKED }

    public record PathSummary(Long id, String slug, String title, String description, boolean published, int stations,
                              LocalDateTime updatedAt) {
    }

    public record PathAdmin(Long id, String slug, String title, String description, boolean published,
                            PathGraph.Graph graph, LocalDateTime updatedAt) {
    }

    public record PathUpdate(String title, String slug, String description, Boolean published, PathGraph.Graph graph) {
    }

    public record PathLink(String slug, String title, String description) {
    }

    /** href: Ziel beim Anklicken, null bei gesperrten Stationen und Meilensteinen */
    public record NodeView(String id, PathGraph.NodeKind kind, String label, String icon, int x, int y, NodeState state,
                           String href, int done, int total, boolean recommended) {
    }

    public record PathView(String slug, String title, String description, List<NodeView> nodes, List<PathGraph.Edge> edges,
                           int stationsDone, int stations, Recommendation recommendation) {
    }

    /** Empfohlene nächste Aufgabe mit kurzer Begründung; pathSlug ist null, wenn sie nicht aus einem Lernpfad stammt. */
    public record Recommendation(String moduleSlug, String moduleTitle, String moduleIcon, String taskSlug, String taskTitle,
                                 String reason, String pathSlug, String pathTitle) {
    }

    private final LearningPathRepository repository;
    private final ContentService contentService;
    private final AccessPolicy accessPolicy;
    private final ProgressService progressService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // ---------------------------------------------------------------- Lehrkraft

    @Transactional(readOnly = true)
    public List<PathSummary> all() {
        return repository.findAllByOrderByPositionAscIdAsc().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public PathAdmin get(Long id) {
        return admin(find(id));
    }

    /** Neuer Lernpfad, vorbelegt mit allen Modulen in ihrer Reihenfolge als Kette – zum Umbauen. */
    @Transactional
    public PathAdmin create(String title, String slug) {
        LearningPath path = new LearningPath();
        path.setTitle(title(title));
        path.setSlug(uniqueSlug(slug(slug), null));
        path.setGraph(write(starter()));
        path.setPosition(repository.findAllByOrderByPositionAscIdAsc().stream().mapToInt(LearningPath::getPosition).max().orElse(-1) + 1);
        repository.save(path);
        auditService.log("LEARNING_PATH_CREATED", path.getSlug(), Map.of());
        return admin(path);
    }

    @Transactional
    public PathAdmin update(Long id, PathUpdate update) {
        LearningPath path = find(id);
        if (update.title() != null) path.setTitle(title(update.title()));
        if (update.slug() != null) path.setSlug(uniqueSlug(slug(update.slug()), path.getId()));
        if (update.description() != null) path.setDescription(description(update.description()));
        if (update.graph() != null) path.setGraph(write(checked(update.graph())));
        if (update.published() != null) path.setPublished(update.published());
        path.setUpdatedAt(LocalDateTime.now());
        auditService.log("LEARNING_PATH_UPDATED", path.getSlug(), Map.of());
        return admin(path);
    }

    @Transactional
    public void delete(Long id) {
        LearningPath path = find(id);
        repository.delete(path);
        auditService.log("LEARNING_PATH_DELETED", path.getSlug(), Map.of());
    }

    /** Reihenfolge der Liste; der erste veröffentlichte Lernpfad bestimmt die Empfehlung. */
    @Transactional
    public void reorder(List<Long> ids) {
        Map<Long, LearningPath> paths = repository.findAll().stream().collect(Collectors.toMap(LearningPath::getId, Function.identity()));
        int position = 0;
        for (Long id : ids) {
            LearningPath path = paths.get(id);
            if (path != null) path.setPosition(position++);
        }
    }

    /** Form prüfen und sicherstellen, dass alle Module und Aufgaben existieren. */
    PathGraph.Graph checked(PathGraph.Graph graph) {
        PathGraph.validate(graph);
        Map<String, Module> modules = contentService.modules().stream().collect(Collectors.toMap(Module::getSlug, Function.identity()));
        Map<String, Set<String>> tasks = new HashMap<>();
        for (PathGraph.Node node : graph.nodes()) {
            if (node.kind() == PathGraph.NodeKind.MILESTONE) continue;
            Module module = modules.get(node.module());
            if (module == null) throw ApiException.badRequest("Das Modul „" + node.module() + "“ gibt es nicht.");
            if (node.kind() == PathGraph.NodeKind.TASK) {
                Set<String> slugs = tasks.computeIfAbsent(module.getSlug(), m -> contentService.publishedTasks(module).stream()
                        .map(t -> t.task().getSlug()).collect(Collectors.toSet()));
                if (!slugs.contains(node.task())) {
                    throw ApiException.badRequest("Die Aufgabe „" + node.module() + "/" + node.task() + "“ gibt es nicht oder sie ist nicht veröffentlicht.");
                }
            }
        }
        return graph;
    }

    private PathGraph.Graph starter() {
        List<PathGraph.Node> nodes = new ArrayList<>();
        List<PathGraph.Edge> edges = new ArrayList<>();
        int i = 0;
        for (Module module : contentService.modules()) {
            // Schlangenlinie: drei Stationen je Reihe, jede zweite Reihe von rechts nach links
            int row = i / 3, column = row % 2 == 0 ? i % 3 : 2 - i % 3;
            String id = "m" + (i + 1);
            nodes.add(new PathGraph.Node(id, PathGraph.NodeKind.MODULE, module.getSlug(), null, null, 60 + column * 260, 60 + row * 160));
            if (i > 0) edges.add(new PathGraph.Edge("m" + i, id));
            i++;
        }
        return new PathGraph.Graph(nodes, edges);
    }

    // ---------------------------------------------------------------- Schüler

    @Transactional(readOnly = true)
    public List<PathLink> published() {
        return repository.findAllByOrderByPositionAscIdAsc().stream().filter(LearningPath::isPublished)
                .map(p -> new PathLink(p.getSlug(), p.getTitle(), p.getDescription())).toList();
    }

    /** Karte mit Stand; nicht veröffentlichte Lernpfade sieht nur die Lehrkraft (Vorschau). */
    @Transactional(readOnly = true)
    public PathView view(User user, String slug) {
        LearningPath path = repository.findBySlug(slug).filter(p -> p.isPublished() || user.isTeacher())
                .orElseThrow(() -> ApiException.notFound("Lernpfad nicht gefunden."));
        return evaluate(path, new Situation(user));
    }

    /** Die eine empfohlene nächste Aufgabe – aus dem ersten veröffentlichten Lernpfad, sonst aus der Modulreihenfolge. */
    @Transactional(readOnly = true)
    public Recommendation next(User user) {
        Situation situation = new Situation(user);
        // Ist der Lernpfad geschafft, geht es mit den übrigen offenen Aufgaben weiter
        return repository.findAllByOrderByPositionAscIdAsc().stream().filter(LearningPath::isPublished).findFirst()
                .map(path -> evaluate(path, situation).recommendation())
                .orElseGet(() -> fallback(situation));
    }

    private PathView evaluate(LearningPath path, Situation s) {
        PathGraph.Graph graph = read(path.getGraph());
        // Stationen, deren Modul oder Aufgabe es nicht mehr gibt, fallen weg
        List<PathGraph.Node> nodes = graph.nodes().stream().filter(s::exists).toList();
        Set<String> ids = nodes.stream().map(PathGraph.Node::id).collect(Collectors.toSet());
        List<PathGraph.Edge> edges = graph.edges().stream().filter(e -> ids.contains(e.from()) && ids.contains(e.to())).toList();
        PathGraph.Graph live = new PathGraph.Graph(nodes, edges);
        Map<String, List<String>> predecessors = PathGraph.predecessors(live);

        Map<String, NodeState> states = new HashMap<>();
        Map<String, Station> stations = new HashMap<>();
        // clear: Station und alles davor ist erledigt oder gar nicht machbar – erst dann geht es dahinter weiter
        Set<String> clear = new HashSet<>();
        for (PathGraph.Node node : PathGraph.order(live)) {
            boolean ready = clear.containsAll(predecessors.getOrDefault(node.id(), List.of()));
            NodeState state;
            if (node.kind() == PathGraph.NodeKind.MILESTONE) {
                state = ready ? NodeState.DONE : NodeState.UPCOMING;
            } else {
                Station station = s.station(node);
                stations.put(node.id(), station);
                state = station.state() == NodeState.AVAILABLE && !ready ? NodeState.UPCOMING : station.state();
            }
            states.put(node.id(), state);
            if (ready && settled(state)) clear.add(node.id());
        }

        String recommendedId = null;
        Recommendation recommendation = null;
        for (PathGraph.Node node : PathGraph.order(live)) {
            Station station = stations.get(node.id());
            if (states.get(node.id()) != NodeState.AVAILABLE || station == null || station.next() == null) continue;
            recommendedId = node.id();
            PublishedTask task = station.next();
            String reason = node.kind() == PathGraph.NodeKind.TASK
                    ? "Nächste Station auf dem Lernpfad „" + path.getTitle() + "“."
                    : "Nächste Station auf dem Lernpfad „" + path.getTitle() + "“: Modul „" + task.module().getTitle() + "“.";
            recommendation = recommendation(task, reason, path);
            break;
        }

        List<NodeView> views = new ArrayList<>();
        int done = 0, total = 0;
        for (PathGraph.Node node : nodes) {
            NodeState state = states.getOrDefault(node.id(), NodeState.LOCKED);
            Station station = stations.get(node.id());
            if (node.kind() != PathGraph.NodeKind.MILESTONE) {
                total++;
                if (state == NodeState.DONE) done++;
            }
            views.add(new NodeView(node.id(), node.kind(), s.label(node), s.icon(node), node.x(), node.y(), state,
                    station == null || state == NodeState.LOCKED ? null : station.href(),
                    station == null ? 0 : station.done(), station == null ? 0 : station.total(), node.id().equals(recommendedId)));
        }
        return new PathView(path.getSlug(), path.getTitle(), path.getDescription(), views, edges, done, total, recommendation);
    }

    /** Ein Vorgänger, der erledigt ist oder gar nicht erledigt werden kann, hält niemanden auf. */
    private static boolean settled(NodeState state) {
        return state == NodeState.DONE || state == NodeState.LOCKED || state == NodeState.CLOSED;
    }

    private Recommendation fallback(Situation s) {
        Station started = null, first = null;
        for (Module module : s.modules()) {
            Station station = s.moduleStation(module);
            if (station.state() != NodeState.AVAILABLE || station.next() == null) continue;
            if (first == null) first = station;
            if (started == null && station.done() > 0) started = station;
        }
        if (started != null) {
            return recommendation(started.next(), "Du hast im Modul „" + started.next().module().getTitle() + "“ schon angefangen – mach dort weiter.", null);
        }
        if (first != null) {
            return recommendation(first.next(), "Als Nächstes dran: Modul „" + first.next().module().getTitle() + "“.", null);
        }
        return null;
    }

    private static Recommendation recommendation(PublishedTask task, String reason, LearningPath path) {
        return new Recommendation(task.module().getSlug(), task.module().getTitle(), task.module().getIcon(), task.task().getSlug(),
                task.version().getTitle(), reason, path == null ? null : path.getSlug(), path == null ? null : path.getTitle());
    }

    /** Stand einer Modul- oder Aufgabenstation; next: die Aufgabe, mit der es weitergeht. */
    private record Station(NodeState state, String href, int done, int total, PublishedTask next) {
    }

    /** Freigaben und Fortschritt einer Person, einmal geladen für viele Stationen. */
    private final class Situation {
        private final AccessPolicy.Releases releases;
        private final Map<Long, ProgressStatus> status;
        private final Map<String, Module> modules = new LinkedHashMap<>();
        private final Map<String, List<PublishedTask>> tasks = new HashMap<>();

        Situation(User user) {
            this.releases = accessPolicy.releases(user);
            this.status = progressService.statusByTask(user.getId());
            contentService.modules().forEach(m -> modules.put(m.getSlug(), m));
        }

        Collection<Module> modules() {
            return modules.values();
        }

        List<PublishedTask> tasks(Module module) {
            return tasks.computeIfAbsent(module.getSlug(), slug -> contentService.publishedTasks(module));
        }

        Optional<PublishedTask> task(String moduleSlug, String taskSlug) {
            Module module = modules.get(moduleSlug);
            if (module == null) return Optional.empty();
            return tasks(module).stream().filter(t -> t.task().getSlug().equals(taskSlug)).findFirst();
        }

        boolean exists(PathGraph.Node node) {
            return switch (node.kind()) {
                case MILESTONE -> true;
                case MODULE -> modules.containsKey(node.module());
                case TASK -> task(node.module(), node.task()).isPresent();
            };
        }

        /** Erledigt heißt: nichts mehr zu tun – gelöst, eingereicht, geübt oder alle Versuche verbraucht. */
        boolean finished(PublishedTask task) {
            ProgressStatus s = status.get(task.taskId());
            return s != null && s != ProgressStatus.OPEN;
        }

        Station station(PathGraph.Node node) {
            if (node.kind() == PathGraph.NodeKind.MODULE) return moduleStation(modules.get(node.module()));
            PublishedTask task = task(node.module(), node.task()).orElseThrow();
            AccessPolicy.ModuleAccess access = releases.taskAccess(task);
            String href = "/modules/" + task.module().getSlug() + "/" + task.task().getSlug();
            boolean finished = finished(task);
            if (!access.visible()) return new Station(NodeState.LOCKED, null, 0, 1, null);
            if (finished) return new Station(NodeState.DONE, href, 1, 1, null);
            if (!access.open()) return new Station(NodeState.CLOSED, href, 0, 1, null);
            return new Station(NodeState.AVAILABLE, href, 0, 1, task);
        }

        Station moduleStation(Module module) {
            List<PublishedTask> all = tasks(module);
            AccessPolicy.ModuleAccess access = releases.moduleAccess(module, all);
            List<PublishedTask> visible = releases.visible(all);
            String href = "/modules/" + module.getSlug();
            if (!access.visible() || visible.isEmpty()) return new Station(NodeState.LOCKED, null, 0, visible.size(), null);
            int done = (int) visible.stream().filter(this::finished).count();
            if (done == visible.size()) return new Station(NodeState.DONE, href, done, visible.size(), null);
            if (!access.open()) return new Station(NodeState.CLOSED, href, done, visible.size(), null);
            PublishedTask next = visible.stream().filter(t -> !finished(t) && releases.taskAccess(t).open()).findFirst().orElse(null);
            return new Station(next == null ? NodeState.CLOSED : NodeState.AVAILABLE, href, done, visible.size(), next);
        }

        String label(PathGraph.Node node) {
            if (node.label() != null && !node.label().isBlank()) return node.label();
            if (node.kind() == PathGraph.NodeKind.MODULE) return modules.get(node.module()).getTitle();
            return task(node.module(), node.task()).map(t -> t.version().getTitle()).orElse(node.task());
        }

        String icon(PathGraph.Node node) {
            if (node.kind() == PathGraph.NodeKind.MILESTONE) return "🏁";
            Module module = modules.get(node.module());
            return module == null ? "" : module.getIcon();
        }
    }

    // ---------------------------------------------------------------- Hilfen

    LearningPath find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Lernpfad nicht gefunden."));
    }

    PathGraph.Graph read(String json) {
        try {
            return objectMapper.readValue(json, PathGraph.Graph.class);
        } catch (JacksonException e) {
            return PathGraph.Graph.empty();
        }
    }

    String write(PathGraph.Graph graph) {
        return objectMapper.writeValueAsString(graph);
    }

    String uniqueSlug(String slug, Long ownId) {
        repository.findBySlug(slug).filter(p -> !p.getId().equals(ownId)).ifPresent(p -> {
            throw ApiException.conflict("Die Adresse „" + slug + "“ ist schon vergeben.");
        });
        return slug;
    }

    static String slug(String slug) {
        if (slug == null || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 80) {
            throw ApiException.badRequest("Die Adresse darf nur Kleinbuchstaben, Ziffern und Bindestriche enthalten (höchstens 80 Zeichen).");
        }
        return slug;
    }

    static String title(String title) {
        if (title == null || title.isBlank()) throw ApiException.badRequest("Bitte einen Titel angeben.");
        if (title.strip().length() > 200) throw ApiException.badRequest("Der Titel ist zu lang (höchstens 200 Zeichen).");
        return title.strip();
    }

    static String description(String description) {
        String value = description == null ? "" : description.strip();
        if (value.length() > 500) throw ApiException.badRequest("Die Beschreibung ist zu lang (höchstens 500 Zeichen).");
        return value;
    }

    private PathSummary summary(LearningPath p) {
        return new PathSummary(p.getId(), p.getSlug(), p.getTitle(), p.getDescription(), p.isPublished(), read(p.getGraph()).nodes().size(),
                p.getUpdatedAt());
    }

    private PathAdmin admin(LearningPath p) {
        return new PathAdmin(p.getId(), p.getSlug(), p.getTitle(), p.getDescription(), p.isPublished(), read(p.getGraph()), p.getUpdatedAt());
    }
}

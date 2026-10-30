package de.codepath.backend.pathway;

import de.codepath.backend.classroom.AccessPolicy;
import de.codepath.backend.classroom.PathGate;
import de.codepath.backend.classroom.ReleaseService;
import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.Module;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.CourseService;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
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
 *
 * <p>Ein Lernpfad gilt für alle Kurse oder für einen Kurs. Ein veröffentlichter Kurs-Lernpfad wird beim Speichern
 * in die Modul-Steuerung des Kurses übernommen: Seine Module werden freigegeben, nicht genutzte Aufgaben darin
 * einzeln gesperrt. Ist der Lernpfad verbindlich ({@link LearningPath#isEnforce()}), setzt {@link PathGate}
 * Pflicht-Verbindungen und die Einstellung für Module außerhalb des Pfads serverseitig über {@link AccessPolicy} durch.
 */
@Service
@RequiredArgsConstructor
public class LearningPathService implements PathGate {

    /**
     * Stand einer Station aus Sicht der Person: DONE erledigt, AVAILABLE als Nächstes dran (alle Vorgänger
     * erledigt), BLOCKED durch offene Pflicht-Vorgänger im verbindlichen Pfad gesperrt, UPCOMING später
     * (Empfehlungs-Vorgänger offen, trotzdem schon bearbeitbar), CLOSED Frist abgelaufen,
     * LOCKED (noch) nicht von der Lehrkraft freigegeben.
     */
    public enum NodeState { DONE, AVAILABLE, BLOCKED, UPCOMING, CLOSED, LOCKED }

    /** courseId/courseName: Kurs, für den der Lernpfad gilt; null für alle Kurse */
    public record PathSummary(Long id, String slug, String title, String description, boolean published,
                              boolean enforce, int stations, Long courseId, String courseName, LocalDateTime updatedAt) {
    }

    /** applied: was beim Speichern in die Modul-Steuerung des Kurses übernommen wurde */
    public record PathAdmin(Long id, String slug, String title, String description, boolean published,
                            boolean enforce, PathGraph.Graph graph, Long courseId, String courseName,
                            boolean lockOthers, String unlockRestAfter, List<String> applied,
                            List<String> warnings, LocalDateTime updatedAt) {
    }

    /**
     * Fehlende Felder bleiben unverändert.
     *
     * @param courseId        Kurs; 0 für alle Kurse
     * @param unlockRestAfter Station-ID oder leerer String zum Aufheben
     */
    public record PathUpdate(String title, String slug, String description, Boolean published, Boolean enforce,
                             PathGraph.Graph graph, Long courseId, Boolean lockOthers, String unlockRestAfter) {
    }

    public record PathLink(String slug, String title, String description) {
    }

    /** href: Ziel beim Anklicken, null bei gesperrten/blockierten Stationen und Meilensteinen */
    public record NodeView(String id, PathGraph.NodeKind kind, String label, String icon, int x, int y, NodeState state,
                           String href, int done, int total, boolean recommended, String reason) {
    }

    public record PathView(String slug, String title, String description, boolean enforce,
                           List<NodeView> nodes, List<PathGraph.Edge> edges,
                           int stationsDone, int stations, Recommendation recommendation) {
    }

    /** Empfohlene nächste Aufgabe mit kurzer Begründung; pathSlug ist null, wenn sie nicht aus einem Lernpfad stammt. */
    public record Recommendation(String moduleSlug, String moduleTitle, String moduleIcon, String taskSlug, String taskTitle,
                                 String reason, String pathSlug, String pathTitle) {
    }

    private final LearningPathRepository repository;
    private final ContentService contentService;
    private final AccessPolicy accessPolicy;
    private final ReleaseService releaseService;
    private final CourseService courseService;
    private final UserService userService;
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
        return admin(find(id), List.of());
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
        return admin(path, List.of());
    }

    @Transactional
    public PathAdmin update(Long id, PathUpdate update) {
        LearningPath path = find(id);
        if (update.title() != null) path.setTitle(title(update.title()));
        if (update.slug() != null) path.setSlug(uniqueSlug(slug(update.slug()), path.getId()));
        if (update.description() != null) path.setDescription(description(update.description()));
        if (update.graph() != null) path.setGraph(write(checked(update.graph())));
        if (update.published() != null) path.setPublished(update.published());
        if (update.enforce() != null) path.setEnforce(update.enforce());
        if (update.courseId() != null) path.setCourseId(update.courseId() <= 0 ? null : courseService.get(update.courseId()).getId());
        if (update.lockOthers() != null) path.setLockOthers(update.lockOthers());
        if (update.unlockRestAfter() != null) {
            path.setUnlockRestAfter(update.unlockRestAfter().isBlank() ? null : update.unlockRestAfter().strip());
        }
        validateUnlockRestAfter(read(path.getGraph()), path.getUnlockRestAfter());
        validateSingleEnforced(path);
        path.setUpdatedAt(LocalDateTime.now());
        auditService.log("LEARNING_PATH_UPDATED", path.getSlug(), Map.of());
        return admin(path, applyToCourse(path));
    }

    void validateUnlockRestAfter(PathGraph.Graph graph, String unlockRestAfter) {
        if (unlockRestAfter == null || unlockRestAfter.isBlank()) return;
        boolean exists = graph.nodes().stream().anyMatch(n -> n.id().equals(unlockRestAfter));
        if (!exists) {
            throw ApiException.badRequest("Die Station „" + unlockRestAfter + "“ für die Freigabe der übrigen Module gibt es im Lernpfad nicht.");
        }
    }

    void validateSingleEnforced(LearningPath path) {
        if (!path.isEnforce()) return;
        for (LearningPath other : repository.findAll()) {
            if (other.getId().equals(path.getId()) || !other.isEnforce()) continue;
            if (Objects.equals(other.getCourseId(), path.getCourseId())
                    || other.getCourseId() == null || path.getCourseId() == null) {
                throw ApiException.conflict("Pro Kurs ist höchstens ein verbindlicher Lernpfad möglich (bereits aktiv: „" + other.getTitle() + "“).");
            }
        }
    }

    /** Veröffentlichter Kurs-Lernpfad → Modul-Steuerung des Kurses; sonst ändert sich an den Freigaben nichts. */
    private List<String> applyToCourse(LearningPath path) {
        if (!path.isPublished() || path.getCourseId() == null) return List.of();
        PathGraph.Graph graph = read(path.getGraph());
        boolean unlockLater = path.isEnforce() && path.getUnlockRestAfter() != null && !path.getUnlockRestAfter().isBlank();
        Map<String, Set<String>> modulePlan = plan(graph);
        if (unlockLater) {
            // Bei „Frei ab Station X“ regelt PathGate die Aufgaben-Sichtbarkeit vor und nach X ohne task_releases
            Map<String, Set<String>> openModules = new LinkedHashMap<>();
            modulePlan.keySet().forEach(m -> openModules.put(m, null));
            return releaseService.applyPlan(path.getCourseId(), openModules, false);
        }
        return releaseService.applyPlan(path.getCourseId(), modulePlan, path.isLockOthers());
    }

    /** Welche Aufgaben je Modul der Lernpfad nutzt: null für alle, sonst die Slugs aus allen Stationen des Moduls. */
    static Map<String, Set<String>> plan(PathGraph.Graph graph) {
        Map<String, Set<String>> plan = new LinkedHashMap<>();
        for (PathGraph.Node node : graph.nodes()) {
            if (node.kind() == PathGraph.NodeKind.MILESTONE) continue;
            boolean all = node.kind() == PathGraph.NodeKind.MODULE && !node.partial();
            if (plan.containsKey(node.module()) && plan.get(node.module()) == null) continue;
            if (all) {
                plan.put(node.module(), null);
                continue;
            }
            Set<String> tasks = plan.computeIfAbsent(node.module(), m -> new LinkedHashSet<>());
            if (node.kind() == PathGraph.NodeKind.TASK) tasks.add(node.task());
            else tasks.addAll(node.tasks());
        }
        return plan;
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
            Set<String> slugs = tasks.computeIfAbsent(module.getSlug(), m -> contentService.publishedTasks(module).stream()
                    .map(t -> t.task().getSlug()).collect(Collectors.toSet()));
            List<String> wanted = node.kind() == PathGraph.NodeKind.TASK ? List.of(node.task())
                    : node.partial() ? node.tasks() : List.of();
            for (String task : wanted) {
                if (!slugs.contains(task)) {
                    throw ApiException.badRequest("Die Aufgabe „" + node.module() + "/" + task + "“ gibt es nicht oder sie ist nicht veröffentlicht.");
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
            nodes.add(new PathGraph.Node(id, PathGraph.NodeKind.MODULE, module.getSlug(), null, null, null, 60 + column * 260, 60 + row * 160));
            if (i > 0) edges.add(new PathGraph.Edge("m" + i, id));
            i++;
        }
        return new PathGraph.Graph(nodes, edges);
    }

    // ---------------------------------------------------------------- Schüler & PathGate

    /** Veröffentlichte Lernpfade dieser Person: zuerst die ihres Kurses, dann die für alle Kurse. */
    @Transactional(readOnly = true)
    public List<PathLink> published(User user) {
        return publishedFor(user).stream().map(p -> new PathLink(p.getSlug(), p.getTitle(), p.getDescription())).toList();
    }

    private List<LearningPath> publishedFor(User user) {
        Long courseId = user.isTeacher() ? null : userService.courseOf(user.getId()).map(Course::getId).orElse(null);
        return repository.findAllByOrderByPositionAscIdAsc().stream()
                .filter(LearningPath::isPublished)
                .filter(p -> user.isTeacher() || p.getCourseId() == null || p.getCourseId().equals(courseId))
                .sorted(Comparator.comparing((LearningPath p) -> p.getCourseId() == null)
                        .thenComparing(p -> !p.isEnforce()))
                .toList();
    }

    private Optional<LearningPath> enforcedFor(User user) {
        if (user.isTeacher()) return Optional.empty();
        Long courseId = userService.courseOf(user.getId()).filter(Course::isActive).map(Course::getId).orElse(null);
        if (courseId == null) return Optional.empty();
        return repository.findAllByOrderByPositionAscIdAsc().stream()
                .filter(LearningPath::isPublished)
                .filter(LearningPath::isEnforce)
                .filter(p -> p.getCourseId() == null || p.getCourseId().equals(courseId))
                .sorted(Comparator.comparing((LearningPath p) -> p.getCourseId() == null))
                .findFirst();
    }

    /** Karte mit Stand; nicht veröffentlichte Lernpfade und die anderer Kurse sieht nur die Lehrkraft (Vorschau). */
    @Transactional(readOnly = true)
    public PathView view(User user, String slug) {
        return view(user, slug, null);
    }

    /**
     * Karte mit Stand; die Lehrkraft kann optional den Benutzernamen einer Schülerin oder eines Schülers
     * übergeben, um den Lernpfad aus deren Sicht zu prüfen.
     */
    @Transactional(readOnly = true)
    public PathView view(User actor, String slug, String studentUsername) {
        LearningPath path = repository.findBySlug(slug)
                .filter(p -> actor.isTeacher() || publishedFor(actor).stream().anyMatch(q -> q.getId().equals(p.getId())))
                .orElseThrow(() -> ApiException.notFound("Lernpfad nicht gefunden."));
        User target = actor;
        if (studentUsername != null && !studentUsername.isBlank()) {
            if (!actor.isTeacher()) throw ApiException.forbidden("Nur für Lehrkräfte.");
            target = userService.byUsername(studentUsername.strip());
        }
        return evaluate(path, new Situation(target, accessPolicy.courseReleases(target))).view();
    }

    /** Die eine empfohlene nächste Aufgabe – aus dem ersten passenden Lernpfad, sonst aus der Modulreihenfolge. */
    @Transactional(readOnly = true)
    public Recommendation next(User user) {
        List<LearningPath> paths = publishedFor(user);
        if (!paths.isEmpty()) {
            Situation baseSituation = new Situation(user, accessPolicy.courseReleases(user));
            Recommendation fromPath = evaluate(paths.getFirst(), baseSituation).view().recommendation();
            if (fromPath != null) return fromPath;
        }
        // Ist der Lernpfad geschafft (oder gibt es keinen), geht es mit den übrigen zugänglichen Aufgaben weiter
        return fallback(new Situation(user, accessPolicy.releases(user)));
    }

    @Override
    @Transactional(readOnly = true)
    public PathGate.Evaluation evaluate(User user, AccessPolicy.Releases baseReleases) {
        Optional<LearningPath> enforced = enforcedFor(user);
        if (enforced.isEmpty()) return PathGate.NO_OP;
        LearningPath path = enforced.get();
        Situation situation = new Situation(user, baseReleases);
        EvaluatedPath evaluated = evaluate(path, situation);

        Map<String, List<PathGraph.Node>> taskNodes = new HashMap<>();
        Set<String> modulesInPath = new HashSet<>();
        for (PathGraph.Node node : evaluated.live().nodes()) {
            if (node.kind() == PathGraph.NodeKind.MILESTONE) continue;
            modulesInPath.add(node.module());
            if (node.kind() == PathGraph.NodeKind.TASK) {
                taskNodes.computeIfAbsent(node.module() + "/" + node.task(), k -> new ArrayList<>()).add(node);
            } else if (node.partial()) {
                for (String t : node.tasks()) {
                    taskNodes.computeIfAbsent(node.module() + "/" + t, k -> new ArrayList<>()).add(node);
                }
            } else {
                Module module = situation.module(node.module());
                if (module != null) {
                    for (PublishedTask t : situation.tasks(module)) {
                        taskNodes.computeIfAbsent(node.module() + "/" + t.task().getSlug(), k -> new ArrayList<>()).add(node);
                    }
                }
            }
        }

        String unlockAfter = path.getUnlockRestAfter();
        boolean hasUnlockAfter = unlockAfter != null && !unlockAfter.isBlank();
        boolean restUnlocked = hasUnlockAfter && evaluated.clear().contains(unlockAfter);
        String outsideReason;
        if (hasUnlockAfter && !restUnlocked) {
            PathGraph.Node unlockNode = evaluated.nodeById().get(unlockAfter);
            outsideReason = unlockNode != null
                    ? "Erst „" + situation.label(unlockNode) + "“ abschließen."
                    : "Diese Station wird erst später im Lernpfad freigeschaltet.";
        } else {
            outsideReason = "Nicht Teil des aktuellen Lernpfads.";
        }
        boolean lockOutside = (hasUnlockAfter && !restUnlocked) || (!hasUnlockAfter && path.isLockOthers());

        return new PathGate.Evaluation() {
            @Override
            public Decision task(PublishedTask task) {
                String key = task.module().getSlug() + "/" + task.task().getSlug();
                List<PathGraph.Node> nodes = taskNodes.get(key);
                if (nodes != null && !nodes.isEmpty()) {
                    // Bereits vor dem Einschalten von „Verbindlich“ erledigte Aufgaben bleiben gültig
                    if (situation.finished(task)) return Decision.OPEN;
                    for (PathGraph.Node n : nodes) {
                        NodeState state = evaluated.states().get(n.id());
                        if (state == NodeState.AVAILABLE || state == NodeState.DONE || state == NodeState.UPCOMING) {
                            return Decision.OPEN;
                        }
                    }
                    for (PathGraph.Node n : nodes) {
                        if (evaluated.states().get(n.id()) == NodeState.BLOCKED) {
                            return new Decision(State.BLOCKED, evaluated.reasons().get(n.id()));
                        }
                    }
                    return Decision.OPEN;
                }
                if (restUnlocked) return Decision.UNLOCKED;
                if (lockOutside) return new Decision(State.HIDDEN, outsideReason);
                return Decision.OPEN;
            }

            @Override
            public Decision module(Module module) {
                if (modulesInPath.contains(module.getSlug())) {
                    return Decision.OPEN;
                }
                if (restUnlocked) return Decision.UNLOCKED;
                if (lockOutside) return new Decision(State.HIDDEN, outsideReason);
                return Decision.OPEN;
            }
        };
    }

    private record EvaluatedPath(PathView view, PathGraph.Graph live, Map<String, PathGraph.Node> nodeById,
                                 Map<String, NodeState> states, Map<String, String> reasons, Set<String> clear) {
    }

    private EvaluatedPath evaluate(LearningPath path, Situation s) {
        PathGraph.Graph graph = read(path.getGraph());
        // Stationen, deren Modul oder Aufgabe es nicht mehr gibt, fallen weg
        List<PathGraph.Node> nodes = graph.nodes().stream().filter(s::exists).toList();
        Map<String, PathGraph.Node> nodeById = nodes.stream().collect(Collectors.toMap(PathGraph.Node::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Set<String> ids = nodeById.keySet();
        List<PathGraph.Edge> edges = graph.edges().stream().filter(e -> ids.contains(e.from()) && ids.contains(e.to())).toList();
        PathGraph.Graph live = new PathGraph.Graph(nodes, edges);
        Map<String, List<String>> predecessors = PathGraph.predecessors(live);
        Map<String, List<String>> requiredPredecessors = PathGraph.requiredPredecessors(live);

        Map<String, NodeState> states = new HashMap<>();
        Map<String, String> reasons = new HashMap<>();
        Map<String, Station> stations = new HashMap<>();
        // clear: Station und alles davor ist erledigt oder gar nicht machbar – erst dann geht es dahinter weiter
        Set<String> clear = new HashSet<>();
        for (PathGraph.Node node : PathGraph.order(live)) {
            List<String> allPreds = predecessors.getOrDefault(node.id(), List.of());
            List<String> reqPreds = path.isEnforce() ? requiredPredecessors.getOrDefault(node.id(), List.of()) : List.of();
            boolean allReady = clear.containsAll(allPreds);
            boolean requiredReady = clear.containsAll(reqPreds);
            if (!requiredReady) {
                List<String> openNames = reqPreds.stream()
                        .filter(id -> !clear.contains(id))
                        .map(id -> s.label(nodeById.get(id)))
                        .toList();
                reasons.put(node.id(), "Erst „" + String.join("“, „", openNames) + "“ abschließen.");
            }
            NodeState state;
            if (node.kind() == PathGraph.NodeKind.MILESTONE) {
                if (!requiredReady) {
                    state = NodeState.BLOCKED;
                } else if (path.isEnforce() && !reqPreds.isEmpty()) {
                    state = NodeState.DONE;
                } else {
                    state = allReady ? NodeState.DONE : NodeState.UPCOMING;
                }
            } else {
                Station station = s.station(node);
                stations.put(node.id(), station);
                if (station.state() == NodeState.LOCKED || station.state() == NodeState.DONE) {
                    state = station.state();
                } else if (!requiredReady) {
                    state = NodeState.BLOCKED;
                } else if (station.state() == NodeState.AVAILABLE && !allReady) {
                    state = NodeState.UPCOMING;
                } else {
                    state = station.state();
                }
            }
            states.put(node.id(), state);
            boolean canClear = state == NodeState.DONE
                    ? requiredReady
                    : (path.isEnforce() && !reqPreds.isEmpty() ? requiredReady : allReady) && settled(state);
            if (canClear) clear.add(node.id());
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
            String href = station == null || state == NodeState.LOCKED || state == NodeState.BLOCKED ? null : station.href();
            views.add(new NodeView(node.id(), node.kind(), s.label(node), s.icon(node), node.x(), node.y(), state,
                    href, station == null ? 0 : station.done(), station == null ? 0 : station.total(),
                    node.id().equals(recommendedId), state == NodeState.BLOCKED ? reasons.get(node.id()) : null));
        }
        PathView pathView = new PathView(path.getSlug(), path.getTitle(), path.getDescription(), path.isEnforce(),
                views, edges, done, total, recommendation);
        return new EvaluatedPath(pathView, live, nodeById, states, reasons, clear);
    }

    /** Ein Vorgänger, der erledigt ist oder gar nicht erledigt werden kann, hält niemanden auf. */
    private static boolean settled(NodeState state) {
        return state == NodeState.DONE || state == NodeState.LOCKED || state == NodeState.CLOSED;
    }

    private Recommendation fallback(Situation s) {
        Station started = null, first = null;
        for (Module module : s.modules()) {
            Station station = s.moduleStation(module, null);
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

        Situation(User user, AccessPolicy.Releases releases) {
            this.releases = releases;
            this.status = progressService.statusByTask(user.getId());
            contentService.modules().forEach(m -> modules.put(m.getSlug(), m));
        }

        Collection<Module> modules() {
            return modules.values();
        }

        Module module(String slug) {
            return modules.get(slug);
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
            if (node.kind() == PathGraph.NodeKind.MODULE) return moduleStation(modules.get(node.module()), node.tasks());
            PublishedTask task = task(node.module(), node.task()).orElseThrow();
            AccessPolicy.ModuleAccess access = releases.taskAccess(task);
            String href = "/modules/" + task.module().getSlug() + "/" + task.task().getSlug();
            boolean finished = finished(task);
            if (!access.visible()) return new Station(NodeState.LOCKED, null, 0, 1, null);
            if (finished) return new Station(NodeState.DONE, href, 1, 1, null);
            if (!access.open()) return new Station(NodeState.CLOSED, href, 0, 1, null);
            return new Station(NodeState.AVAILABLE, href, 0, 1, task);
        }

        /** @param selection nur diese Aufgaben des Moduls zählen; null für alle */
        Station moduleStation(Module module, List<String> selection) {
            List<PublishedTask> all = tasks(module);
            AccessPolicy.ModuleAccess access = releases.moduleAccess(module, all);
            List<PublishedTask> visible = releases.visible(all).stream()
                    .filter(t -> selection == null || selection.contains(t.task().getSlug())).toList();
            String href = "/modules/" + module.getSlug();
            if (!access.visible() || visible.isEmpty()) return new Station(NodeState.LOCKED, null, 0, visible.size(), null);
            int done = (int) visible.stream().filter(this::finished).count();
            if (done == visible.size()) return new Station(NodeState.DONE, href, done, visible.size(), null);
            if (!access.open()) return new Station(NodeState.CLOSED, href, done, visible.size(), null);
            PublishedTask next = visible.stream().filter(t -> !finished(t) && releases.taskAccess(t).open()).findFirst().orElse(null);
            return new Station(next == null ? NodeState.CLOSED : NodeState.AVAILABLE, href, done, visible.size(), next);
        }

        String label(PathGraph.Node node) {
            if (node == null) return "";
            if (node.label() != null && !node.label().isBlank()) return node.label();
            if (node.kind() == PathGraph.NodeKind.MODULE) {
                Module m = modules.get(node.module());
                String title = m != null ? m.getTitle() : node.module();
                return node.partial() ? title + " (" + node.tasks().size() + " Aufgaben)" : title;
            }
            return task(node.module(), node.task()).map(t -> t.version().getTitle()).orElse(node.task());
        }

        String icon(PathGraph.Node node) {
            if (node.kind() == PathGraph.NodeKind.MILESTONE) return "🏁";
            Module module = modules.get(node.module());
            return module == null ? "" : module.getIcon();
        }
    }

    // ---------------------------------------------------------------- Hilfen

    private List<String> warnings(LearningPath p, PathGraph.Graph graph) {
        List<String> result = new ArrayList<>();
        if (p.isEnforce() && !graph.edges().isEmpty() && graph.edges().stream().noneMatch(PathGraph.Edge::required)) {
            result.add("Der Lernpfad ist verbindlich, enthält aber nur Empfehlungs-Verbindungen (gestrichelt).");
        }
        if (p.getCourseId() != null && p.isPublished()) {
            AccessPolicy.Releases courseRel = accessPolicy.forCourse(p.getCourseId());
            Set<String> hasSuccessors = graph.edges().stream().map(PathGraph.Edge::from).collect(Collectors.toSet());
            Map<String, Module> modules = contentService.modules().stream().collect(Collectors.toMap(Module::getSlug, Function.identity()));
            for (PathGraph.Node node : graph.nodes()) {
                if (node.kind() == PathGraph.NodeKind.MILESTONE || !hasSuccessors.contains(node.id())) continue;
                Module m = modules.get(node.module());
                if (m == null) continue;
                List<PublishedTask> all = contentService.publishedTasks(m);
                AccessPolicy.ModuleAccess ma = courseRel.moduleAccess(m, all);
                String name = node.label() != null && !node.label().isBlank() ? node.label() : m.getTitle();
                if (!ma.visible()) {
                    result.add("Station „" + name + "“ ist im Kurs gesperrt und hält nachfolgende Stationen nicht auf.");
                } else if (!ma.open()) {
                    result.add("Station „" + name + "“: Die Bearbeitungszeit ist abgelaufen – sie hält nachfolgende Stationen nicht auf.");
                }
            }
        }
        return result;
    }

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
        return new PathSummary(p.getId(), p.getSlug(), p.getTitle(), p.getDescription(), p.isPublished(),
                p.isEnforce(), read(p.getGraph()).nodes().size(), p.getCourseId(), courseName(p), p.getUpdatedAt());
    }

    private PathAdmin admin(LearningPath p, List<String> applied) {
        PathGraph.Graph graph = read(p.getGraph());
        return new PathAdmin(p.getId(), p.getSlug(), p.getTitle(), p.getDescription(), p.isPublished(),
                p.isEnforce(), graph, p.getCourseId(), courseName(p), p.isLockOthers(), p.getUnlockRestAfter(),
                applied, warnings(p, graph), p.getUpdatedAt());
    }

    private String courseName(LearningPath p) {
        return p.getCourseId() == null ? null : courseService.get(p.getCourseId()).getName();
    }
}

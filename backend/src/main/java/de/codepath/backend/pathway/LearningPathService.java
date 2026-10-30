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
import de.codepath.backend.platform.events.RealtimeUpdateService;
import de.codepath.backend.progress.ProgressService;
import de.codepath.backend.progress.ProgressStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    static final int MAX_PATH_DEPTH = 3;
    static final int MAX_RESOLVED_NODES = 500;

    /**
     * Stand einer Station aus Sicht der Person: DONE erledigt, AVAILABLE als Nächstes dran (alle Vorgänger
     * erledigt), BLOCKED durch offene Pflicht-Vorgänger im verbindlichen Pfad gesperrt, UPCOMING später
     * (Empfehlungs-Vorgänger offen, trotzdem schon bearbeitbar), CLOSED Frist abgelaufen,
     * LOCKED (noch) nicht von der Lehrkraft freigegeben, EXCLUDED durch Entscheidung an einer Weiche ausgeschlossen.
     */
    public enum NodeState { DONE, AVAILABLE, BLOCKED, UPCOMING, CLOSED, LOCKED, EXCLUDED }

    public record ChoiceOption(String to, String label, String hint, boolean chosen, boolean unlocked) {
    }

    public record ChoiceRequest(String nodeId, String to) {
    }

    public record ChoiceResetRequest(String nodeId, Boolean resetProgress) {
    }

    public record NodeUnlockRequest(String nodeId) {
    }

    public record StudentChoiceEntry(String pathSlug, String pathTitle, boolean subpath, String nodeId,
                                     String nodeLabel, NodeState state, String chosenTo, String chosenLabel,
                                     String chosenBy, LocalDateTime chosenAt, List<ChoiceOption> options) {
    }

    public record StudentOverrideEntry(Long id, String pathSlug, String pathTitle, String nodeId, String nodeLabel,
                                       String kind, String target, String targetLabel,
                                       LocalDateTime createdAt) {
    }

    public record StudentPathAdminView(String selectedSlug, List<PathLink> paths, PathView view,
                                       List<StudentChoiceEntry> choices, List<StudentOverrideEntry> overrides) {
    }

    public record ChoiceBranchStat(String to, String label, int count, int unlockedCount) {
    }

    public record ChoiceNodeStats(String pathSlug, String pathTitle, boolean subpath, String nodeId, String nodeLabel,
                                  int totalStudents, int openCount, List<ChoiceBranchStat> branches) {
    }

    public enum CourseActionKind { RESET_CHOICE, SET_CHOICE, UNLOCK_BRANCH, UNLOCK_NODE }

    public record CoursePathActionRequest(CourseActionKind action, Long courseId, String pathSlug,
                                          String nodeId, String to, Boolean resetProgress) {
    }

    /** courseId/courseName: Kurs, für den der Lernpfad gilt; null für alle Kurse */
    public record PathSummary(Long id, String slug, String title, String description, boolean published,
                              boolean enforce, boolean templateOnly, int stations, Long courseId, String courseName,
                              List<String> usedIn, LocalDateTime updatedAt) {
    }

    /** applied: was beim Speichern in die Modul-Steuerung des Kurses übernommen wurde */
    public record PathAdmin(Long id, String slug, String title, String description, boolean published,
                            boolean enforce, boolean templateOnly, PathGraph.Graph graph, Long courseId, String courseName,
                            boolean lockOthers, String unlockRestAfter, List<String> usedIn, List<String> applied,
                            List<String> warnings, LocalDateTime updatedAt) {
    }

    /**
     * Fehlende Felder bleiben unverändert.
     *
     * @param courseId        Kurs; 0 für alle Kurse
     * @param unlockRestAfter Station-ID oder leerer String zum Aufheben
     */
    public record PathUpdate(String title, String slug, String description, Boolean published, Boolean enforce,
                             Boolean templateOnly, PathGraph.Graph graph, Long courseId, Boolean lockOthers,
                             String unlockRestAfter) {
    }

    public record PathLink(String slug, String title, String description) {
    }

    /** href: Ziel beim Anklicken, null bei gesperrten/blockierten Stationen, Meilensteinen, Weichen und Logik-Blöcken */
    public record NodeView(String id, PathGraph.NodeKind kind, String label, String icon, int x, int y, NodeState state,
                           String href, int done, int total, boolean recommended, String reason, Integer min,
                           List<ChoiceOption> options, String chosenTo, String path, boolean teacherUnlocked) {
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
    private final LearningPathChoiceRepository choiceRepository;
    private final LearningPathOverrideRepository overrideRepository;
    private final ContentService contentService;
    private final AccessPolicy accessPolicy;
    private final ReleaseService releaseService;
    private final CourseService courseService;
    private final UserService userService;
    private final ProgressService progressService;
    private final AuditService auditService;
    private final RealtimeUpdateService realtime;
    private final ObjectMapper objectMapper;

    // ---------------------------------------------------------------- Lehrkraft

    @Transactional(readOnly = true)
    public List<PathSummary> all() {
        List<LearningPath> allPaths = repository.findAllByOrderByPositionAscIdAsc();
        Map<String, PathGraph.Graph> graphs = allPaths.stream()
                .collect(Collectors.toMap(LearningPath::getSlug, p -> read(p.getGraph()), (a, b) -> a, LinkedHashMap::new));
        return allPaths.stream().map(p -> summary(p, allPaths, graphs)).toList();
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
        if (update.graph() != null) path.setGraph(write(checked(path.getSlug(), update.graph())));
        if (update.published() != null) path.setPublished(update.published());
        if (update.enforce() != null) path.setEnforce(update.enforce());
        if (update.templateOnly() != null) path.setTemplateOnly(update.templateOnly());
        if (update.courseId() != null) path.setCourseId(update.courseId() <= 0 ? null : courseService.get(update.courseId()).getId());
        if (update.lockOthers() != null) path.setLockOthers(update.lockOthers());
        if (update.unlockRestAfter() != null) {
            path.setUnlockRestAfter(update.unlockRestAfter().isBlank() ? null : update.unlockRestAfter().strip());
        }
        validateUnlockRestAfter(read(path.getGraph()), path.getUnlockRestAfter());
        validateSingleEnforced(path);
        path.setUpdatedAt(LocalDateTime.now());
        auditService.log("LEARNING_PATH_UPDATED", path.getSlug(), Map.of());
        List<String> applied = new ArrayList<>(applyToCourse(path));
        // Wenn dieser Lernpfad als Baustein in veröffentlichten Kurs-Pfaden steckt, deren Kursfreigaben mitaktualisieren
        List<LearningPath> allPaths = repository.findAllByOrderByPositionAscIdAsc();
        Map<String, PathGraph.Graph> graphs = allPaths.stream()
                .collect(Collectors.toMap(LearningPath::getSlug, p -> read(p.getGraph()), (a, b) -> a, LinkedHashMap::new));
        for (LearningPath other : allPaths) {
            if (other.getId().equals(path.getId()) || !other.isPublished() || other.getCourseId() == null) continue;
            if (embedsSlug(other.getSlug(), path.getSlug(), graphs, new HashSet<>())) {
                applied.addAll(applyToCourse(other));
            }
        }
        return admin(path, applied);
    }

    void validateUnlockRestAfter(PathGraph.Graph graph, String unlockRestAfter) {
        if (unlockRestAfter == null || unlockRestAfter.isBlank()) return;
        boolean exists = graph.nodes().stream().anyMatch(n -> n.id().equals(unlockRestAfter));
        if (!exists) {
            throw ApiException.badRequest("Die Station „" + unlockRestAfter + "“ für die Freigabe der übrigen Module gibt es im Lernpfad nicht.");
        }
    }

    void validateSingleEnforced(LearningPath path) {
        if (!path.isEnforce() || path.isTemplateOnly()) return;
        for (LearningPath other : repository.findAll()) {
            if (other.getId().equals(path.getId()) || !other.isEnforce() || other.isTemplateOnly()) continue;
            if (Objects.equals(other.getCourseId(), path.getCourseId())
                     || other.getCourseId() == null || path.getCourseId() == null) {
                throw ApiException.conflict("Pro Kurs ist höchstens ein verbindlicher Lernpfad möglich (bereits aktiv: „" + other.getTitle() + "“).");
            }
        }
    }

    /** Veröffentlichter Kurs-Lernpfad → Modul-Steuerung des Kurses; sonst ändert sich an den Freigaben nichts. */
    private List<String> applyToCourse(LearningPath path) {
        if (!path.isPublished() || path.isTemplateOnly() || path.getCourseId() == null) return List.of();
        Map<String, PathGraph.Graph> graphs = repository.findAllByOrderByPositionAscIdAsc().stream()
                .collect(Collectors.toMap(LearningPath::getSlug, p -> read(p.getGraph()), (a, b) -> a, LinkedHashMap::new));
        graphs.put(path.getSlug(), read(path.getGraph()));
        boolean unlockLater = path.isEnforce() && path.getUnlockRestAfter() != null && !path.getUnlockRestAfter().isBlank();
        Map<String, Set<String>> modulePlan = plan(graphs.get(path.getSlug()), graphs);
        if (unlockLater) {
            // Bei „Frei ab Station X“ regelt PathGate die Aufgaben-Sichtbarkeit vor und nach X ohne task_releases
            Map<String, Set<String>> openModules = new LinkedHashMap<>();
            modulePlan.keySet().forEach(m -> openModules.put(m, null));
            return releaseService.applyPlan(path.getCourseId(), openModules, false);
        }
        return releaseService.applyPlan(path.getCourseId(), modulePlan, path.isLockOthers());
    }

    /** Welche Aufgaben je Modul der Lernpfad (samt eingebundenen Bausteinen) nutzt: null für alle, sonst die Slugs. */
    static Map<String, Set<String>> plan(PathGraph.Graph graph) {
        return plan(graph, Map.of());
    }

    static Map<String, Set<String>> plan(PathGraph.Graph graph, Map<String, PathGraph.Graph> graphsBySlug) {
        Map<String, Set<String>> plan = new LinkedHashMap<>();
        collectPlan(graph, graphsBySlug, plan, new HashSet<>(), 0);
        return plan;
    }

    private static void collectPlan(PathGraph.Graph graph, Map<String, PathGraph.Graph> graphsBySlug,
                                    Map<String, Set<String>> plan, Set<String> visited, int depth) {
        if (graph == null || depth > MAX_PATH_DEPTH) return;
        for (PathGraph.Node node : graph.nodes()) {
            if (node.subpath()) {
                if (node.path() != null && visited.add(node.path())) {
                    collectPlan(graphsBySlug.get(node.path()), graphsBySlug, plan, visited, depth + 1);
                    visited.remove(node.path());
                }
                continue;
            }
            if (!node.station()) continue;
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
    }

    @Transactional
    public void delete(Long id) {
        LearningPath path = find(id);
        List<LearningPath> allPaths = repository.findAllByOrderByPositionAscIdAsc();
        List<String> usedBy = usedInTitles(path.getSlug(), allPaths);
        if (!usedBy.isEmpty()) {
            throw ApiException.conflict("Der Lernpfad „" + path.getTitle()
                    + "“ wird noch als Baustein in anderen Lernpfaden genutzt: " + String.join(", ", usedBy) + ".");
        }
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

    PathGraph.Graph checked(PathGraph.Graph graph) {
        return checked(null, graph);
    }

    /** Form prüfen und sicherstellen, dass alle Module, Aufgaben und eingebundenen Lernpfad-Bausteine gültig sind. */
    PathGraph.Graph checked(String ownSlug, PathGraph.Graph graph) {
        PathGraph.validate(graph);
        Map<String, Module> modules = contentService.modules().stream().collect(Collectors.toMap(Module::getSlug, Function.identity()));
        Map<String, Set<String>> tasks = new HashMap<>();
        Map<String, PathGraph.Graph> graphsBySlug = repository.findAll().stream()
                .collect(Collectors.toMap(LearningPath::getSlug, p -> read(p.getGraph()), (a, b) -> a, LinkedHashMap::new));
        if (ownSlug != null && !ownSlug.isBlank()) {
            graphsBySlug.put(ownSlug, graph);
        }
        for (PathGraph.Node node : graph.nodes()) {
            if (node.subpath()) {
                if (node.path().equals(ownSlug)) {
                    throw ApiException.badRequest("Ein Lernpfad kann sich nicht selbst als Baustein einbinden.");
                }
                if (!graphsBySlug.containsKey(node.path())) {
                    throw ApiException.badRequest("Den Lernpfad-Baustein „" + node.path() + "“ gibt es nicht.");
                }
                continue;
            }
            if (!node.station()) continue;
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
        // Zyklus-, Schachtelungstiefen- und Gesamtgrößenprüfung über alle Lernpfade hinweg
        String rootToCheck = ownSlug != null && !ownSlug.isBlank() ? ownSlug : "__draft__";
        graphsBySlug.put(rootToCheck, graph);
        for (String startSlug : graphsBySlug.keySet()) {
            int[] totalNodes = {0};
            validateSubpathHierarchy(startSlug, graphsBySlug, new LinkedHashSet<>(), 0, totalNodes);
        }
        return graph;
    }

    private static void validateSubpathHierarchy(String currentSlug, Map<String, PathGraph.Graph> graphsBySlug,
                                                 Set<String> chain, int depth, int[] totalNodes) {
        if (!chain.add(currentSlug)) {
            throw ApiException.badRequest("Zyklische Einbindung von Lernpfad-Bausteinen („" + currentSlug + "“) ist nicht erlaubt.");
        }
        if (depth > MAX_PATH_DEPTH) {
            throw ApiException.badRequest("Lernpfad-Bausteine dürfen höchstens " + MAX_PATH_DEPTH + " Ebenen tief verschachtelt werden.");
        }
        PathGraph.Graph g = graphsBySlug.get(currentSlug);
        if (g != null) {
            totalNodes[0] += g.nodes().size();
            if (totalNodes[0] > MAX_RESOLVED_NODES) {
                throw ApiException.badRequest("Ein Lernpfad darf samt eingebundenen Bausteinen höchstens " + MAX_RESOLVED_NODES + " Knoten umfassen.");
            }
            for (PathGraph.Node n : g.nodes()) {
                if (n.subpath() && n.path() != null) {
                    validateSubpathHierarchy(n.path(), graphsBySlug, chain, depth + 1, totalNodes);
                }
            }
        }
        chain.remove(currentSlug);
    }

    private static boolean embedsSlug(String outerSlug, String targetSlug, Map<String, PathGraph.Graph> graphs, Set<String> seen) {
        if (!seen.add(outerSlug)) return false;
        PathGraph.Graph g = graphs.get(outerSlug);
        if (g == null) return false;
        for (PathGraph.Node n : g.nodes()) {
            if (n.subpath() && n.path() != null) {
                if (n.path().equals(targetSlug) || embedsSlug(n.path(), targetSlug, graphs, seen)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> usedInTitles(String slug, List<LearningPath> allPaths) {
        List<String> used = new ArrayList<>();
        for (LearningPath other : allPaths) {
            if (other.getSlug().equals(slug)) continue;
            if (read(other.getGraph()).nodes().stream().anyMatch(n -> n.subpath() && slug.equals(n.path()))) {
                used.add(other.getTitle());
            }
        }
        return used;
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
                .filter(p -> !p.isTemplateOnly())
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
                .filter(p -> !p.isTemplateOnly())
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
        User target = actor;
        if (studentUsername != null && !studentUsername.isBlank()) {
            if (!actor.isTeacher()) throw ApiException.forbidden("Nur für Lehrkräfte.");
            target = userService.byUsername(studentUsername.strip());
        }
        Situation situation = new Situation(target, accessPolicy.courseReleases(target));
        ResolvedPath resolved = resolveAccessiblePath(actor, target, slug, situation);
        return evaluate(resolved.path(), situation, resolved.enforce(), 0).view();
    }

    private record ResolvedPath(LearningPath path, boolean enforce) {
    }

    private ResolvedPath resolveAccessiblePath(User actor, User target, String slug, Situation situation) {
        LearningPath targetPath = repository.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("Lernpfad nicht gefunden."));
        List<LearningPath> published = publishedFor(target);
        boolean directPublished = published.stream().anyMatch(q -> q.getId().equals(targetPath.getId()));

        boolean accessibleViaOuter = false;
        boolean outerEnforced = false;
        NodeState blockedState = null;
        String blockedReason = null;

        for (LearningPath outer : published) {
            if (outer.getId().equals(targetPath.getId())) continue;
            EvaluatedPath eval = evaluate(outer, situation, outer.isEnforce(), 0);
            SubpathAccess access = findSubpathAccess(eval, slug, outer.isEnforce());
            if (access != null) {
                if (access.accessible()) {
                    accessibleViaOuter = true;
                    if (access.enforced()) outerEnforced = true;
                } else if (blockedState == null) {
                    blockedState = access.state();
                    blockedReason = access.reason();
                }
            }
        }

        if (actor.isTeacher()) {
            return new ResolvedPath(targetPath, targetPath.isEnforce() || outerEnforced);
        }
        if (accessibleViaOuter) {
            return new ResolvedPath(targetPath, targetPath.isEnforce() || outerEnforced);
        }
        if (blockedState != null && !directPublished) {
            throw ApiException.forbidden(blockedReason != null ? blockedReason : "Dieser Lernpfad-Baustein ist noch nicht freigeschaltet.");
        }
        if (directPublished) {
            return new ResolvedPath(targetPath, targetPath.isEnforce() || outerEnforced);
        }
        throw ApiException.notFound("Lernpfad nicht gefunden.");
    }

    private record SubpathAccess(boolean accessible, boolean enforced, NodeState state, String reason) {
    }

    private static SubpathAccess findSubpathAccess(EvaluatedPath eval, String targetSlug, boolean currentEnforce) {
        SubpathAccess blockedFallback = null;
        for (PathGraph.Node node : eval.live().nodes()) {
            if (!node.subpath() || node.path() == null) continue;
            NodeState st = eval.states().getOrDefault(node.id(), NodeState.LOCKED);
            String rsn = eval.reasons().get(node.id());
            boolean open = st != NodeState.BLOCKED && st != NodeState.EXCLUDED && st != NodeState.LOCKED;
            if (node.path().equals(targetSlug)) {
                if (open) return new SubpathAccess(true, currentEnforce, st, rsn);
                if (blockedFallback == null) blockedFallback = new SubpathAccess(false, currentEnforce, st, rsn);
            }
            EvaluatedPath child = eval.subpaths().get(node.id());
            if (child != null) {
                SubpathAccess nested = findSubpathAccess(child, targetSlug, currentEnforce || child.view().enforce());
                if (nested != null) {
                    if (open && nested.accessible()) {
                        return new SubpathAccess(true, currentEnforce || nested.enforced(), nested.state(), nested.reason());
                    }
                    if (blockedFallback == null) {
                        blockedFallback = new SubpathAccess(false, currentEnforce || nested.enforced(),
                                open ? nested.state() : st, open ? nested.reason() : rsn);
                    }
                }
            }
        }
        return blockedFallback;
    }

    /** Die eine empfohlene nächste Aufgabe – aus dem ersten passenden Lernpfad, sonst aus der Modulreihenfolge. */
    @Transactional(readOnly = true)
    public Recommendation next(User user) {
        List<LearningPath> paths = publishedFor(user);
        if (!paths.isEmpty()) {
            Situation baseSituation = new Situation(user, accessPolicy.courseReleases(user));
            Recommendation fromPath = evaluate(paths.getFirst(), baseSituation, paths.getFirst().isEnforce(), 0).view().recommendation();
            if (fromPath != null) return fromPath;
        }
        // Ist der Lernpfad geschafft (oder gibt es keinen), geht es mit den übrigen zugänglichen Aufgaben weiter
        return fallback(new Situation(user, accessPolicy.releases(user)));
    }

    /**
     * Trifft eine Entscheidung an einer Entweder-oder-Weiche ({@link PathGraph.NodeKind#CHOICE}).
     * Die Wahl ist für Schülerinnen und Schüler verbindlich und sperrt die nicht gewählten Zweige.
     */
    @Transactional
    public PathView choose(User user, String slug, String nodeId, String to) {
        if (nodeId == null || nodeId.isBlank() || to == null || to.isBlank()) {
            throw ApiException.badRequest("Bitte eine Weiche und einen Weg angeben.");
        }
        Situation situation = new Situation(user, accessPolicy.courseReleases(user));
        ResolvedPath resolved = resolveAccessiblePath(user, user, slug, situation);
        LearningPath path = resolved.path();
        String trimmedNode = nodeId.strip();
        String trimmedTo = to.strip();
        EvaluatedPath evaluated = evaluate(path, situation, resolved.enforce(), 0);
        PathGraph.Node choiceNode = evaluated.nodeById().get(trimmedNode);
        if (choiceNode == null || choiceNode.kind() != PathGraph.NodeKind.CHOICE) {
            throw ApiException.badRequest("Die Weiche „" + trimmedNode + "“ gibt es im Lernpfad nicht.");
        }
        boolean validEdge = evaluated.live().edges().stream()
                .anyMatch(e -> e.from().equals(trimmedNode) && e.to().equals(trimmedTo));
        if (!validEdge) {
            throw ApiException.badRequest("Der gewählte Weg gehört nicht zu dieser Weiche.");
        }
        Optional<LearningPathChoice> existing = choiceRepository.findByUserIdAndPathIdAndNodeId(user.getId(), path.getId(), trimmedNode);
        if (existing.isPresent()) {
            LearningPathChoice prev = existing.get();
            boolean prevStillValid = evaluated.live().edges().stream()
                    .anyMatch(e -> e.from().equals(trimmedNode) && e.to().equals(prev.getChosenEdgeTo()));
            if (prevStillValid) {
                throw ApiException.conflict("An dieser Weiche wurde bereits ein Weg gewählt.");
            }
            choiceRepository.delete(prev);
            choiceRepository.flush();
        }
        NodeState currentState = evaluated.states().get(trimmedNode);
        if (currentState != NodeState.AVAILABLE) {
            throw ApiException.forbidden("Diese Weiche ist noch nicht freigeschaltet.");
        }
        LearningPathChoice choice = new LearningPathChoice();
        choice.setUserId(user.getId());
        choice.setPathId(path.getId());
        choice.setNodeId(trimmedNode);
        choice.setChosenEdgeTo(trimmedTo);
        choice.setChosenBy(user.isTeacher() ? "TEACHER" : "STUDENT");
        choiceRepository.save(choice);
        auditService.log("LEARNING_PATH_CHOICE_MADE", path.getSlug(), Map.of("nodeId", trimmedNode, "to", trimmedTo));
        return evaluate(path, situation, resolved.enforce(), 0).view();
    }

    // ---------------------------------------------------------------- Lehrkraft-Eingriffe (#25)

    @Transactional(readOnly = true)
    public StudentPathAdminView studentPathView(User actor, String username, String requestedSlug) {
        if (!actor.isTeacher()) throw ApiException.forbidden("Nur für Lehrkräfte.");
        User student = userService.byUsername(username.strip());
        Situation situation = new Situation(student, accessPolicy.courseReleases(student));
        List<LearningPath> published = publishedFor(student);
        List<PathLink> links = new ArrayList<>(published.stream()
                .map(p -> new PathLink(p.getSlug(), p.getTitle(), p.getDescription()))
                .toList());
        LearningPath selected = null;
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            LearningPath reqPath = repository.findBySlug(requestedSlug.strip())
                    .orElseThrow(() -> ApiException.notFound("Lernpfad nicht gefunden."));
            selected = reqPath;
            if (links.stream().noneMatch(l -> l.slug().equals(reqPath.getSlug()))) {
                links.add(new PathLink(reqPath.getSlug(), reqPath.getTitle(), reqPath.getDescription()));
            }
        } else if (!published.isEmpty()) {
            selected = published.getFirst();
        }
        if (selected == null) {
            return new StudentPathAdminView(null, links, null, List.of(), List.of());
        }
        ResolvedPath resolved = resolveAccessiblePath(actor, student, selected.getSlug(), situation);
        EvaluatedPath eval = evaluate(selected, situation, resolved.enforce(), 0);
        List<StudentChoiceEntry> choices = new ArrayList<>();
        List<StudentOverrideEntry> overrides = new ArrayList<>();
        collectStudentAdminEntries(student, selected, eval, false, situation, choices, overrides, new HashSet<>());
        return new StudentPathAdminView(selected.getSlug(), links, eval.view(), choices, overrides);
    }

    private void collectStudentAdminEntries(User student, LearningPath path, EvaluatedPath eval, boolean isSubpath,
                                            Situation s, List<StudentChoiceEntry> choices,
                                            List<StudentOverrideEntry> overrides, Set<Long> visitedPaths) {
        if (path.getId() == null || !visitedPaths.add(path.getId())) return;
        Map<String, LearningPathChoice> choiceMap = choiceRepository.findByUserIdAndPathId(student.getId(), path.getId()).stream()
                .collect(Collectors.toMap(LearningPathChoice::getNodeId, Function.identity(), (a, b) -> a));
        Map<String, NodeView> viewById = eval.view().nodes().stream()
                .collect(Collectors.toMap(NodeView::id, Function.identity(), (a, b) -> a));
        for (PathGraph.Node node : eval.live().nodes()) {
            if (node.choice()) {
                NodeView nv = viewById.get(node.id());
                LearningPathChoice ch = choiceMap.get(node.id());
                List<ChoiceOption> opts = nv != null && nv.options() != null ? nv.options() : List.of();
                String chosenTo = ch != null ? ch.getChosenEdgeTo() : null;
                String chosenLabel = chosenTo == null ? null : opts.stream()
                        .filter(o -> o.to().equals(chosenTo))
                        .map(ChoiceOption::label)
                        .findFirst()
                        .orElseGet(() -> s.label(eval.nodeById().get(chosenTo)));
                choices.add(new StudentChoiceEntry(
                        path.getSlug(),
                        path.getTitle(),
                        isSubpath,
                        node.id(),
                        s.label(node),
                        nv != null ? nv.state() : NodeState.LOCKED,
                        chosenTo,
                        chosenLabel,
                        ch != null ? ch.getChosenBy() : null,
                        ch != null ? ch.getChosenAt() : null,
                        opts
                ));
            }
        }
        for (LearningPathOverride ov : overrideRepository.findByUserIdAndPathId(student.getId(), path.getId())) {
            PathGraph.Node n = eval.nodeById().get(ov.getNodeId());
            String nodeLabel = n != null ? s.label(n) : ov.getNodeId();
            String targetLabel = null;
            if (ov.getTarget() != null && !ov.getTarget().isBlank()) {
                targetLabel = eval.live().edges().stream()
                        .filter(e -> e.from().equals(ov.getNodeId()) && e.to().equals(ov.getTarget()))
                        .map(PathGraph.Edge::label)
                        .filter(l -> l != null && !l.isBlank())
                        .findFirst()
                        .orElseGet(() -> s.label(eval.nodeById().get(ov.getTarget())));
            }
            overrides.add(new StudentOverrideEntry(
                    ov.getId(),
                    path.getSlug(),
                    path.getTitle(),
                    ov.getNodeId(),
                    nodeLabel,
                    ov.getKind(),
                    ov.getTarget(),
                    targetLabel,
                    ov.getCreatedAt()
            ));
        }
        for (PathGraph.Node node : eval.live().nodes()) {
            if (node.subpath() && node.path() != null) {
                LearningPath sub = s.path(node.path());
                EvaluatedPath subEval = eval.subpaths().get(node.id());
                if (sub != null && subEval != null) {
                    collectStudentAdminEntries(student, sub, subEval, true, s, choices, overrides, visitedPaths);
                }
            }
        }
    }

    @Transactional
    public void resetChoice(User actor, String username, String pathSlug, String nodeId, boolean resetProgress) {
        if (!actor.isTeacher()) throw ApiException.forbidden("Nur für Lehrkräfte.");
        User student = userService.byUsername(username.strip());
        LearningPath path = repository.findBySlug(pathSlug.strip())
                .orElseThrow(() -> ApiException.notFound("Lernpfad nicht gefunden."));
        String trimmedNode = requireChoiceNode(path, nodeId);
        doResetChoice(actor, student, path, trimmedNode, resetProgress);
        auditService.log("LEARNING_PATH_CHOICE_RESET", path.getSlug(),
                Map.of("student", student.getUsername(), "nodeId", trimmedNode, "resetProgress", resetProgress));
        realtime.publishLearningPathUpdate(student.getUsername(), "Deine Lehrkraft hat deinen Weg zurückgesetzt.");
    }

    private void doResetChoice(User actor, User student, LearningPath path, String nodeId, boolean resetProgress) {
        Optional<LearningPathChoice> existing = choiceRepository.findByUserIdAndPathIdAndNodeId(student.getId(), path.getId(), nodeId);
        String abandonedTo = existing.map(LearningPathChoice::getChosenEdgeTo).orElse(null);
        existing.ifPresent(choiceRepository::delete);
        for (LearningPathOverride ov : overrideRepository.findByUserIdAndPathId(student.getId(), path.getId())) {
            if (LearningPathOverride.UNLOCK_BRANCH.equals(ov.getKind()) && ov.getNodeId().equals(nodeId)) {
                overrideRepository.delete(ov);
            }
        }
        if (resetProgress && abandonedTo != null) {
            Set<Long> exclusiveTaskIds = exclusiveBranchTaskIds(student, path, nodeId, abandonedTo);
            if (!exclusiveTaskIds.isEmpty()) {
                progressService.resetTasks(student.getId(), exclusiveTaskIds,
                        "Lernpfad-Weiche zurückgesetzt (" + path.getTitle() + ")", actor.getId());
            }
        }
    }

    private Set<Long> exclusiveBranchTaskIds(User student, LearningPath targetPath, String choiceNodeId, String abandonedTo) {
        Map<String, PathGraph.Graph> graphsBySlug = repository.findAllByOrderByPositionAscIdAsc().stream()
                .collect(Collectors.toMap(LearningPath::getSlug, p -> read(p.getGraph()), (a, b) -> a, LinkedHashMap::new));
        PathGraph.Graph graph = graphsBySlug.getOrDefault(targetPath.getSlug(), read(targetPath.getGraph()));
        Set<String> otherTargets = graph.edges().stream()
                .filter(e -> e.from().equals(choiceNodeId) && !e.to().equals(abandonedTo))
                .map(PathGraph.Edge::to)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> branchNodeIds = otherTargets.isEmpty()
                ? Set.of(abandonedTo)
                : PathGraph.excludedByChoice(graph, choiceNodeId, otherTargets);

        Map<String, Module> modulesBySlug = contentService.modules().stream()
                .collect(Collectors.toMap(Module::getSlug, Function.identity()));
        Map<String, List<PublishedTask>> tasksCache = new HashMap<>();

        Set<Long> branchTasks = new LinkedHashSet<>();
        Set<Long> outsideTasks = new LinkedHashSet<>();
        for (PathGraph.Node node : graph.nodes()) {
            if (branchNodeIds.contains(node.id())) {
                collectNodeTaskIds(node, graphsBySlug, modulesBySlug, tasksCache, branchTasks, new HashSet<>(), 0);
            } else {
                collectNodeTaskIds(node, graphsBySlug, modulesBySlug, tasksCache, outsideTasks, new HashSet<>(), 0);
            }
        }
        // Aufgaben in übergeordneten Pfaden außerhalb dieses Bausteins ebenfalls schützen
        for (LearningPath outer : publishedFor(student)) {
            if (outer.getId().equals(targetPath.getId())) continue;
            PathGraph.Graph outerGraph = graphsBySlug.getOrDefault(outer.getSlug(), read(outer.getGraph()));
            if (embedsSlug(outer.getSlug(), targetPath.getSlug(), graphsBySlug, new HashSet<>())) {
                for (PathGraph.Node node : outerGraph.nodes()) {
                    if (node.subpath() && targetPath.getSlug().equals(node.path())) continue;
                    collectNodeTaskIds(node, graphsBySlug, modulesBySlug, tasksCache, outsideTasks, new HashSet<>(), 0);
                }
            }
        }
        branchTasks.removeAll(outsideTasks);
        return branchTasks;
    }

    private void collectNodeTaskIds(PathGraph.Node node, Map<String, PathGraph.Graph> graphsBySlug,
                                    Map<String, Module> modulesBySlug, Map<String, List<PublishedTask>> tasksCache,
                                    Set<Long> target, Set<String> visitedPaths, int depth) {
        if (node == null || depth > MAX_PATH_DEPTH) return;
        if (node.subpath()) {
            if (node.path() != null && visitedPaths.add(node.path())) {
                PathGraph.Graph subGraph = graphsBySlug.get(node.path());
                if (subGraph != null) {
                    for (PathGraph.Node subNode : subGraph.nodes()) {
                        collectNodeTaskIds(subNode, graphsBySlug, modulesBySlug, tasksCache, target, visitedPaths, depth + 1);
                    }
                }
                visitedPaths.remove(node.path());
            }
            return;
        }
        if (!node.station()) return;
        Module module = modulesBySlug.get(node.module());
        if (module == null) return;
        List<PublishedTask> all = tasksCache.computeIfAbsent(module.getSlug(), s -> contentService.publishedTasks(module));
        if (node.kind() == PathGraph.NodeKind.TASK) {
            all.stream().filter(t -> t.task().getSlug().equals(node.task())).forEach(t -> target.add(t.taskId()));
        } else if (node.partial()) {
            all.stream().filter(t -> node.tasks().contains(t.task().getSlug())).forEach(t -> target.add(t.taskId()));
        } else {
            all.forEach(t -> target.add(t.taskId()));
        }
    }

    @Transactional
    public void setChoiceForStudent(User actor, String username, String pathSlug, String nodeId, String to) {
        if (!actor.isTeacher()) throw ApiException.forbidden("Nur für Lehrkräfte.");
        User student = userService.byUsername(username.strip());
        LearningPath path = repository.findBySlug(pathSlug.strip())
                .orElseThrow(() -> ApiException.notFound("Lernpfad nicht gefunden."));
        String trimmedNode = requireChoiceNode(path, nodeId);
        String trimmedTo = requireChoiceEdge(path, trimmedNode, to);
        doSetChoice(student, path, trimmedNode, trimmedTo);
        auditService.log("LEARNING_PATH_CHOICE_SET", path.getSlug(),
                Map.of("student", student.getUsername(), "nodeId", trimmedNode, "to", trimmedTo));
        realtime.publishLearningPathUpdate(student.getUsername(), "Deine Lehrkraft hat deinen Weg im Lernpfad festgelegt.");
    }

    private void doSetChoice(User student, LearningPath path, String nodeId, String to) {
        LearningPathChoice choice = choiceRepository.findByUserIdAndPathIdAndNodeId(student.getId(), path.getId(), nodeId)
                .orElseGet(LearningPathChoice::new);
        choice.setUserId(student.getId());
        choice.setPathId(path.getId());
        choice.setNodeId(nodeId);
        choice.setChosenEdgeTo(to);
        choice.setChosenBy("TEACHER");
        choice.setChosenAt(LocalDateTime.now());
        choiceRepository.save(choice);
    }

    @Transactional
    public void unlockBranchForStudent(User actor, String username, String pathSlug, String nodeId, String to) {
        if (!actor.isTeacher()) throw ApiException.forbidden("Nur für Lehrkräfte.");
        User student = userService.byUsername(username.strip());
        LearningPath path = repository.findBySlug(pathSlug.strip())
                .orElseThrow(() -> ApiException.notFound("Lernpfad nicht gefunden."));
        String trimmedNode = requireChoiceNode(path, nodeId);
        String trimmedTo = requireChoiceEdge(path, trimmedNode, to);
        Optional<LearningPathOverride> existing = overrideRepository.findByUserIdAndPathIdAndNodeIdAndKindAndTarget(
                student.getId(), path.getId(), trimmedNode, LearningPathOverride.UNLOCK_BRANCH, trimmedTo);
        boolean unlocked;
        if (existing.isPresent()) {
            overrideRepository.delete(existing.get());
            unlocked = false;
        } else {
            saveOverride(student.getId(), path.getId(), trimmedNode, LearningPathOverride.UNLOCK_BRANCH, trimmedTo, actor.getId());
            unlocked = true;
        }
        auditService.log("LEARNING_PATH_BRANCH_UNLOCK", path.getSlug(),
                Map.of("student", student.getUsername(), "nodeId", trimmedNode, "to", trimmedTo, "unlocked", unlocked));
        realtime.publishLearningPathUpdate(student.getUsername(),
                unlocked ? "Deine Lehrkraft hat dir einen weiteren Weg freigeschaltet." : "Deine Lehrkraft hat deinen Lernpfad angepasst.");
    }

    @Transactional
    public void unlockNodeForStudent(User actor, String username, String pathSlug, String nodeId) {
        if (!actor.isTeacher()) throw ApiException.forbidden("Nur für Lehrkräfte.");
        User student = userService.byUsername(username.strip());
        LearningPath path = repository.findBySlug(pathSlug.strip())
                .orElseThrow(() -> ApiException.notFound("Lernpfad nicht gefunden."));
        String trimmedNode = requireNode(path, nodeId);
        Optional<LearningPathOverride> existing = overrideRepository.findByUserIdAndPathIdAndNodeIdAndKindAndTarget(
                student.getId(), path.getId(), trimmedNode, LearningPathOverride.UNLOCK_NODE, "");
        boolean unlocked;
        if (existing.isPresent()) {
            overrideRepository.delete(existing.get());
            unlocked = false;
        } else {
            saveOverride(student.getId(), path.getId(), trimmedNode, LearningPathOverride.UNLOCK_NODE, "", actor.getId());
            unlocked = true;
        }
        auditService.log("LEARNING_PATH_NODE_UNLOCK", path.getSlug(),
                Map.of("student", student.getUsername(), "nodeId", trimmedNode, "unlocked", unlocked));
        realtime.publishLearningPathUpdate(student.getUsername(),
                unlocked ? "Deine Lehrkraft hat eine Station für dich freigeschaltet." : "Deine Lehrkraft hat deinen Lernpfad angepasst.");
    }

    private void saveOverride(Long userId, Long pathId, String nodeId, String kind, String target, Long actorId) {
        LearningPathOverride ov = new LearningPathOverride();
        ov.setUserId(userId);
        ov.setPathId(pathId);
        ov.setNodeId(nodeId);
        ov.setKind(kind);
        ov.setTarget(target != null ? target : "");
        ov.setCreatedBy(actorId);
        overrideRepository.save(ov);
    }

    private String requireNode(LearningPath path, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) throw ApiException.badRequest("Bitte eine Station angeben.");
        String trimmed = nodeId.strip();
        boolean exists = read(path.getGraph()).nodes().stream().anyMatch(n -> n.id().equals(trimmed));
        if (!exists) throw ApiException.badRequest("Die Station „" + trimmed + "“ gibt es im Lernpfad nicht.");
        return trimmed;
    }

    private String requireChoiceNode(LearningPath path, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) throw ApiException.badRequest("Bitte eine Weiche angeben.");
        String trimmed = nodeId.strip();
        PathGraph.Node node = read(path.getGraph()).nodes().stream()
                .filter(n -> n.id().equals(trimmed))
                .findFirst()
                .orElse(null);
        if (node == null || !node.choice()) {
            throw ApiException.badRequest("Die Weiche „" + trimmed + "“ gibt es im Lernpfad nicht.");
        }
        return trimmed;
    }

    private String requireChoiceEdge(LearningPath path, String choiceNodeId, String to) {
        if (to == null || to.isBlank()) throw ApiException.badRequest("Bitte einen Ziel-Weg angeben.");
        String trimmed = to.strip();
        boolean exists = read(path.getGraph()).edges().stream()
                .anyMatch(e -> e.from().equals(choiceNodeId) && e.to().equals(trimmed));
        if (!exists) {
            throw ApiException.badRequest("Der gewählte Weg gehört nicht zu dieser Weiche.");
        }
        return trimmed;
    }

    @Transactional(readOnly = true)
    public List<ChoiceNodeStats> choiceStats(Long pathId, Long courseId) {
        LearningPath root = find(pathId);
        Long effectiveCourseId = courseId != null && courseId > 0 ? courseId : root.getCourseId();
        Set<Long> studentIds = effectiveCourseId != null
                ? new LinkedHashSet<>(userService.memberIds(effectiveCourseId))
                : userService.students().stream().map(User::getId).collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, LearningPath> pathsBySlug = repository.findAllByOrderByPositionAscIdAsc().stream()
                .collect(Collectors.toMap(LearningPath::getSlug, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<LearningPath> hierarchy = new ArrayList<>();
        collectPathHierarchy(root, pathsBySlug, hierarchy, new HashSet<>(), 0);

        List<Long> pathIds = hierarchy.stream().map(LearningPath::getId).filter(Objects::nonNull).toList();
        List<LearningPathChoice> allChoices = pathIds.isEmpty() ? List.of() : choiceRepository.findByPathIdIn(pathIds);
        List<LearningPathOverride> allOverrides = pathIds.isEmpty() ? List.of() : overrideRepository.findByPathIdIn(pathIds);

        Map<String, Module> modulesBySlug = contentService.modules().stream()
                .collect(Collectors.toMap(Module::getSlug, Function.identity()));
        List<ChoiceNodeStats> stats = new ArrayList<>();
        for (int i = 0; i < hierarchy.size(); i++) {
            LearningPath p = hierarchy.get(i);
            boolean isSubpath = i > 0;
            PathGraph.Graph g = read(p.getGraph());
            Map<String, PathGraph.Node> nodeById = g.nodes().stream()
                    .collect(Collectors.toMap(PathGraph.Node::id, Function.identity(), (a, b) -> a));
            for (PathGraph.Node node : g.nodes()) {
                if (!node.choice()) continue;
                String nodeLabel = nodeLabel(node, modulesBySlug, pathsBySlug);
                List<LearningPathChoice> nodeChoices = allChoices.stream()
                        .filter(c -> c.getPathId().equals(p.getId()) && c.getNodeId().equals(node.id()) && studentIds.contains(c.getUserId()))
                        .toList();
                List<LearningPathOverride> nodeOverrides = allOverrides.stream()
                        .filter(o -> o.getPathId().equals(p.getId()) && o.getNodeId().equals(node.id())
                                && LearningPathOverride.UNLOCK_BRANCH.equals(o.getKind()) && studentIds.contains(o.getUserId()))
                        .toList();
                Set<Long> decidedUsers = nodeChoices.stream().map(LearningPathChoice::getUserId).collect(Collectors.toSet());
                List<ChoiceBranchStat> branches = g.edges().stream()
                        .filter(e -> e.from().equals(node.id()))
                        .map(e -> {
                            String bLabel = e.label() != null && !e.label().isBlank()
                                    ? e.label()
                                    : nodeLabel(nodeById.get(e.to()), modulesBySlug, pathsBySlug);
                            int count = (int) nodeChoices.stream().filter(c -> e.to().equals(c.getChosenEdgeTo())).count();
                            int unlockedCount = (int) nodeOverrides.stream().filter(o -> e.to().equals(o.getTarget())).count();
                            return new ChoiceBranchStat(e.to(), bLabel, count, unlockedCount);
                        })
                        .toList();
                int openCount = Math.max(0, studentIds.size() - decidedUsers.size());
                stats.add(new ChoiceNodeStats(p.getSlug(), p.getTitle(), isSubpath, node.id(), nodeLabel,
                        studentIds.size(), openCount, branches));
            }
        }
        return stats;
    }

    private void collectPathHierarchy(LearningPath current, Map<String, LearningPath> pathsBySlug,
                                      List<LearningPath> result, Set<String> seen, int depth) {
        if (current == null || depth > MAX_PATH_DEPTH || !seen.add(current.getSlug())) return;
        result.add(current);
        PathGraph.Graph g = read(current.getGraph());
        for (PathGraph.Node node : g.nodes()) {
            if (node.subpath() && node.path() != null) {
                collectPathHierarchy(pathsBySlug.get(node.path()), pathsBySlug, result, seen, depth + 1);
            }
        }
    }

    private String nodeLabel(PathGraph.Node node, Map<String, Module> modules, Map<String, LearningPath> paths) {
        if (node == null) return "";
        if (node.label() != null && !node.label().isBlank()) return node.label();
        if (node.choice()) return "Weiche";
        if (node.kind() == PathGraph.NodeKind.AND) return "UND-Block";
        if (node.kind() == PathGraph.NodeKind.OR) return "ODER-Block";
        if (node.subpath()) {
            LearningPath sub = paths.get(node.path());
            return sub != null ? sub.getTitle() : node.path();
        }
        if (node.kind() == PathGraph.NodeKind.MODULE) {
            Module m = modules.get(node.module());
            return m != null ? m.getTitle() : node.module();
        }
        Module m = modules.get(node.module());
        if (m != null) {
            return contentService.publishedTasks(m).stream()
                    .filter(t -> t.task().getSlug().equals(node.task()))
                    .map(t -> t.version().getTitle())
                    .findFirst()
                    .orElse(node.task());
        }
        return node.task() != null ? node.task() : node.id();
    }

    @Transactional
    public int courseAction(User actor, Long pathId, CoursePathActionRequest req) {
        if (!actor.isTeacher()) throw ApiException.forbidden("Nur für Lehrkräfte.");
        if (req == null || req.action() == null) throw ApiException.badRequest("Bitte eine Aktion angeben.");
        LearningPath root = find(pathId);
        LearningPath targetPath = req.pathSlug() != null && !req.pathSlug().isBlank()
                ? repository.findBySlug(req.pathSlug().strip()).orElseThrow(() -> ApiException.notFound("Lernpfad nicht gefunden."))
                : root;
        Long effectiveCourseId = req.courseId() != null && req.courseId() > 0 ? req.courseId() : root.getCourseId();
        Set<Long> memberIds = effectiveCourseId != null ? new HashSet<>(userService.memberIds(effectiveCourseId)) : null;
        List<User> students = userService.students().stream()
                .filter(u -> memberIds == null || memberIds.contains(u.getId()))
                .toList();

        switch (req.action()) {
            case RESET_CHOICE -> {
                String trimmedNode = requireChoiceNode(targetPath, req.nodeId());
                for (User student : students) {
                    doResetChoice(actor, student, targetPath, trimmedNode, Boolean.TRUE.equals(req.resetProgress()));
                    realtime.publishLearningPathUpdate(student.getUsername(), "Deine Lehrkraft hat deinen Weg zurückgesetzt.");
                }
            }
            case SET_CHOICE -> {
                String trimmedNode = requireChoiceNode(targetPath, req.nodeId());
                String trimmedTo = requireChoiceEdge(targetPath, trimmedNode, req.to());
                for (User student : students) {
                    doSetChoice(student, targetPath, trimmedNode, trimmedTo);
                    realtime.publishLearningPathUpdate(student.getUsername(), "Deine Lehrkraft hat deinen Weg im Lernpfad festgelegt.");
                }
            }
            case UNLOCK_BRANCH -> {
                String trimmedNode = requireChoiceNode(targetPath, req.nodeId());
                String trimmedTo = requireChoiceEdge(targetPath, trimmedNode, req.to());
                for (User student : students) {
                    if (overrideRepository.findByUserIdAndPathIdAndNodeIdAndKindAndTarget(
                            student.getId(), targetPath.getId(), trimmedNode, LearningPathOverride.UNLOCK_BRANCH, trimmedTo).isEmpty()) {
                        saveOverride(student.getId(), targetPath.getId(), trimmedNode, LearningPathOverride.UNLOCK_BRANCH, trimmedTo, actor.getId());
                    }
                    realtime.publishLearningPathUpdate(student.getUsername(), "Deine Lehrkraft hat dir einen weiteren Weg freigeschaltet.");
                }
            }
            case UNLOCK_NODE -> {
                String trimmedNode = requireNode(targetPath, req.nodeId());
                for (User student : students) {
                    if (overrideRepository.findByUserIdAndPathIdAndNodeIdAndKindAndTarget(
                            student.getId(), targetPath.getId(), trimmedNode, LearningPathOverride.UNLOCK_NODE, "").isEmpty()) {
                        saveOverride(student.getId(), targetPath.getId(), trimmedNode, LearningPathOverride.UNLOCK_NODE, "", actor.getId());
                    }
                    realtime.publishLearningPathUpdate(student.getUsername(), "Deine Lehrkraft hat eine Station für dich freigeschaltet.");
                }
            }
        }
        auditService.log("LEARNING_PATH_COURSE_ACTION", targetPath.getSlug(),
                Map.of("action", req.action().name(), "nodeId", req.nodeId() != null ? req.nodeId() : "",
                        "affected", students.size()));
        return students.size();
    }

    @EventListener
    @Transactional
    public void onCourseEnding(CourseService.CourseEndingEvent event) {
        if (!event.userIds().isEmpty()) {
            choiceRepository.deleteByUserIdIn(event.userIds());
            overrideRepository.deleteByUserIdIn(event.userIds());
        }
    }

    private record GateStation(PathGraph.Node node, NodeState state, String reason) {
    }

    @Override
    @Transactional(readOnly = true)
    public PathGate.Evaluation evaluate(User user, AccessPolicy.Releases baseReleases) {
        Optional<LearningPath> enforced = enforcedFor(user);
        if (enforced.isEmpty()) return PathGate.NO_OP;
        LearningPath path = enforced.get();
        Situation situation = new Situation(user, baseReleases);
        EvaluatedPath evaluated = evaluate(path, situation, path.isEnforce(), 0);

        Map<String, List<GateStation>> taskNodes = new HashMap<>();
        Map<String, List<GateStation>> moduleNodes = new HashMap<>();
        Set<String> modulesInPath = new HashSet<>();
        collectGateStations(evaluated, null, null, taskNodes, moduleNodes, modulesInPath, situation);

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
                List<GateStation> entries = taskNodes.get(key);
                if (entries != null && !entries.isEmpty()) {
                    if (entries.stream().allMatch(e -> e.state() == NodeState.EXCLUDED)) {
                        return new Decision(State.HIDDEN, "Du hast dich für einen anderen Weg entschieden.");
                    }
                    // Bereits vor dem Einschalten von „Verbindlich“ erledigte Aufgaben bleiben gültig
                    if (situation.finished(task)) return Decision.OPEN;
                    for (GateStation e : entries) {
                        NodeState state = e.state();
                        if (state == NodeState.AVAILABLE || state == NodeState.DONE || state == NodeState.UPCOMING) {
                            return Decision.OPEN;
                        }
                    }
                    for (GateStation e : entries) {
                        if (e.state() == NodeState.BLOCKED) {
                            return new Decision(State.BLOCKED, e.reason());
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
                    List<GateStation> mEntries = moduleNodes.getOrDefault(module.getSlug(), List.of());
                    if (!mEntries.isEmpty() && mEntries.stream().allMatch(e -> e.state() == NodeState.EXCLUDED)) {
                        boolean wholeModuleInPath = mEntries.stream().anyMatch(e -> e.node().kind() == PathGraph.NodeKind.MODULE && !e.node().partial())
                                || situation.tasks(module).stream().allMatch(t -> taskNodes.containsKey(module.getSlug() + "/" + t.task().getSlug()));
                        if (wholeModuleInPath || !restUnlocked) {
                            return new Decision(State.HIDDEN, "Du hast dich für einen anderen Weg entschieden.");
                        }
                        return Decision.UNLOCKED;
                    }
                    return Decision.OPEN;
                }
                if (restUnlocked) return Decision.UNLOCKED;
                if (lockOutside) return new Decision(State.HIDDEN, outsideReason);
                return Decision.OPEN;
            }
        };
    }

    private void collectGateStations(EvaluatedPath eval, NodeState overrideState, String overrideReason,
                                     Map<String, List<GateStation>> taskNodes, Map<String, List<GateStation>> moduleNodes,
                                     Set<String> modulesInPath, Situation situation) {
        for (PathGraph.Node node : eval.live().nodes()) {
            NodeState rawState = eval.states().get(node.id());
            String rawReason = eval.reasons().get(node.id());
            NodeState effectiveState = overrideState != null ? overrideState : rawState;
            String effectiveReason = overrideState != null ? overrideReason : rawReason;
            if (node.subpath()) {
                EvaluatedPath subEval = eval.subpaths().get(node.id());
                if (subEval != null) {
                    NodeState nextOverride = (effectiveState == NodeState.EXCLUDED || effectiveState == NodeState.BLOCKED)
                            ? effectiveState : null;
                    String nextReason = nextOverride != null ? effectiveReason : null;
                    collectGateStations(subEval, nextOverride, nextReason, taskNodes, moduleNodes, modulesInPath, situation);
                }
                continue;
            }
            if (!node.station()) continue;
            GateStation entry = new GateStation(node, effectiveState, effectiveReason);
            modulesInPath.add(node.module());
            moduleNodes.computeIfAbsent(node.module(), k -> new ArrayList<>()).add(entry);
            if (node.kind() == PathGraph.NodeKind.TASK) {
                taskNodes.computeIfAbsent(node.module() + "/" + node.task(), k -> new ArrayList<>()).add(entry);
            } else if (node.partial()) {
                for (String t : node.tasks()) {
                    taskNodes.computeIfAbsent(node.module() + "/" + t, k -> new ArrayList<>()).add(entry);
                }
            } else {
                Module module = situation.module(node.module());
                if (module != null) {
                    for (PublishedTask t : situation.tasks(module)) {
                        taskNodes.computeIfAbsent(node.module() + "/" + t.task().getSlug(), k -> new ArrayList<>()).add(entry);
                    }
                }
            }
        }
    }

    private record EvaluatedPath(PathView view, PathGraph.Graph live, Map<String, PathGraph.Node> nodeById,
                                 Map<String, NodeState> states, Map<String, String> reasons, Set<String> clear,
                                 Set<String> achieved, Map<String, EvaluatedPath> subpaths, PublishedTask nextTask) {
    }

    private EvaluatedPath evaluate(LearningPath path, Situation s, boolean outerEnforce, int depth) {
        boolean effectiveEnforce = path.isEnforce() || outerEnforce;
        PathGraph.Graph graph = read(path.getGraph());
        // Stationen, deren Modul, Aufgabe oder eingebundener Lernpfad es nicht mehr gibt, fallen weg
        List<PathGraph.Node> nodes = graph.nodes().stream().filter(s::exists).toList();
        Map<String, PathGraph.Node> nodeById = nodes.stream().collect(Collectors.toMap(PathGraph.Node::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Set<String> ids = nodeById.keySet();
        List<PathGraph.Edge> edges = graph.edges().stream().filter(e -> ids.contains(e.from()) && ids.contains(e.to())).toList();
        PathGraph.Graph live = new PathGraph.Graph(nodes, edges);
        Map<String, List<String>> predecessors = PathGraph.predecessors(live);
        Map<String, List<String>> requiredPredecessors = PathGraph.requiredPredecessors(live);
        List<PathGraph.Node> ordered = PathGraph.order(live);

        Map<String, String> chosenByNode = new HashMap<>();
        Set<String> unlockedNodes = new HashSet<>();
        Map<String, Set<String>> unlockedBranches = new HashMap<>();
        if (path.getId() != null && s.user().getId() != null) {
            for (LearningPathChoice c : choiceRepository.findByUserIdAndPathId(s.user().getId(), path.getId())) {
                PathGraph.Node cn = nodeById.get(c.getNodeId());
                boolean edgeExists = edges.stream().anyMatch(e -> e.from().equals(c.getNodeId()) && e.to().equals(c.getChosenEdgeTo()));
                if (cn != null && cn.kind() == PathGraph.NodeKind.CHOICE && edgeExists) {
                    chosenByNode.put(c.getNodeId(), c.getChosenEdgeTo());
                }
            }
            for (LearningPathOverride ov : overrideRepository.findByUserIdAndPathId(s.user().getId(), path.getId())) {
                if (LearningPathOverride.UNLOCK_NODE.equals(ov.getKind())) {
                    unlockedNodes.add(ov.getNodeId());
                } else if (LearningPathOverride.UNLOCK_BRANCH.equals(ov.getKind()) && ov.getTarget() != null && !ov.getTarget().isBlank()) {
                    boolean edgeExists = edges.stream().anyMatch(e -> e.from().equals(ov.getNodeId()) && e.to().equals(ov.getTarget()));
                    if (edgeExists) {
                        unlockedBranches.computeIfAbsent(ov.getNodeId(), k -> new LinkedHashSet<>()).add(ov.getTarget());
                    }
                }
            }
        }
        Set<String> excluded = new HashSet<>();
        for (Map.Entry<String, String> entry : chosenByNode.entrySet()) {
            Set<String> allowed = new LinkedHashSet<>();
            allowed.add(entry.getValue());
            allowed.addAll(unlockedBranches.getOrDefault(entry.getKey(), Set.of()));
            excluded.addAll(PathGraph.excludedByChoice(live, entry.getKey(), allowed));
        }

        Map<String, NodeState> states = new HashMap<>();
        Map<String, String> reasons = new HashMap<>();
        Map<String, Station> stations = new HashMap<>();
        Map<String, EvaluatedPath> subpaths = new HashMap<>();
        Map<String, Integer> gateDone = new HashMap<>();
        Map<String, Integer> gateTotal = new HashMap<>();
        // clear: Station und alles davor ist erledigt oder gar nicht machbar – erst dann geht es dahinter weiter
        Set<String> clear = new HashSet<>();
        // achieved: Station ist tatsächlich erledigt (nicht nur wegen gesperrter/abgelaufener/ausgeschlossener Vorgänger übersprungen)
        Set<String> achieved = new HashSet<>();

        for (PathGraph.Node node : ordered) {
            boolean teacherUnlocked = unlockedNodes.contains(node.id());
            List<String> allPreds = predecessors.getOrDefault(node.id(), List.of());
            if (!teacherUnlocked && (excluded.contains(node.id()) || (!allPreds.isEmpty() && allPreds.stream().allMatch(excluded::contains)))) {
                excluded.add(node.id());
                states.put(node.id(), NodeState.EXCLUDED);
                reasons.put(node.id(), "Du hast dich für einen anderen Weg entschieden.");
                clear.add(node.id());
                if (node.subpath() && depth < MAX_PATH_DEPTH) {
                    LearningPath sub = s.path(node.path());
                    if (sub != null) {
                        subpaths.put(node.id(), evaluate(sub, s, effectiveEnforce, depth + 1));
                    }
                }
                continue;
            }
            excluded.remove(node.id());
            List<String> baseReq = effectiveEnforce
                    ? requiredPredecessors.getOrDefault(node.id(), List.of()).stream()
                            .filter(id -> !unlockedBranches.getOrDefault(id, Set.of()).contains(node.id()))
                            .toList()
                    : List.of();
            List<String> choicePreds = allPreds.stream()
                    .filter(id -> nodeById.get(id) != null && nodeById.get(id).choice())
                    .filter(id -> !unlockedBranches.getOrDefault(id, Set.of()).contains(node.id()))
                    .toList();
            List<String> reqPreds = choicePreds.isEmpty() ? baseReq
                    : Stream.concat(baseReq.stream(), choicePreds.stream()).distinct().toList();
            if (node.gate()) {
                List<String> inputs = !reqPreds.isEmpty() ? reqPreds : allPreds;
                int m = inputs.size();
                int achievedCount = (int) inputs.stream().filter(achieved::contains).count();
                int unfeasibleCount = (int) inputs.stream().filter(id -> clear.contains(id) && !achieved.contains(id)).count();
                int pendingCount = m - achievedCount - unfeasibleCount;
                gateDone.put(node.id(), achievedCount);
                gateTotal.put(node.id(), m);
                if (teacherUnlocked) {
                    states.put(node.id(), NodeState.DONE);
                    clear.add(node.id());
                    achieved.add(node.id());
                    reasons.put(node.id(), "Von deiner Lehrkraft freigeschaltet.");
                    continue;
                }
                List<String> openNames = inputs.stream()
                        .filter(id -> !clear.contains(id))
                        .map(id -> s.label(nodeById.get(id)))
                        .toList();
                NodeState state;
                if (node.kind() == PathGraph.NodeKind.AND) {
                    boolean gateClear = pendingCount == 0;
                    boolean gateAchieved = gateClear && (achievedCount > 0 || m == 0);
                    if (gateClear) {
                        state = NodeState.DONE;
                        clear.add(node.id());
                        if (gateAchieved) achieved.add(node.id());
                        reasons.put(node.id(), "Alle Eingänge erfüllt.");
                    } else {
                        state = !reqPreds.isEmpty() ? NodeState.BLOCKED : NodeState.UPCOMING;
                        reasons.put(node.id(), "Schaffe alle dieser Stationen: „" + String.join("“, „", openNames) + "“.");
                    }
                } else {
                    int k = Math.min(node.min() != null ? node.min() : 1, Math.max(1, m));
                    boolean normalMet = achievedCount >= k;
                    boolean fallbackMet = pendingCount == 0 && achievedCount >= 1;
                    boolean allUnfeasible = m > 0 && unfeasibleCount == m;
                    boolean gateClear = normalMet || fallbackMet || allUnfeasible;
                    boolean gateAchieved = normalMet || fallbackMet;
                    if (gateClear) {
                        state = NodeState.DONE;
                        clear.add(node.id());
                        if (gateAchieved) achieved.add(node.id());
                        reasons.put(node.id(), "Bedingung erfüllt.");
                    } else {
                        state = !reqPreds.isEmpty() ? NodeState.BLOCKED : NodeState.UPCOMING;
                        String explain = k <= 1
                                ? "Schaffe eine dieser Stationen: „" + String.join("“, „", openNames) + "“."
                                : "Schaffe mindestens " + k + " dieser Stationen: „" + String.join("“, „", openNames) + "“.";
                        reasons.put(node.id(), explain);
                    }
                }
                states.put(node.id(), state);
                continue;
            }

            boolean allReady = teacherUnlocked || clear.containsAll(allPreds);
            boolean requiredReady = teacherUnlocked || clear.containsAll(reqPreds);
            if (!requiredReady) {
                List<String> openPreds = reqPreds.stream().filter(id -> !clear.contains(id)).toList();
                Optional<PathGraph.Node> openChoice = openPreds.stream()
                        .map(nodeById::get)
                        .filter(n -> n != null && n.choice())
                        .findFirst();
                if (openChoice.isPresent()) {
                    reasons.put(node.id(), "Wähle zuerst einen Weg bei „" + s.label(openChoice.get()) + "“.");
                } else if (openPreds.size() == 1
                        && nodeById.get(openPreds.getFirst()) != null
                        && nodeById.get(openPreds.getFirst()).gate()
                        && (nodeById.get(openPreds.getFirst()).label() == null || nodeById.get(openPreds.getFirst()).label().isBlank())
                        && reasons.containsKey(openPreds.getFirst())) {
                    reasons.put(node.id(), reasons.get(openPreds.getFirst()));
                } else {
                    List<String> openNames = openPreds.stream()
                            .map(id -> s.label(nodeById.get(id)))
                            .toList();
                    reasons.put(node.id(), "Erst „" + String.join("“, „", openNames) + "“ abschließen.");
                }
            }
            NodeState state;
            if (node.choice()) {
                if (!requiredReady) {
                    state = NodeState.BLOCKED;
                } else if (chosenByNode.containsKey(node.id())) {
                    state = NodeState.DONE;
                    clear.add(node.id());
                    achieved.add(node.id());
                    String chosenTo = chosenByNode.get(node.id());
                    String chosenLabel = edges.stream()
                            .filter(e -> e.from().equals(node.id()) && e.to().equals(chosenTo))
                            .map(PathGraph.Edge::label)
                            .filter(l -> l != null && !l.isBlank())
                            .findFirst()
                            .orElseGet(() -> s.label(nodeById.get(chosenTo)));
                    reasons.put(node.id(), "Gewählter Weg: „" + chosenLabel + "“.");
                } else {
                    state = NodeState.AVAILABLE;
                    reasons.put(node.id(), "Wähle einen Weg, um fortzufahren.");
                }
            } else if (node.kind() == PathGraph.NodeKind.MILESTONE) {
                if (!requiredReady) {
                    state = NodeState.BLOCKED;
                } else if (!reqPreds.isEmpty()) {
                    state = NodeState.DONE;
                } else {
                    state = allReady ? NodeState.DONE : NodeState.UPCOMING;
                }
                if (state == NodeState.DONE) {
                    clear.add(node.id());
                    if (allPreds.isEmpty() || allPreds.stream().anyMatch(achieved::contains)) {
                        achieved.add(node.id());
                    }
                }
            } else if (node.subpath()) {
                LearningPath sub = depth < MAX_PATH_DEPTH ? s.path(node.path()) : null;
                if (sub == null) {
                    state = NodeState.LOCKED;
                    clear.add(node.id());
                } else {
                    EvaluatedPath subEval = evaluate(sub, s, effectiveEnforce, depth + 1);
                    subpaths.put(node.id(), subEval);
                    Station station = subpathStation(sub, subEval);
                    stations.put(node.id(), station);
                    if (station.state() == NodeState.LOCKED) {
                        state = NodeState.LOCKED;
                    } else if (!requiredReady) {
                        state = NodeState.BLOCKED;
                    } else if (station.state() == NodeState.DONE) {
                        state = NodeState.DONE;
                    } else if (station.state() == NodeState.AVAILABLE && !allReady) {
                        state = NodeState.UPCOMING;
                    } else {
                        state = station.state();
                    }
                    boolean canClear = state == NodeState.DONE
                            ? requiredReady
                            : (!reqPreds.isEmpty() ? requiredReady : allReady) && settled(state);
                    if (canClear) {
                        clear.add(node.id());
                        if (state == NodeState.DONE) achieved.add(node.id());
                    }
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
                boolean canClear = state == NodeState.DONE
                        ? requiredReady
                        : (!reqPreds.isEmpty() ? requiredReady : allReady) && settled(state);
                if (canClear) {
                    clear.add(node.id());
                    if (state == NodeState.DONE) achieved.add(node.id());
                }
            }
            if (teacherUnlocked) {
                clear.add(node.id());
                achieved.add(node.id());
            }
            states.put(node.id(), state);
        }

        // Bei offenen ODER-Blöcken wird der Zweig mit dem meisten Fortschritt bevorzugt, bei Gleichstand nach Lage auf der Karte
        Map<String, Integer> branchProgress = orBranchProgress(live, predecessors, states, stations);
        String recommendedId = null;
        PublishedTask nextTask = null;
        Recommendation recommendation = null;
        List<PathGraph.Node> candidates = ordered.stream()
                .filter(n -> states.get(n.id()) == NodeState.AVAILABLE && stations.get(n.id()) != null && stations.get(n.id()).next() != null)
                .toList();
        if (!candidates.isEmpty()) {
            PathGraph.Node best = candidates.stream()
                    .max(Comparator.comparingInt((PathGraph.Node n) -> branchProgress.getOrDefault(n.id(), 0))
                            .thenComparingInt(n -> -ordered.indexOf(n)))
                    .orElseThrow();
            recommendedId = best.id();
            nextTask = stations.get(best.id()).next();
            String reason = best.kind() == PathGraph.NodeKind.TASK
                    ? "Nächste Station auf dem Lernpfad „" + path.getTitle() + "“."
                    : "Nächste Station auf dem Lernpfad „" + path.getTitle() + "“: Modul „" + nextTask.module().getTitle() + "“.";
            recommendation = recommendation(nextTask, reason, path);
        }

        List<NodeView> views = new ArrayList<>();
        int done = 0, total = 0;
        for (PathGraph.Node node : nodes) {
            NodeState state = states.getOrDefault(node.id(), NodeState.LOCKED);
            Station station = stations.get(node.id());
            if (node.station() && state != NodeState.EXCLUDED) {
                total++;
                if (state == NodeState.DONE) done++;
            } else if (node.subpath() && state != NodeState.EXCLUDED && station != null) {
                total += station.total();
                done += station.done();
            }
            String href = station == null || state == NodeState.LOCKED || state == NodeState.BLOCKED || state == NodeState.EXCLUDED ? null : station.href();
            int nodeDone = node.gate() ? gateDone.getOrDefault(node.id(), 0) : (station == null || state == NodeState.EXCLUDED ? 0 : station.done());
            int nodeTotal = node.gate() ? gateTotal.getOrDefault(node.id(), 0) : (station == null ? 0 : station.total());
            String nodeReason = node.gate() || node.choice() || state == NodeState.BLOCKED || state == NodeState.EXCLUDED ? reasons.get(node.id()) : null;
            List<ChoiceOption> options = null;
            String chosenTo = null;
            if (node.choice()) {
                chosenTo = chosenByNode.get(node.id());
                String finalChosenTo = chosenTo;
                Set<String> unlockedTargets = unlockedBranches.getOrDefault(node.id(), Set.of());
                options = edges.stream()
                        .filter(e -> e.from().equals(node.id()))
                        .map(e -> new ChoiceOption(
                                e.to(),
                                e.label() != null && !e.label().isBlank() ? e.label() : s.label(nodeById.get(e.to())),
                                e.hint(),
                                e.to().equals(finalChosenTo),
                                unlockedTargets.contains(e.to())))
                        .toList();
            }
            String subpathSlug = node.subpath() ? node.path() : null;
            views.add(new NodeView(node.id(), node.kind(), s.label(node), s.icon(node), node.x(), node.y(), state,
                    href, nodeDone, nodeTotal, node.id().equals(recommendedId), nodeReason, node.min(), options, chosenTo, subpathSlug,
                    unlockedNodes.contains(node.id())));
        }
        PathView pathView = new PathView(path.getSlug(), path.getTitle(), path.getDescription(), effectiveEnforce,
                views, edges, done, total, recommendation);
        return new EvaluatedPath(pathView, live, nodeById, states, reasons, clear, achieved, subpaths, nextTask);
    }

    private static Station subpathStation(LearningPath sub, EvaluatedPath subEval) {
        String href = "/learning-paths/" + sub.getSlug();
        int done = subEval.view().stationsDone();
        int total = subEval.view().stations();
        List<PathGraph.Node> allNodes = subEval.live().nodes();
        if (allNodes.isEmpty()) {
            return new Station(NodeState.LOCKED, null, 0, 0, null);
        }
        Set<String> hasOutgoing = subEval.live().edges().stream().map(PathGraph.Edge::from).collect(Collectors.toSet());
        List<PathGraph.Node> activeSinks = allNodes.stream()
                .filter(n -> !hasOutgoing.contains(n.id()))
                .filter(n -> subEval.states().get(n.id()) != NodeState.EXCLUDED)
                .toList();
        if (activeSinks.isEmpty()) {
            return new Station(NodeState.LOCKED, null, done, total, null);
        }
        boolean allLocked = allNodes.stream()
                .filter(n -> subEval.states().get(n.id()) != NodeState.EXCLUDED)
                .allMatch(n -> subEval.states().get(n.id()) == NodeState.LOCKED);
        if (allLocked) {
            return new Station(NodeState.LOCKED, null, done, total, null);
        }
        boolean sinksDone = activeSinks.stream().allMatch(n -> subEval.states().get(n.id()) == NodeState.DONE && subEval.clear().contains(n.id()));
        if (sinksDone) {
            return new Station(NodeState.DONE, href, done, total, null);
        }
        boolean sinksSettled = activeSinks.stream().allMatch(n -> settled(subEval.states().get(n.id())) && subEval.clear().contains(n.id()));
        if (sinksSettled) {
            return new Station(NodeState.CLOSED, href, done, total, null);
        }
        return new Station(NodeState.AVAILABLE, href, done, total, subEval.nextTask());
    }

    private static Map<String, Integer> orBranchProgress(PathGraph.Graph live, Map<String, List<String>> predecessors,
                                                         Map<String, NodeState> states, Map<String, Station> stations) {
        Map<String, Integer> branchProgress = new HashMap<>();
        Map<String, PathGraph.Node> byId = live.nodes().stream()
                .collect(Collectors.toMap(PathGraph.Node::id, Function.identity(), (a, b) -> a));
        for (PathGraph.Node gate : live.nodes()) {
            if (gate.kind() != PathGraph.NodeKind.OR || states.get(gate.id()) == NodeState.DONE) continue;
            List<String> inputs = predecessors.getOrDefault(gate.id(), List.of());
            if (inputs.size() < 2) continue;
            List<Set<String>> branches = inputs.stream()
                    .map(in -> branchAncestors(in, predecessors, byId))
                    .toList();
            Set<String> common = new HashSet<>(branches.getFirst());
            for (Set<String> b : branches) common.retainAll(b);
            for (Set<String> raw : branches) {
                Set<String> branch = new HashSet<>(raw);
                branch.removeAll(common);
                int progress = branch.stream()
                        .mapToInt(id -> stations.containsKey(id) ? stations.get(id).done() : 0)
                        .sum();
                for (String member : branch) {
                    branchProgress.merge(member, progress, Math::max);
                }
            }
        }
        return branchProgress;
    }

    /** Vorgänger eines ODER-Zweigs bis zum nächsten inneren ODER-Gatter. */
    private static Set<String> branchAncestors(String startId, Map<String, List<String>> predecessors,
                                               Map<String, PathGraph.Node> byId) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(startId);
        while (!stack.isEmpty()) {
            String curr = stack.pop();
            if (!visited.add(curr)) continue;
            PathGraph.Node node = byId.get(curr);
            if (node != null && node.kind() == PathGraph.NodeKind.OR) continue;
            for (String p : predecessors.getOrDefault(curr, List.of())) {
                stack.push(p);
            }
        }
        return visited;
    }

    /** Ein Vorgänger, der erledigt ist oder gar nicht erledigt werden kann, hält niemanden auf. */
    private static boolean settled(NodeState state) {
        return state == NodeState.DONE || state == NodeState.LOCKED || state == NodeState.CLOSED || state == NodeState.EXCLUDED;
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
        private final User user;
        private final AccessPolicy.Releases releases;
        private final Map<Long, ProgressStatus> status;
        private final Map<String, Module> modules = new LinkedHashMap<>();
        private final Map<String, List<PublishedTask>> tasks = new HashMap<>();
        private final Map<String, LearningPath> pathsBySlug = new LinkedHashMap<>();

        Situation(User user, AccessPolicy.Releases releases) {
            this.user = user;
            this.releases = releases;
            this.status = progressService.statusByTask(user.getId());
            contentService.modules().forEach(m -> modules.put(m.getSlug(), m));
            repository.findAllByOrderByPositionAscIdAsc().forEach(p -> pathsBySlug.put(p.getSlug(), p));
        }

        User user() {
            return user;
        }

        Collection<Module> modules() {
            return modules.values();
        }

        Module module(String slug) {
            return modules.get(slug);
        }

        LearningPath path(String slug) {
            return slug == null ? null : pathsBySlug.get(slug);
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
                case MILESTONE, AND, OR, CHOICE -> true;
                case MODULE -> modules.containsKey(node.module());
                case TASK -> task(node.module(), node.task()).isPresent();
                case PATH -> node.path() != null && pathsBySlug.containsKey(node.path());
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
            if (node.kind() == PathGraph.NodeKind.CHOICE) return "Weiche";
            if (node.kind() == PathGraph.NodeKind.AND) return "UND-Block";
            if (node.kind() == PathGraph.NodeKind.OR) {
                int min = node.min() != null ? node.min() : 1;
                return min > 1 ? "ODER-Block (mind. " + min + ")" : "ODER-Block";
            }
            if (node.kind() == PathGraph.NodeKind.PATH) {
                LearningPath sub = path(node.path());
                return sub != null ? sub.getTitle() : node.path();
            }
            if (node.kind() == PathGraph.NodeKind.MODULE) {
                Module m = modules.get(node.module());
                String title = m != null ? m.getTitle() : node.module();
                return node.partial() ? title + " (" + node.tasks().size() + " Aufgaben)" : title;
            }
            return task(node.module(), node.task()).map(t -> t.version().getTitle()).orElse(node.task());
        }

        String icon(PathGraph.Node node) {
            if (node.kind() == PathGraph.NodeKind.MILESTONE) return "🏁";
            if (node.kind() == PathGraph.NodeKind.CHOICE) return "🔀";
            if (node.kind() == PathGraph.NodeKind.PATH) return "🧩";
            if (node.kind() == PathGraph.NodeKind.AND) return "&";
            if (node.kind() == PathGraph.NodeKind.OR) {
                int min = node.min() != null ? node.min() : 1;
                return "≥" + min;
            }
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
        if (p.getId() != null) {
            List<LearningPathChoice> choices = choiceRepository.findByPathId(p.getId());
            if (!choices.isEmpty()) {
                Map<String, Long> byNode = choices.stream()
                        .collect(Collectors.groupingBy(LearningPathChoice::getNodeId, Collectors.counting()));
                for (PathGraph.Node node : graph.nodes()) {
                    if (!node.choice()) continue;
                    long count = byNode.getOrDefault(node.id(), 0L);
                    if (count > 0) {
                        String name = node.label() != null && !node.label().isBlank() ? node.label() : "Weiche";
                        result.add(count + " Schüler haben an dieser Weiche („" + name + "“) schon gewählt.");
                    }
                }
            }
        }
        if (p.getCourseId() != null && p.isPublished()) {
            AccessPolicy.Releases courseRel = accessPolicy.forCourse(p.getCourseId());
            Set<String> hasSuccessors = graph.edges().stream().map(PathGraph.Edge::from).collect(Collectors.toSet());
            Map<String, Module> modules = contentService.modules().stream().collect(Collectors.toMap(Module::getSlug, Function.identity()));
            Set<String> unfeasibleNodes = new HashSet<>();
            for (PathGraph.Node node : graph.nodes()) {
                if (!node.station()) continue;
                Module m = modules.get(node.module());
                if (m == null) continue;
                List<PublishedTask> all = contentService.publishedTasks(m);
                AccessPolicy.ModuleAccess ma = courseRel.moduleAccess(m, all);
                String name = node.label() != null && !node.label().isBlank() ? node.label() : m.getTitle();
                if (!ma.visible()) {
                    unfeasibleNodes.add(node.id());
                    if (hasSuccessors.contains(node.id())) {
                        result.add("Station „" + name + "“ ist im Kurs gesperrt und hält nachfolgende Stationen nicht auf.");
                    }
                } else if (!ma.open()) {
                    unfeasibleNodes.add(node.id());
                    if (hasSuccessors.contains(node.id())) {
                        result.add("Station „" + name + "“: Die Bearbeitungszeit ist abgelaufen – sie hält nachfolgende Stationen nicht auf.");
                    }
                }
            }
            Map<String, List<String>> preds = PathGraph.predecessors(graph);
            for (PathGraph.Node node : graph.nodes()) {
                if (!node.gate()) continue;
                List<String> inputs = preds.getOrDefault(node.id(), List.of());
                long feasible = inputs.stream().filter(id -> !unfeasibleNodes.contains(id)).count();
                String gateName = node.label() != null && !node.label().isBlank() ? node.label() : node.id();
                if (feasible == 0) {
                    result.add("Logik-Block „" + gateName + "“ hat im Kurs keine machbaren Eingänge und hält nachfolgende Stationen nicht auf.");
                } else if (node.kind() == PathGraph.NodeKind.OR && node.min() != null && feasible < node.min()) {
                    result.add("ODER-Block „" + gateName + "“ hat nur " + feasible + " machbare Eingänge (gefordert: mindestens " + node.min() + ").");
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

    private PathSummary summary(LearningPath p, List<LearningPath> allPaths, Map<String, PathGraph.Graph> graphs) {
        int stations = countStations(graphs.getOrDefault(p.getSlug(), read(p.getGraph())), graphs, new HashSet<>(), 0);
        return new PathSummary(p.getId(), p.getSlug(), p.getTitle(), p.getDescription(), p.isPublished(),
                p.isEnforce(), p.isTemplateOnly(), stations, p.getCourseId(), courseName(p),
                usedInTitles(p.getSlug(), allPaths), p.getUpdatedAt());
    }

    private static int countStations(PathGraph.Graph graph, Map<String, PathGraph.Graph> graphs, Set<String> seen, int depth) {
        if (graph == null || depth > MAX_PATH_DEPTH) return 0;
        int count = 0;
        for (PathGraph.Node n : graph.nodes()) {
            if (n.subpath() && n.path() != null) {
                if (seen.add(n.path())) {
                    count += countStations(graphs.get(n.path()), graphs, seen, depth + 1);
                    seen.remove(n.path());
                }
            } else if (!n.gate() && !n.choice()) {
                count++;
            }
        }
        return count;
    }

    private PathAdmin admin(LearningPath p, List<String> applied) {
        PathGraph.Graph graph = read(p.getGraph());
        List<LearningPath> allPaths = repository.findAllByOrderByPositionAscIdAsc();
        return new PathAdmin(p.getId(), p.getSlug(), p.getTitle(), p.getDescription(), p.isPublished(),
                p.isEnforce(), p.isTemplateOnly(), graph, p.getCourseId(), courseName(p), p.isLockOthers(),
                p.getUnlockRestAfter(), usedInTitles(p.getSlug(), allPaths), applied, warnings(p, graph), p.getUpdatedAt());
    }

    private String courseName(LearningPath p) {
        return p.getCourseId() == null ? null : courseService.get(p.getCourseId()).getName();
    }
}

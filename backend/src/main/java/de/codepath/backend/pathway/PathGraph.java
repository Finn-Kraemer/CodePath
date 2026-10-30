package de.codepath.backend.pathway;

import de.codepath.backend.platform.ApiException;

import java.util.*;

/**
 * Die Karte eines Lernpfads: Knoten (Modul, einzelne Aufgabe oder Meilenstein) mit Position auf der
 * Zeichenfläche und gerichtete Verbindungen "erst das, dann das". Verbindungen dürfen keinen Kreis bilden.
 */
public final class PathGraph {

    static final int MAX_NODES = 150;
    static final int MAX_EDGES = 400;
    static final int MAX_COORDINATE = 4000;

    private PathGraph() {
    }

    public enum NodeKind { MODULE, TASK, MILESTONE, AND, OR, CHOICE, PATH, BONUS, REMEDIAL }

    public enum GateMode { TOTAL, EACH }

    public enum GateFallback { TEACHER, REMEDIAL, OPEN }

    public record GateRequirement(Integer minPercent, GateMode mode, List<String> scope,
                                  GateFallback fallback, String fallbackRemedialNode) {
        public GateRequirement {
            minPercent = minPercent == null ? 80 : minPercent;
            mode = mode == null ? GateMode.TOTAL : mode;
            fallback = fallback == null ? GateFallback.TEACHER : fallback;
            scope = scope == null || scope.isEmpty() ? null : List.copyOf(new LinkedHashSet<>(scope));
            fallbackRemedialNode = fallbackRemedialNode != null && !fallbackRemedialNode.isBlank()
                    ? fallbackRemedialNode.strip() : null;
        }

        public GateRequirement(Integer minPercent) {
            this(minPercent, GateMode.TOTAL, null, GateFallback.TEACHER, null);
        }

        public GateRequirement(Integer minPercent, GateMode mode, List<String> scope) {
            this(minPercent, mode, scope, GateFallback.TEACHER, null);
        }
    }

    /**
     * @param module           Slug des Moduls (MODULE, TASK und REMEDIAL)
     * @param task             Slug der Aufgabe (TASK und REMEDIAL)
     * @param tasks            nur MODULE: Slugs der Aufgaben, die diese Station nutzt; leer oder null für alle
     * @param label            eigene Beschriftung; Pflicht für Meilensteine und Bonus-Stationen, sonst gilt der Titel von Modul bzw. Aufgabe
     * @param min              nur OR / CHOICE: Mindestanzahl erfüllter Eingänge bzw. zu wählender Wege (Standard 1)
     * @param max              optional für CHOICE: Höchstanzahl zu wählender Wege (Standard 1)
     * @param path             nur PATH: Slug des eingebundenen Lernpfad-Bausteins
     * @param points           nur BONUS: Bonuspunkte (1–1000)
     * @param knowledgeArticle optional für REMEDIAL: Slug des empfohlenen Wissensartikels
     * @param boss             Markierung als Boss-Aufgabe
     * @param requirement      optionale Punkte-Schwelle zur Freischaltung
     */
    public record Node(String id, NodeKind kind, String module, String task, List<String> tasks, String label,
                       Integer min, Integer max, String path, Integer points, String knowledgeArticle,
                       Boolean boss, GateRequirement requirement, int x, int y) {
        public Node {
            boss = Boolean.TRUE.equals(boss);
            tasks = tasks == null || tasks.isEmpty() ? null : List.copyOf(new LinkedHashSet<>(tasks));
            if (kind == NodeKind.OR) {
                min = min == null ? Integer.valueOf(1) : min;
            } else if (kind != NodeKind.CHOICE) {
                min = null;
            }
            max = kind == NodeKind.CHOICE ? max : null;
            path = kind == NodeKind.PATH && path != null && !path.isBlank() ? path.strip() : null;
            points = kind == NodeKind.BONUS ? points : null;
            knowledgeArticle = kind == NodeKind.REMEDIAL && knowledgeArticle != null && !knowledgeArticle.isBlank()
                    ? knowledgeArticle.strip() : null;
        }

        public Node(String id, NodeKind kind, String module, String task, List<String> tasks, String label,
                    Integer min, Integer max, String path, Integer points, String knowledgeArticle, int x, int y) {
            this(id, kind, module, task, tasks, label, min, max, path, points, knowledgeArticle, false, null, x, y);
        }

        public Node(String id, NodeKind kind, String module, String task, List<String> tasks, String label,
                    Integer min, Integer max, String path, Integer points, int x, int y) {
            this(id, kind, module, task, tasks, label, min, max, path, points, null, x, y);
        }

        public Node(String id, NodeKind kind, String module, String task, List<String> tasks, String label,
                    Integer min, Integer max, String path, int x, int y) {
            this(id, kind, module, task, tasks, label, min, max, path, null, null, x, y);
        }

        public Node(String id, NodeKind kind, String module, String task, List<String> tasks, String label,
                    Integer min, Integer max, int x, int y) {
            this(id, kind, module, task, tasks, label, min, max, null, null, null, x, y);
        }

        public Node(String id, NodeKind kind, String module, String task, List<String> tasks, String label,
                    Integer min, int x, int y) {
            this(id, kind, module, task, tasks, label, min, null, null, null, null, x, y);
        }

        public Node(String id, NodeKind kind, String module, String task, List<String> tasks, String label, int x, int y) {
            this(id, kind, module, task, tasks, label, null, null, null, null, null, x, y);
        }

        /** Nutzt die Station nur einen Teil der Aufgaben ihres Moduls? */
        public boolean partial() {
            return kind == NodeKind.MODULE && tasks != null;
        }

        public boolean gate() {
            return kind == NodeKind.AND || kind == NodeKind.OR;
        }

        public boolean choice() {
            return kind == NodeKind.CHOICE;
        }

        public boolean subpath() {
            return kind == NodeKind.PATH;
        }

        public boolean bonus() {
            return kind == NodeKind.BONUS;
        }

        public boolean remedial() {
            return kind == NodeKind.REMEDIAL;
        }

        public boolean station() {
            return kind == NodeKind.MODULE || kind == NodeKind.TASK || kind == NodeKind.REMEDIAL;
        }
    }

    public enum ConditionRule {
        PERFECT_FIRST_TRY,
        SCORE_AT_LEAST,
        STRUGGLED,
        DEFAULT
    }

    public record EdgeCondition(ConditionRule rule, String task, Double scoreThreshold,
                                Integer failedAttemptsThreshold, Integer hintsThreshold) {
        public EdgeCondition {
            if (rule == null) {
                rule = ConditionRule.DEFAULT;
            }
            task = task != null && !task.isBlank() ? task.strip() : null;
        }

        public EdgeCondition(ConditionRule rule, String task) {
            this(rule, task, null, null, null);
        }
    }

    public record Edge(String from, String to, Boolean required, String label, String hint, EdgeCondition condition) {
        public Edge {
            required = Boolean.TRUE.equals(required);
            label = label == null || label.isBlank() ? null : label.strip();
            hint = hint == null || hint.isBlank() ? null : hint.strip();
        }

        public Edge(String from, String to, Boolean required, String label, String hint) {
            this(from, to, required, label, hint, null);
        }

        public Edge(String from, String to, Boolean required) {
            this(from, to, required, null, null, null);
        }

        public Edge(String from, String to) {
            this(from, to, false, null, null, null);
        }
    }

    public record Graph(List<Node> nodes, List<Edge> edges) {
        public Graph {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
        }

        public static Graph empty() {
            return new Graph(List.of(), List.of());
        }
    }

    /** Prüft die Form; ob Module und Aufgaben existieren, prüft der Service. */
    static void validate(Graph graph) {
        if (graph.nodes().size() > MAX_NODES) throw ApiException.badRequest("Höchstens " + MAX_NODES + " Stationen je Lernpfad.");
        if (graph.edges().size() > MAX_EDGES) throw ApiException.badRequest("Höchstens " + MAX_EDGES + " Verbindungen je Lernpfad.");
        Set<String> ids = new HashSet<>();
        for (Node node : graph.nodes()) {
            if (node == null || node.id() == null || !node.id().matches("[A-Za-z0-9_-]{1,40}")) {
                throw ApiException.badRequest("Jede Station braucht eine ID (Buchstaben, Ziffern, - und _).");
            }
            if (!ids.add(node.id())) throw ApiException.badRequest("Die ID " + node.id() + " kommt doppelt vor.");
            if (node.kind() == null) throw ApiException.badRequest("Station " + node.id() + ": Art fehlt.");
            if (node.x() < 0 || node.y() < 0 || node.x() > MAX_COORDINATE || node.y() > MAX_COORDINATE) {
                throw ApiException.badRequest("Station " + node.id() + " liegt außerhalb der Zeichenfläche.");
            }
            if (node.label() != null && node.label().length() > 80) {
                throw ApiException.badRequest("Station " + node.id() + ": Beschriftung höchstens 80 Zeichen.");
            }
            switch (node.kind()) {
                case MILESTONE -> {
                    if (node.label() == null || node.label().isBlank()) throw ApiException.badRequest("Ein Meilenstein braucht eine Beschriftung.");
                }
                case MODULE -> {
                    if (isBlank(node.module())) throw ApiException.badRequest("Station " + node.id() + ": Modul fehlt.");
                    if (node.tasks() != null && node.tasks().size() > 200) throw ApiException.badRequest("Station " + node.id() + ": zu viele Aufgaben.");
                }
                case TASK -> {
                    if (isBlank(node.module()) || isBlank(node.task())) throw ApiException.badRequest("Station " + node.id() + ": Aufgabe fehlt.");
                }
                case PATH -> {
                    if (isBlank(node.path())) throw ApiException.badRequest("Baustein „" + node.id() + "“ braucht die Adresse eines Lernpfads.");
                    if (!isBlank(node.module()) || !isBlank(node.task()) || node.tasks() != null) {
                        throw ApiException.badRequest("Baustein „" + node.id() + "“ verweist auf einen Lernpfad, nicht auf ein einzelnes Modul oder eine Aufgabe.");
                    }
                }
                case BONUS -> {
                    if (isBlank(node.label())) throw ApiException.badRequest("Eine Bonus-Station braucht eine Beschriftung.");
                    if (node.points() == null || node.points() < 1 || node.points() > 1000) {
                        throw ApiException.badRequest("Bonuspunkte müssen zwischen 1 und 1000 liegen.");
                    }
                }
                case REMEDIAL -> {
                    if (isBlank(node.module()) || isBlank(node.task())) {
                        throw ApiException.badRequest("Stütz-Knoten „" + node.id() + "“ braucht ein Modul und eine Aufgabe.");
                    }
                }
                case AND, OR, CHOICE -> {
                }
            }
        }
        Set<String> seen = new HashSet<>();
        Map<String, Integer> inCount = new HashMap<>();
        Map<String, Integer> outCount = new HashMap<>();
        Map<String, List<Edge>> outgoingEdges = new HashMap<>();
        Map<String, List<Edge>> incomingEdges = new HashMap<>();
        for (Edge edge : graph.edges()) {
            if (edge == null || !ids.contains(edge.from()) || !ids.contains(edge.to())) {
                throw ApiException.badRequest("Eine Verbindung zeigt auf eine Station, die es nicht gibt.");
            }
            if (edge.from().equals(edge.to())) throw ApiException.badRequest("Eine Station kann nicht mit sich selbst verbunden sein.");
            if (!seen.add(edge.from() + "→" + edge.to())) throw ApiException.badRequest("Eine Verbindung kommt doppelt vor.");
            if (edge.label() != null && edge.label().length() > 80) {
                throw ApiException.badRequest("Weg-Beschriftung höchstens 80 Zeichen.");
            }
            if (edge.hint() != null && edge.hint().length() > 200) {
                throw ApiException.badRequest("Weg-Kurzbeschreibung höchstens 200 Zeichen.");
            }
            inCount.merge(edge.to(), 1, Integer::sum);
            outCount.merge(edge.from(), 1, Integer::sum);
            outgoingEdges.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
            incomingEdges.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge);
        }
        for (Node node : graph.nodes()) {
            String name = isBlank(node.label()) ? node.id() : node.label();
            int inc = inCount.getOrDefault(node.id(), 0);
            int out = outCount.getOrDefault(node.id(), 0);
            if (node.remedial()) {
                List<Edge> inEdges = incomingEdges.getOrDefault(node.id(), List.of());
                if (inEdges.size() != 1 || inEdges.getFirst().condition() == null || inEdges.getFirst().condition().rule() != ConditionRule.STRUGGLED) {
                    throw ApiException.badRequest("Stütz-Knoten „" + name + "“ braucht genau eine eingehende Verbindung mit der Bedingung STRUGGLED.");
                }
            }
            List<Edge> outEdges = outgoingEdges.getOrDefault(node.id(), List.of());
            boolean hasConditional = outEdges.stream().anyMatch(e -> e.condition() != null);
            if (hasConditional) {
                long defaultCount = outEdges.stream()
                        .filter(e -> e.condition() != null && e.condition().rule() == ConditionRule.DEFAULT)
                        .count();
                if (defaultCount > 1) {
                    throw ApiException.badRequest("Station „" + name + "“ darf höchstens eine DEFAULT-Verbindung haben.");
                }
                if (defaultCount == 0) {
                    throw ApiException.badRequest("Station „" + name + "“ hat bedingte Verbindungen, braucht aber eine DEFAULT-Verbindung (Normalweg).");
                }
                for (Edge edge : outEdges) {
                    EdgeCondition cond = edge.condition();
                    if (cond == null || cond.rule() == ConditionRule.DEFAULT) continue;
                    if (isBlank(cond.task())) {
                        throw ApiException.badRequest("Bedingte Verbindung ab „" + name + "“ braucht eine Bezugsaufgabe.");
                    }
                    if (node.kind() == NodeKind.TASK) {
                        if (!cond.task().equals(node.task()) && !(node.module() + "/" + node.task()).equals(cond.task())) {
                            throw ApiException.badRequest("Bedingte Verbindung ab „" + name + "“: Die Bezugsaufgabe gehört nicht zu dieser Station.");
                        }
                    } else if (node.kind() == NodeKind.MODULE && node.partial()) {
                        if (!node.tasks().contains(cond.task()) && node.tasks().stream().noneMatch(t -> (node.module() + "/" + t).equals(cond.task()))) {
                            throw ApiException.badRequest("Bedingte Verbindung ab „" + name + "“: Die Bezugsaufgabe gehört nicht zu den Aufgaben dieser Station.");
                        }
                    }
                    if (cond.rule() == ConditionRule.SCORE_AT_LEAST) {
                        Double score = cond.scoreThreshold();
                        if (score == null || score < 0.0 || score > 100.0) {
                            throw ApiException.badRequest("Bedingte Verbindung ab „" + name + "“: Mindest-Punktzahl muss zwischen 0 und 100 % liegen.");
                        }
                    } else if (cond.rule() == ConditionRule.STRUGGLED) {
                        if (cond.failedAttemptsThreshold() != null && cond.failedAttemptsThreshold() < 1) {
                            throw ApiException.badRequest("Bedingte Verbindung ab „" + name + "“: Fehlversuche müssen mindestens 1 sein.");
                        }
                        if (cond.hintsThreshold() != null && cond.hintsThreshold() < 1) {
                            throw ApiException.badRequest("Bedingte Verbindung ab „" + name + "“: Tipps müssen mindestens 1 sein.");
                        }
                    }
                }
            }
            if (node.gate()) {
                String kindName = node.kind() == NodeKind.AND ? "UND-Block" : "ODER-Block";
                if (inc < 2 || out < 1) {
                    throw ApiException.badRequest(kindName + " „" + name + "“ braucht mindestens 2 eingehende und 1 ausgehende Verbindung.");
                }
                if (node.kind() == NodeKind.OR && (node.min() == null || node.min() < 1 || node.min() > inc)) {
                    throw ApiException.badRequest(kindName + " „" + name + "“: Mindestanzahl (" + node.min()
                            + ") muss zwischen 1 und der Anzahl der Eingänge (" + inc + ") liegen.");
                }
            } else if (node.choice()) {
                if (out < 2) {
                    throw ApiException.badRequest("Weiche „" + name + "“ braucht mindestens 2 ausgehende Verbindungen (Wege).");
                }
                for (Edge edge : outgoingEdges.getOrDefault(node.id(), List.of())) {
                    if (isBlank(edge.label())) {
                        throw ApiException.badRequest("Jeder Weg ab Weiche „" + name + "“ braucht eine Beschriftung.");
                    }
                }
            }
        }
        List<Node> orderedNodes = order(graph);
        if (orderedNodes.size() != graph.nodes().size()) {
            throw ApiException.badRequest("Die Verbindungen bilden einen Kreis – ein Lernpfad muss irgendwo anfangen und enden.");
        }

        Map<String, Set<String>> allPredecessors = new HashMap<>();
        for (Node node : orderedNodes) {
            Set<String> preds = new HashSet<>();
            for (Edge edge : incomingEdges.getOrDefault(node.id(), List.of())) {
                preds.add(edge.from());
                preds.addAll(allPredecessors.getOrDefault(edge.from(), Set.of()));
            }
            allPredecessors.put(node.id(), preds);
        }

        for (Node node : graph.nodes()) {
            if (node.requirement() != null) {
                GateRequirement req = node.requirement();
                String name = isBlank(node.label()) ? node.id() : node.label();
                if (req.minPercent() == null || req.minPercent() < 1 || req.minPercent() > 100) {
                    throw ApiException.badRequest("Punkte-Schwelle an Station „" + name + "“ muss zwischen 1 und 100 % liegen.");
                }
                if (req.mode() == GateMode.EACH) {
                    int inc = inCount.getOrDefault(node.id(), 0);
                    if (inc < 2) {
                        throw ApiException.badRequest("Modus EACH an Station „" + name + "“ erfordert mindestens 2 eingehende Verbindungen.");
                    }
                }
                if (req.scope() != null) {
                    Set<String> preds = allPredecessors.getOrDefault(node.id(), Set.of());
                    for (String scopeId : req.scope()) {
                        if (!ids.contains(scopeId)) {
                            throw ApiException.badRequest("Station „" + scopeId + "“ im Scope von „" + name + "“ existiert nicht.");
                        }
                        if (scopeId.equals(node.id())) {
                            throw ApiException.badRequest("Station „" + name + "“ kann nicht im eigenen Scope liegen.");
                        }
                        if (!preds.contains(scopeId)) {
                            throw ApiException.badRequest("Station „" + scopeId + "“ im Scope von „" + name + "“ liegt nicht vor dieser Station.");
                        }
                    }
                }
                if (req.fallback() == GateFallback.REMEDIAL) {
                    if (isBlank(req.fallbackRemedialNode())) {
                        throw ApiException.badRequest("Rückfall-Regel REMEDIAL an Station „" + name + "“ benötigt einen Stütz-Knoten.");
                    }
                    Node remNode = graph.nodes().stream()
                            .filter(n -> n.id().equals(req.fallbackRemedialNode()))
                            .findFirst()
                            .orElse(null);
                    if (remNode == null || remNode.kind() != NodeKind.REMEDIAL) {
                        throw ApiException.badRequest("Angegebener Stütz-Knoten „" + req.fallbackRemedialNode() + "“ an Station „" + name + "“ existiert nicht oder ist kein Stütz-Knoten.");
                    }
                }
            }
        }
    }

    /**
     * Topologische Reihenfolge (Kahn): Eine Station kommt erst nach allen, die auf sie zeigen. Bei Gleichstand
     * entscheidet die Lage auf der Karte – von oben nach unten, dann von links nach rechts. Bei einem Kreis
     * fehlen die Stationen des Kreises im Ergebnis.
     */
    static List<Node> order(Graph graph) {
        Map<String, Node> byId = new LinkedHashMap<>();
        graph.nodes().forEach(n -> byId.put(n.id(), n));
        Map<String, Integer> incoming = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        byId.keySet().forEach(id -> incoming.put(id, 0));
        for (Edge e : graph.edges()) {
            if (!byId.containsKey(e.from()) || !byId.containsKey(e.to())) continue;
            incoming.merge(e.to(), 1, Integer::sum);
            outgoing.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
        }
        Comparator<Node> position = Comparator.comparingInt(Node::y).thenComparingInt(Node::x).thenComparing(Node::id);
        PriorityQueue<Node> ready = new PriorityQueue<>(position);
        byId.values().stream().filter(n -> incoming.get(n.id()) == 0).forEach(ready::add);
        List<Node> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            Node node = ready.poll();
            result.add(node);
            for (String next : outgoing.getOrDefault(node.id(), List.of())) {
                if (incoming.merge(next, -1, Integer::sum) == 0) ready.add(byId.get(next));
            }
        }
        return result;
    }

    /** Vorgänger je Station */
    static Map<String, List<String>> predecessors(Graph graph) {
        Map<String, List<String>> result = new HashMap<>();
        for (Edge e : graph.edges()) result.computeIfAbsent(e.to(), k -> new ArrayList<>()).add(e.from());
        return result;
    }

    /** Pflicht-Vorgänger je Station (nur Verbindungen mit required = true) */
    static Map<String, List<String>> requiredPredecessors(Graph graph) {
        Map<String, List<String>> result = new HashMap<>();
        for (Edge e : graph.edges()) {
            if (e.required()) result.computeIfAbsent(e.to(), k -> new ArrayList<>()).add(e.from());
        }
        return result;
    }

    /** Alle mittelbaren und unmittelbaren Vorgänger je Station (inklusive der Station selbst). */
    static Map<String, Set<String>> ancestors(Graph graph) {
        Map<String, List<String>> preds = predecessors(graph);
        Map<String, Set<String>> result = new HashMap<>();
        for (Node node : order(graph)) {
            Set<String> set = new LinkedHashSet<>();
            set.add(node.id());
            for (String p : preds.getOrDefault(node.id(), List.of())) {
                set.addAll(result.getOrDefault(p, Set.of(p)));
            }
            result.put(node.id(), set);
        }
        return result;
    }

    /** Alle von einer Station aus erreichbaren Stationen (inklusive der Station selbst). */
    static Map<String, Set<String>> reachableFrom(Graph graph) {
        Map<String, List<String>> outgoing = new HashMap<>();
        for (Edge e : graph.edges()) {
            outgoing.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
        }
        Map<String, Set<String>> result = new HashMap<>();
        List<Node> ordered = order(graph);
        for (int i = ordered.size() - 1; i >= 0; i--) {
            String id = ordered.get(i).id();
            Set<String> set = new LinkedHashSet<>();
            set.add(id);
            for (String next : outgoing.getOrDefault(id, List.of())) {
                set.addAll(result.getOrDefault(next, Set.of(next)));
            }
            result.put(id, set);
        }
        return result;
    }

    /**
     * Exklusive Stationen je Weg einer Weiche: alle Stationen, die von diesem Weg aus erreichbar sind, aber
     * von keinem anderen Weg derselben Weiche. Stationen hinter der Zusammenführung gehören zu keinem Weg exklusiv.
     */
    static Map<String, Set<String>> choiceBranches(Graph graph, String choiceNodeId) {
        Map<String, Set<String>> reachable = reachableFrom(graph);
        List<String> targets = graph.edges().stream()
                .filter(e -> e.from().equals(choiceNodeId))
                .map(Edge::to)
                .toList();
        Map<String, Set<String>> branches = new LinkedHashMap<>();
        for (String target : targets) {
            Set<String> exclusive = new LinkedHashSet<>(reachable.getOrDefault(target, Set.of()));
            for (String other : targets) {
                if (!other.equals(target)) {
                    exclusive.removeAll(reachable.getOrDefault(other, Set.of()));
                }
            }
            branches.put(target, exclusive);
        }
        return branches;
    }

    /**
     * Welche Stationen durch die Wahl an einer Weiche ausgeschlossen sind: alle Stationen, die von nicht
     * gewählten/freigeschalteten Wegen erreichbar sind, aber von keinem der erlaubten Wege.
     */
    static Set<String> excludedByChoice(Graph graph, String choiceNodeId, Set<String> allowedTargets) {
        Map<String, Set<String>> reachable = reachableFrom(graph);
        List<String> targets = graph.edges().stream()
                .filter(e -> e.from().equals(choiceNodeId))
                .map(Edge::to)
                .toList();
        Set<String> allowedReachable = new HashSet<>();
        Set<String> unchosenReachable = new LinkedHashSet<>();
        for (String target : targets) {
            if (allowedTargets.contains(target)) {
                allowedReachable.addAll(reachable.getOrDefault(target, Set.of()));
            } else {
                unchosenReachable.addAll(reachable.getOrDefault(target, Set.of()));
            }
        }
        unchosenReachable.removeAll(allowedReachable);
        return unchosenReachable;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

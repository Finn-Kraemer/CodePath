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

    public enum NodeKind { MODULE, TASK, MILESTONE }

    /**
     * @param module Slug des Moduls (MODULE und TASK)
     * @param task   Slug der Aufgabe (nur TASK)
     * @param tasks  nur MODULE: Slugs der Aufgaben, die diese Station nutzt; leer oder null für alle – so lässt
     *               sich ein Modul auf mehrere Stationen verteilen (Programmierung 1, Web 1, Programmierung 2)
     * @param label  eigene Beschriftung; Pflicht für Meilensteine, sonst gilt der Titel von Modul bzw. Aufgabe
     */
    public record Node(String id, NodeKind kind, String module, String task, List<String> tasks, String label, int x, int y) {
        public Node {
            tasks = tasks == null || tasks.isEmpty() ? null : List.copyOf(new LinkedHashSet<>(tasks));
        }

        /** Nutzt die Station nur einen Teil der Aufgaben ihres Moduls? */
        public boolean partial() {
            return kind == NodeKind.MODULE && tasks != null;
        }
    }

    public record Edge(String from, String to) {
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
            }
        }
        Set<String> seen = new HashSet<>();
        for (Edge edge : graph.edges()) {
            if (edge == null || !ids.contains(edge.from()) || !ids.contains(edge.to())) {
                throw ApiException.badRequest("Eine Verbindung zeigt auf eine Station, die es nicht gibt.");
            }
            if (edge.from().equals(edge.to())) throw ApiException.badRequest("Eine Station kann nicht mit sich selbst verbunden sein.");
            if (!seen.add(edge.from() + "→" + edge.to())) throw ApiException.badRequest("Eine Verbindung kommt doppelt vor.");
        }
        if (order(graph).size() != graph.nodes().size()) {
            throw ApiException.badRequest("Die Verbindungen bilden einen Kreis – ein Lernpfad muss irgendwo anfangen und enden.");
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

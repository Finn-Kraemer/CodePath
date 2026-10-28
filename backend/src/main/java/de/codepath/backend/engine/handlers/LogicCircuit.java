package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.Cfg;

import java.util.*;

/**
 * Boolesche Ausdrücke und Gatterschaltungen. Wahrheitstabellen zählen binär hoch, der erste Eingang ist
 * das höchstwertige Bit: bei A, B die Zeilen 00, 01, 10, 11.
 */
final class LogicCircuit {

    static final int MAX_INPUTS = 4;
    static final int MAX_GATES = 20;
    static final List<String> GATES = List.of("AND", "OR", "NOT", "XOR", "NAND", "NOR", "XNOR");

    private LogicCircuit() {
    }

    static boolean apply(String gate, boolean a, boolean b) {
        return switch (gate) {
            case "AND" -> a && b;
            case "OR" -> a || b;
            case "NOT" -> !a;
            case "XOR" -> a ^ b;
            case "NAND" -> !(a && b);
            case "NOR" -> !(a || b);
            case "XNOR" -> a == b;
            default -> throw new IllegalArgumentException(gate);
        };
    }

    static int arity(String gate) {
        return gate.equals("NOT") ? 1 : 2;
    }

    static Map<String, Boolean> row(List<String> inputs, int index) {
        Map<String, Boolean> values = new HashMap<>();
        for (int i = 0; i < inputs.size(); i++) {
            values.put(inputs.get(i), ((index >> (inputs.size() - 1 - i)) & 1) == 1);
        }
        return values;
    }

    // Ausdrücke ---------------------------------------------------------------------------------

    /** Ausdruck als Baum; wirft IllegalArgumentException mit verständlicher Meldung. */
    sealed interface Expr permits Var, Const, Op {
        boolean eval(Map<String, Boolean> values);
    }

    record Var(String name) implements Expr {
        public boolean eval(Map<String, Boolean> values) {
            return values.get(name);
        }
    }

    record Const(boolean value) implements Expr {
        public boolean eval(Map<String, Boolean> values) {
            return value;
        }
    }

    record Op(String gate, Expr left, Expr right) implements Expr {
        public boolean eval(Map<String, Boolean> values) {
            return apply(gate, left.eval(values), right != null && right.eval(values));
        }
    }

    /**
     * Grammatik (Rangfolge NOT vor AND vor XOR vor OR):
     * <pre>or := xor (("OR"|"NOR"|"|"|"+"|"∨") xor)*;  xor := and (("XOR"|"XNOR"|"^"|"⊕") and)*;
     * and := not (("AND"|"NAND"|"&amp;"|"*"|"·"|"∧") not)*;  not := ("NOT"|"!"|"¬") not | "(" or ")" | Name | 0 | 1</pre>
     */
    static Expr parse(String text, Collection<String> inputs) {
        Parser parser = new Parser(tokenize(text), new HashSet<>(inputs));
        Expr expr = parser.or();
        if (parser.pos != parser.tokens.size()) throw new IllegalArgumentException("Unerwartet: '" + parser.tokens.get(parser.pos) + "'");
        return expr;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) throw new IllegalArgumentException("Ausdruck fehlt");
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isLetterOrDigit(c) || c == '_') {
                int start = i;
                while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) i++;
                tokens.add(text.substring(start, i));
            } else {
                tokens.add(String.valueOf(c));
                i++;
            }
        }
        if (tokens.isEmpty()) throw new IllegalArgumentException("Ausdruck fehlt");
        if (tokens.size() > 200) throw new IllegalArgumentException("Ausdruck zu lang");
        return tokens;
    }

    private static final class Parser {
        final List<String> tokens;
        final Set<String> inputs;
        int pos;
        int depth;

        Parser(List<String> tokens, Set<String> inputs) {
            this.tokens = tokens;
            this.inputs = inputs;
        }

        String peek() {
            return pos < tokens.size() ? tokens.get(pos).toUpperCase(Locale.ROOT) : "";
        }

        Expr or() {
            Expr left = xor();
            while (true) {
                String gate = switch (peek()) {
                    case "OR", "|", "+", "∨" -> "OR";
                    case "NOR" -> "NOR";
                    default -> null;
                };
                if (gate == null) return left;
                pos++;
                left = new Op(gate, left, xor());
            }
        }

        Expr xor() {
            Expr left = and();
            while (true) {
                String gate = switch (peek()) {
                    case "XOR", "^", "⊕" -> "XOR";
                    case "XNOR" -> "XNOR";
                    default -> null;
                };
                if (gate == null) return left;
                pos++;
                left = new Op(gate, left, and());
            }
        }

        Expr and() {
            Expr left = not();
            while (true) {
                String gate = switch (peek()) {
                    case "AND", "&", "*", "·", "∧" -> "AND";
                    case "NAND" -> "NAND";
                    default -> null;
                };
                if (gate == null) return left;
                pos++;
                left = new Op(gate, left, not());
            }
        }

        Expr not() {
            if (++depth > 50) throw new IllegalArgumentException("Ausdruck zu tief verschachtelt");
            try {
                String token = peek();
                if (token.isEmpty()) throw new IllegalArgumentException("Ausdruck endet zu früh");
                pos++;
                switch (token) {
                    case "NOT", "!", "¬":
                        return new Op("NOT", not(), null);
                    case "(":
                        Expr inner = or();
                        if (!peek().equals(")")) throw new IllegalArgumentException("Schließende Klammer fehlt");
                        pos++;
                        return inner;
                    case "0":
                        return new Const(false);
                    case "1":
                        return new Const(true);
                    default:
                        String name = tokens.get(pos - 1);
                        if (!inputs.contains(name)) throw new IllegalArgumentException("Unbekannter Eingang '" + name + "'");
                        return new Var(name);
                }
            } finally {
                depth--;
            }
        }
    }

    static List<Boolean> truthTable(Expr expr, List<String> inputs) {
        List<Boolean> table = new ArrayList<>();
        for (int i = 0; i < (1 << inputs.size()); i++) table.add(expr.eval(row(inputs, i)));
        return table;
    }

    // Schaltungen -------------------------------------------------------------------------------

    record Gate(String id, String type, List<String> inputs) {
    }

    record Circuit(List<Gate> gates, String output) {
    }

    /** Liest eine Schaltung aus Antwort oder Konfiguration; null-Felder werden zu Fehlern in {@link #check}. */
    static Circuit read(Map<String, Object> map) {
        List<Gate> gates = new ArrayList<>();
        for (Map<String, Object> g : Cfg.objects(map, "gates")) {
            gates.add(new Gate(Cfg.string(g, "id"), Cfg.string(g, "type"), Cfg.strings(g, "inputs")));
        }
        return new Circuit(gates, Cfg.string(map, "output"));
    }

    /** Leere Liste = gültig. Gatter dürfen nur Eingänge und weiter oben stehende Gatter verwenden – so gibt es keine Schleifen. */
    static List<String> check(Circuit circuit, List<String> inputs, Collection<String> allowed, Integer maxGates) {
        List<String> problems = new ArrayList<>();
        int limit = maxGates != null ? Math.min(maxGates, MAX_GATES) : MAX_GATES;
        if (circuit.gates().size() > limit) problems.add("Höchstens " + limit + " Gatter");
        Set<String> known = new HashSet<>(inputs);
        for (Gate gate : circuit.gates()) {
            String name = gate.id() == null ? "?" : gate.id();
            if (gate.id() == null || !gate.id().matches("[a-z][a-z0-9]{0,9}") || known.contains(gate.id())) {
                problems.add("Gatter '" + name + "' braucht einen eindeutigen Namen");
                continue;
            }
            if (gate.type() == null || !allowed.contains(gate.type())) {
                problems.add("Gatter " + name + ": Typ nicht erlaubt");
            } else if (gate.inputs().size() != arity(gate.type())) {
                problems.add("Gatter " + name + " braucht " + arity(gate.type()) + " Eingang/Eingänge");
            }
            for (String input : gate.inputs()) {
                if (!known.contains(input)) problems.add("Gatter " + name + ": Eingang '" + input + "' gibt es (noch) nicht");
            }
            known.add(gate.id());
        }
        if (circuit.output() == null || !known.contains(circuit.output())) problems.add("Der Ausgang ist mit nichts verbunden");
        return problems;
    }

    static List<Boolean> truthTable(Circuit circuit, List<String> inputs) {
        List<Boolean> table = new ArrayList<>();
        for (int i = 0; i < (1 << inputs.size()); i++) {
            Map<String, Boolean> values = row(inputs, i);
            for (Gate gate : circuit.gates()) {
                boolean a = values.get(gate.inputs().getFirst());
                boolean b = gate.inputs().size() > 1 && values.get(gate.inputs().get(1));
                values.put(gate.id(), apply(gate.type(), a, b));
            }
            table.add(values.get(circuit.output()));
        }
        return table;
    }

    static Map<String, Object> toMap(Circuit circuit) {
        return Map.of("gates", circuit.gates().stream().map(g -> Map.<String, Object>of("id", g.id(), "type", g.type(), "inputs", g.inputs())).toList(),
                "output", circuit.output());
    }
}

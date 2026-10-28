package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Config: { initialDirectories: [...], initialFiles: {pfad: inhalt}, git?: {root, history, head, remote},
 *           variants?: [{name: wert}], expectedDirectories: [...], expectedFiles: [...], expectedContents?: {pfad: inhalt},
 *           absent?: [...], allowExtra?: bool, answer?: "text" | ["text", ...], answerLabel?: "...",
 *           gitChecks?: [{check: "commits", min: 2}, ...], solution?: ["befehl", ...] }
 * Antwort: { commands: ["mkdir website", ...], answer?: "..." } – der Server spielt die Befehle selbst nach.
 * Platzhalter {{name}} in Dateien, Git-Verlauf, Prüfungen und Lösung werden je Person ersetzt: aus einer der
 * variants und {{code}} (vier Zeichen). So hat der Nachbar eine andere Antwort.
 */
@Component
public class TerminalHandler implements TaskHandler {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Override
    public TaskType type() {
        return TaskType.TERMINAL;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        Cfg.Problems problems = new Cfg.Problems()
                .require(!Cfg.strings(config, "expectedDirectories").isEmpty() || !Cfg.strings(config, "expectedFiles").isEmpty()
                        || !Cfg.object(config, "expectedContents").isEmpty() || !Cfg.strings(config, "absent").isEmpty()
                        || !answers(config).isEmpty() || !Cfg.list(config, "gitChecks").isEmpty(), "Ein erwarteter Endzustand fehlt");
        for (Map<String, Object> check : Cfg.objects(config, "gitChecks")) {
            problems.require(GitSimulator.CHECKS.contains(Cfg.string(check, "check", "")),
                    "Unbekannte Git-Prüfung \"" + Cfg.string(check, "check", "") + "\"; verfügbar: " + String.join(", ", new TreeSet<>(GitSimulator.CHECKS)));
        }
        boolean needsSolution = !answers(config).isEmpty() || !Cfg.list(config, "gitChecks").isEmpty()
                || !Cfg.strings(config, "absent").isEmpty() || !Cfg.object(config, "git").isEmpty();
        problems.require(!needsSolution || !Cfg.strings(config, "solution").isEmpty(),
                "Musterlösung fehlt: Befehle unter \"solution\" angeben (für Lösungsansicht und Probelauf)");
        problems.require(!config.containsKey("variants") || !Cfg.objects(config, "variants").isEmpty(),
                "variants muss eine Liste von Objekten sein");
        return problems.list();
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        Map<String, Object> cfg = resolved(spec.config(), context);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("initialDirectories", Cfg.strings(cfg, "initialDirectories"));
        view.put("initialFiles", initialFiles(cfg));
        if (!Cfg.object(cfg, "git").isEmpty()) view.put("git", Cfg.object(cfg, "git"));
        if (!answers(cfg).isEmpty()) view.put("answerLabel", Cfg.string(cfg, "answerLabel", "Deine Antwort"));
        return view;
    }

    private static Map<String, String> initialFiles(Map<String, Object> config) {
        Map<String, String> result = new TreeMap<>();
        Cfg.object(config, "initialFiles").forEach((k, v) -> result.put(k, v == null ? "" : String.valueOf(v)));
        return result;
    }

    static TerminalSimulator simulator(Map<String, Object> config) {
        return new TerminalSimulator(Cfg.strings(config, "initialDirectories"), initialFiles(config), Cfg.object(config, "git"));
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        Map<String, Object> cfg = resolved(spec.config(), context);
        List<String> commands = Cfg.strings(answer, "commands");
        List<String> answers = answers(cfg);
        String given = Cfg.string(answer, "answer");
        if (commands.isEmpty() && answers.isEmpty()) return Evaluation.invalid("Es wurden keine Befehle ausgeführt.");
        if (!answers.isEmpty() && (given == null || given.isBlank())) return Evaluation.invalid("Trag deine Antwort in das Antwortfeld ein.");
        if (commands.size() > TerminalSimulator.MAX_COMMANDS) return Evaluation.invalid("Zu viele Befehle.");

        TerminalSimulator sim = simulator(cfg);
        for (String command : commands) {
            if (command.length() > 500) return Evaluation.invalid("Ein Befehl ist zu lang.");
            sim.execute(command);
        }

        Set<String> expectedDirs = new TreeSet<>();
        Cfg.strings(cfg, "expectedDirectories").forEach(d -> expectedDirs.add(TerminalSimulator.normalize(d)));
        Set<String> expectedFiles = new TreeSet<>();
        Cfg.strings(cfg, "expectedFiles").forEach(f -> expectedFiles.add(TerminalSimulator.normalize(f)));
        Map<String, Object> expectedContents = Cfg.object(cfg, "expectedContents");

        int total = 0;
        List<String> missing = new ArrayList<>();
        for (String d : expectedDirs) {
            total++;
            if (!sim.directories().contains(d)) missing.add("Ordner " + d + " fehlt.");
        }
        for (String f : expectedFiles) {
            total++;
            if (!sim.files().containsKey(f)) missing.add("Datei " + f + " fehlt.");
        }
        for (var entry : expectedContents.entrySet()) {
            total++;
            String actual = sim.files().get(TerminalSimulator.normalize(entry.getKey()));
            if (actual == null || !actual.strip().equals(String.valueOf(entry.getValue()).strip())) {
                missing.add("Der Inhalt von " + entry.getKey() + " stimmt noch nicht.");
            }
        }
        for (String a : Cfg.strings(cfg, "absent")) {
            total++;
            String p = TerminalSimulator.normalize(a);
            if (sim.files().containsKey(p) || sim.directories().contains(p)) missing.add(a + " sollte nicht mehr da sein.");
        }
        if (!answers.isEmpty()) {
            total++;
            if (answers.stream().noneMatch(a -> normalizeAnswer(a).equals(normalizeAnswer(given)))) missing.add("Die Antwort stimmt noch nicht.");
        }
        for (Map<String, Object> check : Cfg.objects(cfg, "gitChecks")) {
            total++;
            String problem = sim.git().check(check);
            if (problem != null) missing.add(problem);
        }
        int met = total - missing.size();

        boolean checksStructure = !expectedDirs.isEmpty() || !expectedFiles.isEmpty() || !expectedContents.isEmpty();
        boolean extra = checksStructure && !Cfg.bool(cfg, "allowExtra")
                && (!expectedDirs.containsAll(withParents(sim.directories(), allExpectedPaths(expectedDirs, expectedFiles, expectedContents)))
                || !expectedFilesWithContents(expectedFiles, expectedContents).containsAll(sim.files().keySet()));
        if (extra) missing.add("Es gibt zusätzliche Dateien oder Ordner, die nicht verlangt sind.");

        if (missing.isEmpty()) return Evaluation.correct(checksStructure && answers.isEmpty() && Cfg.list(cfg, "gitChecks").isEmpty()
                ? "Richtig! Die Ordnerstruktur stimmt." : "Richtig – genau so arbeiten Profis im Terminal!");
        return Evaluation.wrong(met + " von " + total + " Anforderungen erfüllt. Noch offen: " + missing.getFirst());
    }

    private static List<String> answers(Map<String, Object> config) {
        Object value = config.get("answer");
        if (value instanceof String s) return s.isBlank() ? List.of() : List.of(s);
        return Cfg.strings(config, "answer").stream().filter(s -> !s.isBlank()).toList();
    }

    static String normalizeAnswer(String s) {
        String t = s.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return t.endsWith(".") ? t.substring(0, t.length() - 1).strip() : t;
    }

    /** Konfiguration mit ersetzten Platzhaltern für diese Person. */
    static Map<String, Object> resolved(Map<String, Object> config, TaskContext context) {
        Map<String, String> vars = new HashMap<>();
        StringBuilder code = new StringBuilder();
        Random random = context.random("terminal-code");
        for (int i = 0; i < 4; i++) code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        vars.put("code", code.toString());
        List<Map<String, Object>> variants = Cfg.objects(config, "variants");
        if (!variants.isEmpty()) {
            Map<String, Object> variant = variants.get(context.random("terminal-variant").nextInt(variants.size()));
            variant.forEach((k, v) -> vars.put(k, v == null ? "" : String.valueOf(v)));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) substitute(config, vars);
        result.remove("variants");
        return result;
    }

    private static Object substitute(Object value, Map<String, String> vars) {
        if (value instanceof String s) {
            if (!s.contains("{{")) return s;
            String r = s;
            for (var v : vars.entrySet()) r = r.replace("{{" + v.getKey() + "}}", v.getValue());
            return r;
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> result = new LinkedHashMap<>();
            m.forEach((k, v) -> result.put((String) substitute(String.valueOf(k), vars), substitute(v, vars)));
            return result;
        }
        if (value instanceof List<?> l) {
            List<Object> result = new ArrayList<>();
            for (Object item : l) result.add(substitute(item, vars));
            return result;
        }
        return value;
    }

    private static Set<String> allExpectedPaths(Set<String> dirs, Set<String> files, Map<String, Object> contents) {
        Set<String> all = new TreeSet<>(dirs);
        all.addAll(expectedFilesWithContents(files, contents));
        return all;
    }

    /** Übergeordnete Ordner erwarteter Pfade zählen nicht als "zusätzlich". */
    private static Set<String> withParents(Set<String> actual, Set<String> expected) {
        Set<String> result = new TreeSet<>(actual);
        for (String e : expected) {
            String p = e;
            while (p.contains("/")) {
                p = p.substring(0, p.lastIndexOf('/'));
                result.remove(p);
            }
        }
        return result;
    }

    private static Set<String> expectedFilesWithContents(Set<String> files, Map<String, Object> contents) {
        Set<String> result = new TreeSet<>(files);
        contents.keySet().forEach(k -> result.add(TerminalSimulator.normalize(k)));
        return result;
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        Map<String, Object> cfg = resolved(spec.config(), context);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("commands", solutionCommands(cfg));
        if (!answers(cfg).isEmpty()) view.put("answer", answers(cfg).getFirst());
        return view;
    }

    /** Die hinterlegte Musterlösung oder eine Befehlsfolge zum erwarteten Endzustand (für Lösungsansicht und Probelauf). */
    static List<String> solutionCommands(Map<String, Object> config) {
        List<String> given = Cfg.strings(config, "solution");
        if (!given.isEmpty()) return given;
        List<String> commands = new ArrayList<>();
        for (String d : Cfg.strings(config, "expectedDirectories")) commands.add("mkdir -p " + d);
        for (String f : Cfg.strings(config, "expectedFiles")) commands.add("touch " + f);
        Cfg.object(config, "expectedContents").forEach((path, content) ->
                commands.add("echo \"" + String.valueOf(content).replace("\"", "") + "\" > " + path));
        return commands;
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return solutionView(spec, context);
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        Map<String, Object> wrong = new LinkedHashMap<>();
        wrong.put("commands", List.of("mkdir falsch"));
        if (!answers(spec.config()).isEmpty()) wrong.put("answer", "falsch");
        return wrong;
    }

    @Override
    public Map<String, Object> template() {
        return Map.of(
                "initialDirectories", List.of(),
                "initialFiles", Map.of(),
                "expectedDirectories", List.of("projekt"),
                "expectedFiles", List.of("projekt/README.md"));
    }
}

package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Config: { initialDirectories: [...], initialFiles: {pfad: inhalt}, expectedDirectories: [...],
 *           expectedFiles: [...], expectedContents?: {pfad: inhalt}, allowExtra?: bool }
 * Antwort: { commands: ["mkdir website", ...] } – der Server spielt die Befehle selbst nach.
 */
@Component
public class TerminalHandler implements TaskHandler {

    @Override
    public TaskType type() {
        return TaskType.TERMINAL;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        return new Cfg.Problems()
                .require(!Cfg.strings(config, "expectedDirectories").isEmpty() || !Cfg.strings(config, "expectedFiles").isEmpty()
                        || !Cfg.object(config, "expectedContents").isEmpty(), "Ein erwarteter Endzustand fehlt")
                .list();
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        return Map.of(
                "initialDirectories", Cfg.strings(spec.config(), "initialDirectories"),
                "initialFiles", initialFiles(spec.config()));
    }

    private static Map<String, String> initialFiles(Map<String, Object> config) {
        Map<String, String> result = new TreeMap<>();
        Cfg.object(config, "initialFiles").forEach((k, v) -> result.put(k, v == null ? "" : String.valueOf(v)));
        return result;
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        List<String> commands = Cfg.strings(answer, "commands");
        if (commands.isEmpty()) return Evaluation.invalid("Es wurden keine Befehle ausgeführt.");
        if (commands.size() > TerminalSimulator.MAX_COMMANDS) return Evaluation.invalid("Zu viele Befehle.");

        TerminalSimulator sim = new TerminalSimulator(Cfg.strings(spec.config(), "initialDirectories"), initialFiles(spec.config()));
        for (String command : commands) {
            if (command.length() > 500) return Evaluation.invalid("Ein Befehl ist zu lang.");
            sim.execute(command);
        }

        Set<String> expectedDirs = new TreeSet<>();
        Cfg.strings(spec.config(), "expectedDirectories").forEach(d -> expectedDirs.add(TerminalSimulator.normalize(d)));
        Set<String> expectedFiles = new TreeSet<>();
        Cfg.strings(spec.config(), "expectedFiles").forEach(f -> expectedFiles.add(TerminalSimulator.normalize(f)));
        Map<String, Object> expectedContents = Cfg.object(spec.config(), "expectedContents");

        int total = expectedDirs.size() + expectedFiles.size() + expectedContents.size();
        int met = 0;
        for (String d : expectedDirs) if (sim.directories().contains(d)) met++;
        for (String f : expectedFiles) if (sim.files().containsKey(f)) met++;
        for (var entry : expectedContents.entrySet()) {
            String actual = sim.files().get(TerminalSimulator.normalize(entry.getKey()));
            if (actual != null && actual.strip().equals(String.valueOf(entry.getValue()).strip())) met++;
        }

        boolean extra = !Cfg.bool(spec.config(), "allowExtra")
                && (!expectedDirs.containsAll(withParents(sim.directories(), allExpectedPaths(expectedDirs, expectedFiles, expectedContents)))
                || !expectedFilesWithContents(expectedFiles, expectedContents).containsAll(sim.files().keySet()));

        if (met == total && !extra) return Evaluation.correct("Richtig! Die Ordnerstruktur stimmt.");
        String feedback = met + " von " + total + " Anforderungen erfüllt" + (extra ? "; es gibt zusätzliche Dateien oder Ordner, die nicht verlangt sind." : ".");
        return Evaluation.wrong(feedback);
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
        return Map.of("commands", solutionCommands(spec.config()));
    }

    /** Eine mögliche Befehlsfolge zum erwarteten Endzustand (für Lösungsansicht und Probelauf). */
    static List<String> solutionCommands(Map<String, Object> config) {
        List<String> commands = new ArrayList<>();
        for (String d : Cfg.strings(config, "expectedDirectories")) commands.add("mkdir -p " + d);
        for (String f : Cfg.strings(config, "expectedFiles")) commands.add("touch " + f);
        Cfg.object(config, "expectedContents").forEach((path, content) ->
                commands.add("echo \"" + String.valueOf(content).replace("\"", "") + "\" > " + path));
        return commands;
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("commands", solutionCommands(spec.config()));
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("commands", List.of("mkdir falsch"));
    }
}

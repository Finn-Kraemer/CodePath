package de.codepath.backend.engine.handlers;

import java.util.*;

/**
 * Kleines Dateisystem-Modell für Terminal-Aufgaben. Das Frontend hat ein gleichwertiges Modell für die
 * Anzeige; bewertet wird ausschließlich, was der Server aus der Befehlsfolge nachspielt.
 * Pfade werden ohne führendes "/" relativ zum Home-Verzeichnis gespeichert, z. B. "website/index.html".
 */
public class TerminalSimulator {

    public static final int MAX_COMMANDS = 200;

    private final SortedSet<String> directories = new TreeSet<>();
    private final SortedMap<String, String> files = new TreeMap<>();
    private final List<String> cwd = new ArrayList<>();

    public TerminalSimulator(Collection<String> initialDirectories, Map<String, String> initialFiles) {
        for (String dir : initialDirectories) mkdirs(normalize(dir));
        initialFiles.forEach((path, content) -> {
            String p = normalize(path);
            mkdirs(parent(p));
            files.put(p, content == null ? "" : content);
        });
    }

    public SortedSet<String> directories() {
        return Collections.unmodifiableSortedSet(directories);
    }

    public SortedMap<String, String> files() {
        return Collections.unmodifiableSortedMap(files);
    }

    /** Führt einen Befehl aus und gibt die Ausgabe zurück; Fehler beginnen mit dem Befehlsnamen. */
    public String execute(String line) {
        List<String> args = tokenize(line);
        if (args.isEmpty()) return "";
        String cmd = args.getFirst();
        List<String> rest = args.subList(1, args.size());

        int redirect = rest.indexOf(">") >= 0 ? rest.indexOf(">") : rest.indexOf(">>");
        if (cmd.equals("echo") && redirect >= 0 && redirect + 1 < rest.size()) {
            boolean append = rest.get(redirect).equals(">>");
            String text = String.join(" ", rest.subList(0, redirect));
            return writeFile(rest.get(redirect + 1), text + "\n", append);
        }

        return switch (cmd) {
            case "pwd" -> "~" + (cwd.isEmpty() ? "" : "/" + String.join("/", cwd));
            case "ls" -> ls(rest.isEmpty() ? "." : rest.getFirst());
            case "cd" -> cd(rest.isEmpty() ? "~" : rest.getFirst());
            case "mkdir" -> mkdir(rest);
            case "touch" -> touch(rest);
            case "rm" -> rm(rest);
            case "cat" -> cat(rest);
            case "echo" -> String.join(" ", rest);
            case "mv" -> move(rest, true);
            case "cp" -> move(rest, false);
            case "clear", "help", "submit" -> "";
            default -> cmd + ": Befehl nicht gefunden";
        };
    }

    private String ls(String target) {
        String dir = resolve(target);
        if (!isDirectory(dir)) return "ls: " + target + ": nicht gefunden";
        String prefix = dir.isEmpty() ? "" : dir + "/";
        SortedSet<String> entries = new TreeSet<>();
        for (String d : directories) if (isChild(prefix, d)) entries.add(d.substring(prefix.length()) + "/");
        for (String f : files.keySet()) if (isChild(prefix, f)) entries.add(f.substring(prefix.length()));
        return String.join("  ", entries);
    }

    private String cd(String target) {
        String dir = resolve(target);
        if (!isDirectory(dir)) return "cd: " + target + ": Verzeichnis nicht gefunden";
        cwd.clear();
        if (!dir.isEmpty()) cwd.addAll(List.of(dir.split("/")));
        return "";
    }

    private String mkdir(List<String> args) {
        boolean parents = args.contains("-p");
        StringBuilder out = new StringBuilder();
        for (String arg : args) {
            if (arg.startsWith("-")) continue;
            String path = resolve(arg);
            if (path.isEmpty() || exists(path)) {
                if (!parents) out.append("mkdir: ").append(arg).append(": existiert bereits\n");
            } else if (!parents && !isDirectory(parent(path))) {
                out.append("mkdir: ").append(arg).append(": übergeordneter Ordner fehlt\n");
            } else {
                mkdirs(path);
            }
        }
        return out.toString().strip();
    }

    private String touch(List<String> args) {
        StringBuilder out = new StringBuilder();
        for (String arg : args) {
            String path = resolve(arg);
            if (isDirectory(path)) continue;
            if (!isDirectory(parent(path))) {
                out.append("touch: ").append(arg).append(": Ordner fehlt\n");
            } else {
                files.putIfAbsent(path, "");
            }
        }
        return out.toString().strip();
    }

    private String rm(List<String> args) {
        boolean recursive = args.stream().anyMatch(a -> a.startsWith("-") && a.contains("r"));
        StringBuilder out = new StringBuilder();
        for (String arg : args) {
            if (arg.startsWith("-")) continue;
            String path = resolve(arg);
            if (files.remove(path) != null) continue;
            if (isDirectory(path) && !path.isEmpty()) {
                if (!recursive) {
                    out.append("rm: ").append(arg).append(": ist ein Ordner (rm -r verwenden)\n");
                    continue;
                }
                String prefix = path + "/";
                directories.removeIf(d -> d.equals(path) || d.startsWith(prefix));
                files.keySet().removeIf(f -> f.startsWith(prefix));
            } else {
                out.append("rm: ").append(arg).append(": nicht gefunden\n");
            }
        }
        return out.toString().strip();
    }

    private String cat(List<String> args) {
        StringBuilder out = new StringBuilder();
        for (String arg : args) {
            String content = files.get(resolve(arg));
            out.append(content != null ? content : "cat: " + arg + ": nicht gefunden\n");
        }
        return out.toString().stripTrailing();
    }

    private String writeFile(String target, String text, boolean append) {
        String path = resolve(target);
        if (isDirectory(path)) return "echo: " + target + ": ist ein Ordner";
        if (!isDirectory(parent(path))) return "echo: " + target + ": Ordner fehlt";
        files.merge(path, text, (old, add) -> append ? old + add : add);
        return "";
    }

    private String move(List<String> args, boolean removeSource) {
        String name = removeSource ? "mv" : "cp";
        List<String> paths = args.stream().filter(a -> !a.startsWith("-")).toList();
        if (paths.size() != 2) return name + ": Quelle und Ziel angeben";
        String source = resolve(paths.get(0));
        String target = resolve(paths.get(1));
        if (isDirectory(target)) target = (target.isEmpty() ? "" : target + "/") + last(source);
        if (files.containsKey(source)) {
            if (!isDirectory(parent(target))) return name + ": Zielordner fehlt";
            files.put(target, files.get(source));
            if (removeSource) files.remove(source);
            return "";
        }
        if (isDirectory(source) && !source.isEmpty()) {
            if (!removeSource && !args.contains("-r")) return "cp: " + paths.get(0) + ": ist ein Ordner (cp -r verwenden)";
            if (target.equals(source) || target.startsWith(source + "/")) return name + ": Ziel liegt in der Quelle";
            String from = source + "/";
            String to = target + "/";
            for (String d : new ArrayList<>(directories)) {
                if (d.equals(source) || d.startsWith(from)) directories.add(target + d.substring(source.length()));
            }
            for (String f : new ArrayList<>(files.keySet())) {
                if (f.startsWith(from)) files.put(to + f.substring(from.length()), files.get(f));
            }
            if (removeSource) {
                directories.removeIf(d -> d.equals(source) || d.startsWith(from));
                files.keySet().removeIf(f -> f.startsWith(from));
            }
            return "";
        }
        return name + ": " + paths.get(0) + ": nicht gefunden";
    }

    private void mkdirs(String path) {
        if (path.isEmpty()) return;
        StringBuilder current = new StringBuilder();
        for (String part : path.split("/")) {
            if (!current.isEmpty()) current.append('/');
            current.append(part);
            directories.add(current.toString());
        }
    }

    private boolean exists(String path) {
        return isDirectory(path) || files.containsKey(path);
    }

    private boolean isDirectory(String path) {
        return path.isEmpty() || directories.contains(path);
    }

    private static boolean isChild(String prefix, String path) {
        return path.startsWith(prefix) && !path.equals(prefix) && path.indexOf('/', prefix.length()) < 0;
    }

    private String resolve(String target) {
        List<String> parts = new ArrayList<>();
        String t = target;
        if (t.equals("~") || t.startsWith("~/") || t.startsWith("/")) {
            t = t.replaceFirst("^~?/?", "");
        } else {
            parts.addAll(cwd);
        }
        for (String part : t.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!parts.isEmpty()) parts.removeLast();
            } else {
                parts.add(part);
            }
        }
        return String.join("/", parts);
    }

    static String normalize(String path) {
        return String.join("/", Arrays.stream(path.split("/")).filter(p -> !p.isEmpty() && !p.equals(".")).toList());
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static String last(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /** Zerlegt eine Zeile in Wörter; Anführungszeichen halten Leerzeichen zusammen. */
    static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Character quote = null;
        boolean inToken = false;
        for (char c : line.strip().toCharArray()) {
            if (quote != null) {
                if (c == quote) quote = null;
                else current.append(c);
            } else if (c == '"' || c == '\'') {
                quote = c;
                inToken = true;
            } else if (Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
            } else {
                current.append(c);
                inToken = true;
            }
        }
        if (inToken) tokens.add(current.toString());
        return tokens;
    }
}

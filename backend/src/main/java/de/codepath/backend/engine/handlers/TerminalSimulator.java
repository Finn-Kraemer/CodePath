package de.codepath.backend.engine.handlers;

import java.util.*;

/**
 * Kleines Dateisystem-Modell mit Shell für Terminal-Aufgaben. Das Frontend hat ein gleichwertiges Modell für
 * die Anzeige (frontend/src/lib/tasks/terminal.ts); bewertet wird ausschließlich, was der Server aus der
 * Befehlsfolge nachspielt. Beide Modelle prüfen dieselben Fälle (frontend/src/lib/tasks/terminal-cases.json).
 * Pfade werden ohne führendes "/" relativ zum Home-Verzeichnis gespeichert, z. B. "website/index.html".
 */
public class TerminalSimulator {

    public static final int MAX_COMMANDS = 200;
    static final int MAX_FILES = 500;
    static final int MAX_FILE_SIZE = 20_000;
    static final int MAX_OUTPUT = 50_000;

    private final SortedSet<String> directories = new TreeSet<>();
    private final SortedMap<String, String> files = new TreeMap<>();
    private final List<String> cwd = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private final GitSimulator git = new GitSimulator(this);

    public TerminalSimulator(Collection<String> initialDirectories, Map<String, String> initialFiles) {
        for (String dir : initialDirectories) mkdirs(normalize(dir));
        initialFiles.forEach((path, content) -> {
            String p = normalize(path);
            mkdirs(parent(p));
            files.put(p, content == null ? "" : content);
        });
    }

    /** Wie oben, zusätzlich mit vorbereitetem Git-Repository (Config "git"). */
    public TerminalSimulator(Collection<String> initialDirectories, Map<String, String> initialFiles, Map<String, Object> gitSetup) {
        this(initialDirectories, initialFiles);
        if (gitSetup != null && !gitSetup.isEmpty()) git.setup(gitSetup);
    }

    public SortedSet<String> directories() {
        return Collections.unmodifiableSortedSet(directories);
    }

    public SortedMap<String, String> files() {
        return Collections.unmodifiableSortedMap(files);
    }

    public GitSimulator git() {
        return git;
    }

    public String prompt() {
        return "~" + (cwd.isEmpty() ? "" : "/" + String.join("/", cwd));
    }

    /** Ergebnis eines Befehls: Ausgabe (geht in Pipe oder Datei) und Fehlermeldungen (immer auf den Bildschirm). */
    record Out(String out, String err) {
        static Out of(String out) {
            return new Out(out, "");
        }

        static Out error(String err) {
            return new Out("", err);
        }
    }

    record Token(String text, boolean op) {
    }

    /** Führt eine Befehlszeile aus (mit | und > / >>) und gibt die Bildschirmausgabe zurück. */
    public String execute(String line) {
        history.add(line);
        List<Token> tokens = lex(line);
        if (tokens.isEmpty()) return "";

        List<List<String>> segments = new ArrayList<>();
        List<String> current = new ArrayList<>();
        String target = null;
        boolean append = false;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (!t.op()) {
                current.add(t.text());
            } else if (t.text().equals("|")) {
                if (current.isEmpty()) return "Syntaxfehler: unerwartetes |";
                segments.add(current);
                current = new ArrayList<>();
            } else {
                if (i + 1 >= tokens.size() || tokens.get(i + 1).op()) return "Syntaxfehler: Dateiname nach " + t.text() + " fehlt";
                if (i + 2 < tokens.size()) return "Syntaxfehler: " + t.text() + " gehört ans Ende der Zeile";
                target = tokens.get(i + 1).text();
                append = t.text().equals(">>");
                break;
            }
        }
        if (current.isEmpty()) return "Syntaxfehler: Befehl fehlt";
        segments.add(current);

        String stdin = null;
        List<String> errors = new ArrayList<>();
        for (List<String> segment : segments) {
            Out result = run(segment, stdin);
            if (!result.err().isEmpty()) errors.add(result.err());
            stdin = result.out();
        }
        String out = stdin;
        if (target != null) {
            String error = writeFile(target, out.isEmpty() ? "" : out + "\n", append);
            if (!error.isEmpty()) errors.add(error);
            out = "";
        }
        List<String> screen = new ArrayList<>();
        for (String e : errors) screen.add(trimEnd(e));
        if (!out.isEmpty()) screen.add(out);
        return String.join("\n", screen);
    }

    private Out run(List<String> args, String stdin) {
        String cmd = args.getFirst();
        List<String> rest = args.subList(1, args.size());
        return switch (cmd) {
            case "pwd" -> Out.of(prompt());
            case "ls" -> ls(rest);
            case "cd" -> cd(rest.isEmpty() ? "~" : rest.getFirst());
            case "mkdir" -> mkdir(rest);
            case "touch" -> touch(rest);
            case "rm" -> rm(rest);
            case "cat" -> cat(rest, stdin);
            case "echo" -> Out.of(String.join(" ", rest));
            case "mv" -> move(rest, true);
            case "cp" -> move(rest, false);
            case "head" -> headTail(rest, stdin, true);
            case "tail" -> headTail(rest, stdin, false);
            case "grep" -> grep(rest, stdin);
            case "wc" -> wc(rest, stdin);
            case "sort" -> sort(rest, stdin);
            case "uniq" -> uniq(rest, stdin);
            case "find" -> find(rest);
            case "history" -> history();
            case "help" -> Out.of(TerminalHelp.HELP);
            case "man" -> man(rest);
            case "git" -> git.run(rest);
            case "clear", "submit" -> Out.of("");
            default -> Out.error(cmd + ": Befehl nicht gefunden – \"help\" zeigt alle Befehle");
        };
    }

    private Out ls(List<String> args) {
        boolean all = args.stream().anyMatch(a -> a.startsWith("-") && a.contains("a"));
        List<String> targets = args.stream().filter(a -> !a.startsWith("-")).toList();
        String target = targets.isEmpty() ? "." : targets.getFirst();
        String dir = resolve(target);
        if (files.containsKey(dir)) return Out.of(last(dir));
        if (!isDirectory(dir)) return Out.error("ls: " + target + ": nicht gefunden");
        String prefix = dir.isEmpty() ? "" : dir + "/";
        SortedSet<String> entries = new TreeSet<>();
        for (String d : directories) if (isChild(prefix, d)) entries.add(d.substring(prefix.length()) + "/");
        for (String f : files.keySet()) if (isChild(prefix, f)) entries.add(f.substring(prefix.length()));
        if (all && dir.equals(git.root())) entries.add(".git/");
        if (!all) entries.removeIf(e -> e.startsWith("."));
        return Out.of(String.join("  ", entries));
    }

    private Out cd(String target) {
        String dir = resolve(target);
        if (!isDirectory(dir)) return Out.error("cd: " + target + ": Verzeichnis nicht gefunden");
        cwd.clear();
        if (!dir.isEmpty()) cwd.addAll(List.of(dir.split("/")));
        return Out.of("");
    }

    private Out mkdir(List<String> args) {
        boolean parents = args.contains("-p");
        StringBuilder err = new StringBuilder();
        for (String arg : args) {
            if (arg.startsWith("-")) continue;
            String path = resolve(arg);
            if (path.isEmpty() || exists(path)) {
                if (!parents) err.append("mkdir: ").append(arg).append(": existiert bereits\n");
            } else if (!parents && !isDirectory(parent(path))) {
                err.append("mkdir: ").append(arg).append(": übergeordneter Ordner fehlt\n");
            } else {
                mkdirs(path);
            }
        }
        return Out.error(err.toString());
    }

    private Out touch(List<String> args) {
        StringBuilder err = new StringBuilder();
        for (String arg : args) {
            String path = resolve(arg);
            if (isDirectory(path) || files.containsKey(path)) continue;
            if (!isDirectory(parent(path))) {
                err.append("touch: ").append(arg).append(": Ordner fehlt\n");
            } else if (files.size() >= MAX_FILES) {
                err.append("touch: zu viele Dateien\n");
            } else {
                files.put(path, "");
            }
        }
        return Out.error(err.toString());
    }

    private Out rm(List<String> args) {
        boolean recursive = args.stream().anyMatch(a -> a.startsWith("-") && a.contains("r"));
        StringBuilder err = new StringBuilder();
        for (String arg : args) {
            if (arg.startsWith("-")) continue;
            String path = resolve(arg);
            if (files.remove(path) != null) continue;
            if (isDirectory(path) && !path.isEmpty()) {
                if (!recursive) {
                    err.append("rm: ").append(arg).append(": ist ein Ordner (rm -r verwenden)\n");
                    continue;
                }
                String prefix = path + "/";
                directories.removeIf(d -> d.equals(path) || d.startsWith(prefix));
                files.keySet().removeIf(f -> f.startsWith(prefix));
            } else {
                err.append("rm: ").append(arg).append(": nicht gefunden\n");
            }
        }
        return Out.error(err.toString());
    }

    private Out cat(List<String> args, String stdin) {
        if (args.isEmpty()) return Out.of(stdin != null ? stdin : "");
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        for (String arg : args) {
            String path = resolve(arg);
            String content = files.get(path);
            if (content != null) {
                if (out.length() + content.length() > MAX_OUTPUT) {
                    err.append("cat: Ausgabe zu lang\n");
                    break;
                }
                out.append(content);
            } else if (isDirectory(path)) {
                err.append("cat: ").append(arg).append(": ist ein Ordner\n");
            } else {
                err.append("cat: ").append(arg).append(": nicht gefunden\n");
            }
        }
        return new Out(trimEnd(out.toString()), err.toString());
    }

    /** Text aus Dateien oder – ohne Dateien – aus der Pipe. */
    private record Input(List<String[]> sources, String err) {
    }

    private Input input(String name, List<String> paths, String stdin) {
        List<String[]> sources = new ArrayList<>();
        StringBuilder err = new StringBuilder();
        if (paths.isEmpty()) {
            if (stdin == null) err.append(name).append(": Datei angeben\n");
            else sources.add(new String[]{"", stdin});
            return new Input(sources, err.toString());
        }
        for (String arg : paths) {
            String path = resolve(arg);
            String content = files.get(path);
            if (content != null) sources.add(new String[]{arg, content});
            else if (isDirectory(path)) err.append(name).append(": ").append(arg).append(": ist ein Ordner\n");
            else err.append(name).append(": ").append(arg).append(": nicht gefunden\n");
        }
        return new Input(sources, err.toString());
    }

    private Out headTail(List<String> args, String stdin, boolean head) {
        String name = head ? "head" : "tail";
        int count = 10;
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("-n") && i + 1 < args.size()) {
                Integer n = parseCount(args.get(++i));
                if (n == null) return Out.error(name + ": ungültige Zeilenzahl: " + args.get(i));
                count = n;
            } else if (a.startsWith("-n") && a.length() > 2) {
                Integer n = parseCount(a.substring(2));
                if (n == null) return Out.error(name + ": ungültige Zeilenzahl: " + a.substring(2));
                count = n;
            } else if (a.startsWith("-") && a.length() > 1) {
                Integer n = parseCount(a.substring(1));
                if (n == null) return Out.error(name + ": unbekannte Option " + a);
                count = n;
            } else {
                paths.add(a);
            }
        }
        Input in = input(name, paths, stdin);
        List<String> all = new ArrayList<>();
        for (String[] source : in.sources()) all.addAll(lines(source[1]));
        List<String> picked = head ? all.subList(0, Math.min(count, all.size()))
                : all.subList(Math.max(0, all.size() - count), all.size());
        return new Out(String.join("\n", picked), in.err());
    }

    private static Integer parseCount(String s) {
        if (s.isEmpty() || s.length() > 6) return null;
        for (char c : s.toCharArray()) if (c < '0' || c > '9') return null;
        return Integer.parseInt(s);
    }

    private Out grep(List<String> args, String stdin) {
        boolean ignoreCase = false, numbers = false, count = false, invert = false, recursive = false;
        String pattern = null;
        List<String> paths = new ArrayList<>();
        for (String a : args) {
            if (pattern == null && a.startsWith("-") && a.length() > 1) {
                for (char c : a.substring(1).toCharArray()) {
                    switch (c) {
                        case 'i' -> ignoreCase = true;
                        case 'n' -> numbers = true;
                        case 'c' -> count = true;
                        case 'v' -> invert = true;
                        case 'r', 'R' -> recursive = true;
                        default -> {
                            return Out.error("grep: unbekannte Option -" + c);
                        }
                    }
                }
            } else if (pattern == null) {
                pattern = a;
            } else {
                paths.add(a);
            }
        }
        if (pattern == null) return Out.error("grep: Suchtext fehlt – z. B. grep ERROR server.log");

        List<String[]> sources = new ArrayList<>();
        StringBuilder err = new StringBuilder();
        boolean showNames;
        if (recursive) {
            for (String arg : paths.isEmpty() ? List.of(".") : paths) {
                String path = resolve(arg);
                if (files.containsKey(path)) {
                    sources.add(new String[]{arg, files.get(path)});
                } else if (isDirectory(path)) {
                    String prefix = path.isEmpty() ? "" : path + "/";
                    String shown = stripSlash(arg);
                    for (var f : files.entrySet()) {
                        if (f.getKey().startsWith(prefix)) sources.add(new String[]{shown + "/" + f.getKey().substring(prefix.length()), f.getValue()});
                    }
                } else {
                    err.append("grep: ").append(arg).append(": nicht gefunden\n");
                }
            }
            showNames = true;
        } else {
            Input in = input("grep", paths, stdin);
            sources.addAll(in.sources());
            err.append(in.err());
            showNames = paths.size() > 1;
        }

        String needle = ignoreCase ? pattern.toLowerCase(Locale.ROOT) : pattern;
        List<String> out = new ArrayList<>();
        int size = 0;
        for (String[] source : sources) {
            String prefix = showNames ? source[0] + ":" : "";
            List<String> sourceLines = lines(source[1]);
            int hits = 0;
            for (int i = 0; i < sourceLines.size(); i++) {
                String l = sourceLines.get(i);
                boolean match = (ignoreCase ? l.toLowerCase(Locale.ROOT) : l).contains(needle) != invert;
                if (!match) continue;
                hits++;
                if (count) continue;
                String hit = prefix + (numbers ? (i + 1) + ":" : "") + l;
                size += hit.length() + 1;
                if (size > MAX_OUTPUT) return new Out(String.join("\n", out), err + "grep: Ausgabe zu lang\n");
                out.add(hit);
            }
            if (count) out.add(prefix + hits);
        }
        return new Out(String.join("\n", out), err.toString());
    }

    private Out wc(List<String> args, String stdin) {
        boolean l = false, w = false, c = false;
        List<String> paths = new ArrayList<>();
        for (String a : args) {
            if (a.startsWith("-") && a.length() > 1) {
                for (char ch : a.substring(1).toCharArray()) {
                    switch (ch) {
                        case 'l' -> l = true;
                        case 'w' -> w = true;
                        case 'c', 'm' -> c = true;
                        default -> {
                            return Out.error("wc: unbekannte Option -" + ch);
                        }
                    }
                }
            } else {
                paths.add(a);
            }
        }
        if (!l && !w && !c) l = w = c = true;
        Input in = input("wc", paths, stdin);
        List<String> out = new ArrayList<>();
        for (String[] source : in.sources()) {
            List<String> parts = new ArrayList<>();
            if (l) parts.add(String.valueOf(lines(source[1]).size()));
            if (w) parts.add(String.valueOf(words(source[1])));
            if (c) parts.add(String.valueOf(source[1].length()));
            if (!source[0].isEmpty()) parts.add(source[0]);
            out.add(String.join(" ", parts));
        }
        return new Out(String.join("\n", out), in.err());
    }

    private Out sort(List<String> args, String stdin) {
        boolean reverse = args.contains("-r");
        Input in = input("sort", args.stream().filter(a -> !a.startsWith("-")).toList(), stdin);
        List<String> all = new ArrayList<>();
        for (String[] source : in.sources()) all.addAll(lines(source[1]));
        Collections.sort(all);
        if (reverse) Collections.reverse(all);
        return new Out(String.join("\n", all), in.err());
    }

    private Out uniq(List<String> args, String stdin) {
        boolean count = args.contains("-c");
        Input in = input("uniq", args.stream().filter(a -> !a.startsWith("-")).toList(), stdin);
        List<String> all = new ArrayList<>();
        for (String[] source : in.sources()) all.addAll(lines(source[1]));
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < all.size()) {
            int j = i;
            while (j < all.size() && all.get(j).equals(all.get(i))) j++;
            out.add(count ? (j - i) + " " + all.get(i) : all.get(i));
            i = j;
        }
        return new Out(String.join("\n", out), in.err());
    }

    private Out find(List<String> args) {
        String start = ".";
        String name = null;
        String type = null;
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("-name")) {
                if (i + 1 >= args.size()) return Out.error("find: Muster nach " + a + " fehlt");
                name = args.get(++i);
            } else if (a.equals("-type")) {
                if (i + 1 >= args.size() || !List.of("f", "d").contains(args.get(i + 1))) return Out.error("find: -type f (Dateien) oder -type d (Ordner)");
                type = args.get(++i);
            } else if (a.startsWith("-")) {
                return Out.error("find: unbekannte Option " + a);
            } else if (i == 0) {
                start = a;
            } else {
                return Out.error("find: Startordner gehört an den Anfang");
            }
        }
        String dir = resolve(start);
        if (!isDirectory(dir)) return Out.error("find: " + start + ": Ordner nicht gefunden");
        String shown = stripSlash(start);
        String prefix = dir.isEmpty() ? "" : dir + "/";
        List<String> out = new ArrayList<>();
        if (!"f".equals(type) && (name == null || glob(name, last(shown)))) out.add(shown);
        if (!"f".equals(type)) {
            for (String d : directories) {
                if (d.startsWith(prefix) && (name == null || glob(name, last(d)))) out.add(shown + "/" + d.substring(prefix.length()));
            }
        }
        if (!"d".equals(type)) {
            for (String f : files.keySet()) {
                if (f.startsWith(prefix) && (name == null || glob(name, last(f)))) out.add(shown + "/" + f.substring(prefix.length()));
            }
        }
        Collections.sort(out);
        return Out.of(String.join("\n", out));
    }

    private Out history() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) out.add(pad(String.valueOf(i + 1), 4) + "  " + history.get(i));
        return Out.of(String.join("\n", out));
    }

    private Out man(List<String> args) {
        if (args.isEmpty()) return Out.error("man: Welcher Befehl? Zum Beispiel: man ls");
        String page = TerminalHelp.MAN.get(args.getFirst());
        return page != null ? Out.of(page) : Out.error("man: Keine Hilfe zu " + args.getFirst());
    }

    // --- Dateisystem-Zugriff (auch für GitSimulator) ---

    String writeFile(String target, String text, boolean append) {
        String path = resolve(target);
        if (path.isEmpty() || isDirectory(path)) return target + ": ist ein Ordner";
        if (!isDirectory(parent(path))) return target + ": Ordner fehlt";
        String content = append && files.containsKey(path) ? files.get(path) + text : text;
        if (content.length() > MAX_FILE_SIZE) return target + ": Datei zu groß";
        if (!files.containsKey(path) && files.size() >= MAX_FILES) return target + ": zu viele Dateien";
        files.put(path, content);
        return "";
    }

    /** Schreibt eine Datei ohne Prüfungen, legt fehlende Ordner an (für Git). */
    void putFile(String path, String content) {
        mkdirs(parent(path));
        files.put(path, content);
    }

    /** Entfernt eine Datei und danach leer gewordene Ordner bis hinauf zu stopAt (für Git). */
    void deleteFile(String path, String stopAt) {
        files.remove(path);
        String dir = parent(path);
        while (!dir.isEmpty() && !dir.equals(stopAt) && isEmptyDirectory(dir)) {
            directories.remove(dir);
            dir = parent(dir);
        }
    }

    private boolean isEmptyDirectory(String dir) {
        String prefix = dir + "/";
        return directories.stream().noneMatch(d -> d.startsWith(prefix)) && files.keySet().stream().noneMatch(f -> f.startsWith(prefix));
    }

    String readFile(String path) {
        return files.get(path);
    }

    String cwdPath() {
        return String.join("/", cwd);
    }

    private Out move(List<String> args, boolean removeSource) {
        String name = removeSource ? "mv" : "cp";
        List<String> paths = args.stream().filter(a -> !a.startsWith("-")).toList();
        if (paths.size() != 2) return Out.error(name + ": Quelle und Ziel angeben");
        String source = resolve(paths.get(0));
        String target = resolve(paths.get(1));
        if (isDirectory(target)) target = (target.isEmpty() ? "" : target + "/") + last(source);
        if (files.containsKey(source)) {
            if (!isDirectory(parent(target))) return Out.error(name + ": Zielordner fehlt");
            if (isDirectory(target)) return Out.error(name + ": " + paths.get(1) + ": ist ein Ordner");
            if (!files.containsKey(target) && !removeSource && files.size() >= MAX_FILES) return Out.error(name + ": zu viele Dateien");
            files.put(target, files.get(source));
            if (removeSource && !target.equals(source)) files.remove(source);
            return Out.of("");
        }
        if (isDirectory(source) && !source.isEmpty()) {
            if (!removeSource && !args.contains("-r")) return Out.error("cp: " + paths.get(0) + ": ist ein Ordner (cp -r verwenden)");
            if (target.equals(source) || target.startsWith(source + "/")) return Out.error(name + ": Ziel liegt in der Quelle");
            if (files.containsKey(target)) return Out.error(name + ": " + paths.get(1) + ": ist eine Datei");
            String from = source + "/";
            String to = target + "/";
            long copied = files.keySet().stream().filter(f -> f.startsWith(from)).count();
            if (!removeSource && files.size() + copied > MAX_FILES) return Out.error("cp: zu viele Dateien");
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
            return Out.of("");
        }
        return Out.error(name + ": " + paths.get(0) + ": nicht gefunden");
    }

    void mkdirs(String path) {
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

    boolean isDirectory(String path) {
        return path.isEmpty() || directories.contains(path);
    }

    private static boolean isChild(String prefix, String path) {
        return path.startsWith(prefix) && !path.equals(prefix) && path.indexOf('/', prefix.length()) < 0;
    }

    String resolve(String target) {
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

    static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    static String last(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String stripSlash(String path) {
        String p = path;
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    /** Zeilen eines Texts; ein abschließender Zeilenumbruch erzeugt keine leere Zeile. */
    static List<String> lines(String text) {
        if (text.isEmpty()) return new ArrayList<>();
        String t = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        return new ArrayList<>(Arrays.asList(t.split("\n", -1)));
    }

    private static int words(String text) {
        int count = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            boolean space = isSpace(text.charAt(i));
            if (!space && !inWord) count++;
            inWord = !space;
        }
        return count;
    }

    static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    static String trimEnd(String s) {
        int end = s.length();
        while (end > 0 && isSpace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }

    static String pad(String s, int width) {
        return s.length() >= width ? s : " ".repeat(width - s.length()) + s;
    }

    /** Platzhalter * (beliebig viele Zeichen außer /) und ? (genau ein Zeichen außer /). */
    static boolean glob(String pattern, String text) {
        String[] ps = pattern.split("/", -1);
        String[] ts = text.split("/", -1);
        if (ps.length != ts.length) return false;
        for (int i = 0; i < ps.length; i++) if (!globSegment(ps[i], ts[i])) return false;
        return true;
    }

    /** Ohne Rekursion: Bei einem Fehlschlag springt nur der letzte * weiter – auch "*a*a*a*b" bleibt schnell. */
    private static boolean globSegment(String p, String t) {
        int pi = 0, ti = 0, star = -1, mark = 0;
        while (ti < t.length()) {
            if (pi < p.length() && (p.charAt(pi) == '?' || p.charAt(pi) == t.charAt(ti))) {
                pi++;
                ti++;
            } else if (pi < p.length() && p.charAt(pi) == '*') {
                star = pi++;
                mark = ti;
            } else if (star >= 0) {
                pi = star + 1;
                ti = ++mark;
            } else {
                return false;
            }
        }
        while (pi < p.length() && p.charAt(pi) == '*') pi++;
        return pi == p.length();
    }

    /** Zerlegt eine Zeile in Wörter; Anführungszeichen halten Leerzeichen zusammen. */
    static List<String> tokenize(String line) {
        return lex(line).stream().map(Token::text).toList();
    }

    /** Wie tokenize, erkennt aber |, > und >> außerhalb von Anführungszeichen als Operatoren. */
    static List<Token> lex(String line) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean inToken = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                else current.append(c);
            } else if (c == '"' || c == '\'') {
                quote = c;
                inToken = true;
            } else if (isSpace(c) || c == '|' || c == '>') {
                if (inToken) {
                    tokens.add(new Token(current.toString(), false));
                    current.setLength(0);
                    inToken = false;
                }
                if (c == '|') {
                    tokens.add(new Token("|", true));
                } else if (c == '>') {
                    boolean twice = i + 1 < line.length() && line.charAt(i + 1) == '>';
                    tokens.add(new Token(twice ? ">>" : ">", true));
                    if (twice) i++;
                }
            } else {
                current.append(c);
                inToken = true;
            }
        }
        if (inToken) tokens.add(new Token(current.toString(), false));
        return tokens;
    }
}

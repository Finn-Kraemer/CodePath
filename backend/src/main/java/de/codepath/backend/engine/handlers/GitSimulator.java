package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.Cfg;
import de.codepath.backend.engine.handlers.TerminalSimulator.Out;

import java.util.*;

/**
 * Vereinfachtes Git für Terminal-Aufgaben: ein Repository, Commits, Staging, Branches, Merge mit Konflikten,
 * .gitignore und ein simulierter Server "origin". Arbeitet auf dem Dateisystem des {@link TerminalSimulator};
 * das Frontend hat ein gleichwertiges Modell (frontend/src/lib/tasks/git.ts).
 * Commit-Kennungen sind ein Hash über Inhalt und Reihenfolge – auf Server und im Browser gleich.
 */
public class GitSimulator {

    public record Commit(String id, String message, String author, List<String> parents, SortedMap<String, String> tree, int seq) {
    }

    private static final class Merge {
        final String other;
        final String label;
        final SortedSet<String> all;
        final SortedSet<String> unresolved;

        Merge(String other, String label, SortedSet<String> conflicts) {
            this.other = other;
            this.label = label;
            this.all = new TreeSet<>(conflicts);
            this.unresolved = new TreeSet<>(conflicts);
        }
    }

    private final TerminalSimulator fs;
    private String root;
    private final Map<String, Commit> commits = new HashMap<>();
    private final SortedMap<String, String> branches = new TreeMap<>();
    private String head = "main";
    private SortedMap<String, String> index = new TreeMap<>();
    private SortedMap<String, String> remote;
    private final SortedMap<String, String> tags = new TreeMap<>();
    private Merge merge;
    private int seq;
    private String author = "Du";

    GitSimulator(TerminalSimulator fs) {
        this.fs = fs;
    }

    // --- Zustand (für Bewertung und Tests) ---

    /** Ordner des Repositorys relativ zum Home-Verzeichnis ("" = Home), null = kein Repository. */
    public String root() {
        return root;
    }

    public String head() {
        return head;
    }

    public SortedMap<String, String> branches() {
        return Collections.unmodifiableSortedMap(branches);
    }

    public SortedMap<String, String> remoteBranches() {
        return remote == null ? Collections.emptySortedMap() : Collections.unmodifiableSortedMap(remote);
    }

    public Commit commit(String id) {
        return commits.get(id);
    }

    // --- Vorbereitetes Repository (Config "git") ---

    void setup(Map<String, Object> config) {
        if (!(config.get("root") instanceof String r)) return;
        root = TerminalSimulator.normalize(r);
        fs.mkdirs(root);
        String current = "main";
        for (Map<String, Object> entry : Cfg.objects(config, "history")) {
            current = commitOnto(branches, entry, current);
        }
        head = Cfg.string(config, "head", branches.containsKey("main") || branches.isEmpty() ? "main" : current);
        SortedMap<String, String> tree = headTree();
        tree.forEach((p, content) -> {
            if (fs.readFile(fsPath(p)) == null) fs.putFile(fsPath(p), content);
        });
        index = new TreeMap<>(tree);
        Object remoteConfig = config.get("remote");
        if (Boolean.TRUE.equals(remoteConfig) || remoteConfig instanceof List<?>) {
            remote = new TreeMap<>(branches);
            String remoteCurrent = "main";
            for (Map<String, Object> entry : Cfg.objects(config, "remote")) {
                remoteCurrent = commitOnto(remote, entry, remoteCurrent);
            }
        }
    }

    private String commitOnto(SortedMap<String, String> refs, Map<String, Object> entry, String current) {
        String target = Cfg.string(entry, "branch", current);
        if (!refs.containsKey(target) && refs.get(current) != null) refs.put(target, refs.get(current));
        String tip = refs.get(target);
        SortedMap<String, String> tree = tip == null ? new TreeMap<>() : new TreeMap<>(commits.get(tip).tree());
        Cfg.object(entry, "files").forEach((path, content) -> {
            String p = TerminalSimulator.normalize(path);
            if (content == null) tree.remove(p);
            else tree.put(p, String.valueOf(content));
        });
        Commit c = newCommit(Cfg.string(entry, "message", "Änderung"), Cfg.string(entry, "author", "Team"),
                tip == null ? List.of() : List.of(tip), tree);
        refs.put(target, c.id());
        return target;
    }

    // --- Befehle ---

    Out run(List<String> args) {
        if (args.isEmpty() || args.getFirst().equals("help") || args.getFirst().equals("--help")) return Out.of(HELP);
        String sub = args.getFirst();
        List<String> rest = args.subList(1, args.size());
        if (sub.equals("init")) return init();
        if (sub.equals("--version")) return Out.of("git version 2.47.0 (CodePath-Simulation)");
        if (sub.equals("config")) return config(rest);
        if (root == null) return Out.error("fatal: Hier ist kein Git-Repository. Lege eines an mit: git init");
        if (!inRepo()) return Out.error("fatal: Hier ist kein Git-Repository. Das Repository liegt in " + display(root) + " – wechsle mit cd dorthin.");
        return switch (sub) {
            case "status" -> status();
            case "add" -> add(rest);
            case "commit" -> commit(rest);
            case "log" -> log(rest);
            case "show" -> show(rest);
            case "diff" -> diff(rest);
            case "restore" -> restore(rest);
            case "rm" -> rm(rest);
            case "branch" -> branch(rest);
            case "switch" -> switchCommand(rest);
            case "checkout" -> checkout(rest);
            case "merge" -> merge(rest);
            case "push" -> push(rest);
            case "pull" -> pull(rest);
            case "fetch" -> remote == null ? Out.error(NO_REMOTE) : Out.of("Von origin");
            case "remote" -> remote == null ? Out.of("") : Out.of(rest.contains("-v") ? "origin\tgit@schulserver:team/projekt.git" : "origin");
            case "tag" -> tag(rest);
            default -> Out.error("git: '" + sub + "' ist kein Git-Befehl. Siehe: git help");
        };
    }

    static final String HELP = String.join("\n",
            "Git-Befehle (vereinfacht):",
            "  git init                  Repository anlegen",
            "  git status                Was ist neu, geändert, vorgemerkt?",
            "  git add datei | .         Änderungen für den nächsten Commit vormerken",
            "  git commit -m \"text\"      Vorgemerktes als Commit speichern",
            "  git log [--oneline]       Verlauf ansehen",
            "  git show [commit]         Was hat ein Commit geändert?",
            "  git diff [--staged]       Unterschiede anzeigen",
            "  git restore datei         Datei auf den letzten Stand zurücksetzen",
            "  git restore --staged datei  Vormerkung zurücknehmen",
            "  git branch [name]         Branches zeigen oder anlegen",
            "  git switch [-c] name      Branch wechseln (-c: neu anlegen)",
            "  git merge name            Branch in den aktuellen holen",
            "  git checkout --ours/--theirs datei   Konflikt: eine Seite nehmen",
            "  git pull / git push       mit dem Server (origin) abgleichen",
            "  git tag name              Version markieren",
            "  git rm [--cached] datei   Datei aus Git entfernen");

    static final int MAX_IGNORE_LINES = 200;
    /** Rechenbudget für den Zeilenvergleich je Diff (Zeilen alt × Zeilen neu, über alle Dateien). */
    static final int DIFF_BUDGET = 250_000;

    private static final String NO_REMOTE = "fatal: Es ist kein Server (origin) eingerichtet.";

    private Out init() {
        String here = fs.cwdPath();
        if (root != null) {
            if (root.equals(here)) return Out.of("Das Repository in " + display(root) + " gibt es schon.");
            return Out.error("Fehler: Es gibt schon ein Repository in " + display(root) + " – eins reicht.");
        }
        root = here;
        head = "main";
        return Out.of("Leeres Git-Repository in " + display(root) + "/.git/ angelegt");
    }

    private Out config(List<String> args) {
        List<String> values = args.stream().filter(a -> !a.startsWith("--")).toList();
        if (values.isEmpty()) return Out.error("Beispiel: git config user.name \"Dein Name\"");
        if (!values.getFirst().equals("user.name")) return Out.of("");
        if (values.size() == 1) return Out.of(author);
        if (values.get(1).isBlank()) return Out.error("Fehler: Der Name ist leer.");
        author = values.get(1);
        return Out.of("");
    }

    private Out status() {
        List<String> blocks = new ArrayList<>();
        String first = "Auf Branch " + head;
        String headId = headId();
        if (remote != null && headId != null && remote.get(head) != null) first += "\n" + tracking(headId, remote.get(head));
        blocks.add(first);
        if (headId == null) blocks.add("Noch keine Commits");

        SortedMap<String, String> work = working();
        SortedMap<String, String> headTree = headTree();
        Set<String> conflicts = merge == null ? Set.of() : merge.unresolved;
        if (!conflicts.isEmpty()) {
            List<String> lines = new ArrayList<>(List.of("Nicht zusammengeführte Pfade:", "  (Konflikt lösen, dann: git add <Datei>)"));
            for (String p : conflicts) lines.add(entry("beide geändert", p));
            blocks.add(String.join("\n", lines));
        } else if (merge != null) {
            blocks.add("Alle Konflikte gelöst – schließe die Zusammenführung ab mit: git commit");
        }

        List<String> staged = new ArrayList<>();
        SortedSet<String> keys = new TreeSet<>(index.keySet());
        keys.addAll(headTree.keySet());
        for (String p : keys) {
            if (conflicts.contains(p)) continue;
            String i = index.get(p), h = headTree.get(p);
            if (Objects.equals(i, h)) continue;
            staged.add(entry(h == null ? "neue Datei" : i == null ? "gelöscht" : "geändert", p));
        }
        List<String> unstaged = new ArrayList<>();
        for (var e : index.entrySet()) {
            if (conflicts.contains(e.getKey())) continue;
            String w = work.get(e.getKey());
            if (w == null) unstaged.add(entry("gelöscht", e.getKey()));
            else if (!w.equals(e.getValue())) unstaged.add(entry("geändert", e.getKey()));
        }
        List<String> untracked = new ArrayList<>();
        for (String p : work.keySet()) {
            if (!index.containsKey(p) && !conflicts.contains(p) && !ignored(p)) untracked.add("\t" + p);
        }

        if (!staged.isEmpty()) blocks.add("Zum Commit vorgemerkte Änderungen:\n  (zurücknehmen mit: git restore --staged <Datei>)\n" + String.join("\n", staged));
        if (!unstaged.isEmpty()) blocks.add("Änderungen, die nicht zum Commit vorgemerkt sind:\n  (vormerken mit: git add <Datei>)\n" + String.join("\n", unstaged));
        if (!untracked.isEmpty()) blocks.add("Unversionierte Dateien:\n  (vormerken mit: git add <Datei>)\n" + String.join("\n", untracked));
        if (merge == null) {
            if (staged.isEmpty() && (!unstaged.isEmpty() || !untracked.isEmpty())) {
                blocks.add("keine Änderungen zum Commit vorgemerkt (benutze: git add)");
            } else if (staged.isEmpty()) {
                blocks.add(headId == null ? "nichts zu committen (lege Dateien an und merke sie vor mit: git add)" : "nichts zu committen, Arbeitsverzeichnis sauber");
            }
        }
        return Out.of(String.join("\n\n", blocks));
    }

    private String tracking(String local, String upstream) {
        int ahead = count(reachable(List.of(local)), reachable(List.of(upstream)));
        int behind = count(reachable(List.of(upstream)), reachable(List.of(local)));
        String ref = "'origin/" + head + "'";
        if (ahead == 0 && behind == 0) return "Dein Branch ist auf demselben Stand wie " + ref + ".";
        if (behind == 0) return "Dein Branch ist " + plural(ahead, "Commit", "Commits") + " vor " + ref + ". (hochladen mit: git push)";
        if (ahead == 0) return "Dein Branch ist " + plural(behind, "Commit", "Commits") + " hinter " + ref + ". (holen mit: git pull)";
        return "Dein Branch und " + ref + " sind auseinandergelaufen. (zusammenführen mit: git pull)";
    }

    private static int count(Set<String> a, Set<String> without) {
        int n = 0;
        for (String id : a) if (!without.contains(id)) n++;
        return n;
    }

    private static String entry(String label, String path) {
        String l = label + ":";
        return "\t" + l + " ".repeat(Math.max(1, 16 - l.length())) + path;
    }

    private Out add(List<String> args) {
        if (args.isEmpty()) return Out.error("Was soll vorgemerkt werden? Beispiel: git add index.html (oder git add . für alles)");
        SortedMap<String, String> work = working();
        StringBuilder err = new StringBuilder();
        for (String arg : args) {
            boolean all = arg.equals("-A") || arg.equals("--all");
            if (arg.startsWith("-") && !all) {
                err.append("git add: unbekannte Option ").append(arg).append('\n');
                continue;
            }
            String spec = all ? "" : repoPath(arg);
            if (spec == null) {
                err.append("fatal: ").append(arg).append(" liegt außerhalb des Repositorys\n");
                continue;
            }
            SortedSet<String> candidates = matching(spec, work.keySet(), index.keySet());
            if (candidates.isEmpty()) {
                err.append("fatal: Pfad '").append(arg).append("' passt zu keiner Datei\n");
                continue;
            }
            for (String p : candidates) {
                String content = work.get(p);
                if (content == null) {
                    index.remove(p);
                } else if (!index.containsKey(p) && ignored(p)) {
                    if (p.equals(spec)) err.append("Die Datei ").append(arg).append(" steht in .gitignore und wird nicht vorgemerkt.\n");
                    continue;
                } else {
                    index.put(p, content);
                }
                if (merge != null) merge.unresolved.remove(p);
            }
        }
        return Out.error(err.toString());
    }

    private Out commit(List<String> args) {
        boolean all = false;
        String message = null;
        boolean wantsMessage = false;
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("-m") || a.equals("--message") || a.equals("-am")) {
                if (a.equals("-am")) all = true;
                wantsMessage = true;
                if (i + 1 < args.size()) message = args.get(++i);
            } else if (a.equals("-a") || a.equals("--all")) {
                all = true;
            } else if (a.startsWith("-m") && a.length() > 2) {
                wantsMessage = true;
                message = a.substring(2);
            } else {
                return Out.error("git commit: unbekannte Angabe '" + a + "' – Beispiel: git commit -m \"Startseite angelegt\"");
            }
        }
        if (wantsMessage && message == null) return Out.error("Fehler: Nach -m fehlt die Nachricht, z. B.: git commit -m \"Startseite angelegt\"");
        if (all) {
            SortedMap<String, String> work = working();
            for (String p : new ArrayList<>(index.keySet())) {
                String w = work.get(p);
                if (w == null) index.remove(p);
                else index.put(p, w);
                if (merge != null) merge.unresolved.remove(p);
            }
        }
        if (merge != null && !merge.unresolved.isEmpty()) {
            return Out.error("Fehler: Commit nicht möglich – es gibt noch Konflikte in: " + String.join(", ", merge.unresolved)
                    + "\n(Datei reparieren oder git checkout --ours/--theirs <Datei>, dann git add <Datei>)");
        }
        if (message == null) {
            if (merge == null) return Out.error("Fehler: Bitte gib eine Nachricht an, z. B.: git commit -m \"Startseite angelegt\"");
            message = "Merge branch '" + merge.label + "'";
        }
        if (message.isBlank()) return Out.error("Fehler: Die Nachricht ist leer.");
        SortedMap<String, String> before = headTree();
        if (merge == null && index.equals(before)) {
            boolean pending = !working().equals(index);
            return Out.of("Auf Branch " + head + "\nnichts zu committen" + (pending ? " – merke Änderungen zuerst vor mit: git add <Datei>" : ", Arbeitsverzeichnis sauber"));
        }
        String parent = headId();
        List<String> parents = new ArrayList<>();
        if (parent != null) parents.add(parent);
        if (merge != null) parents.add(merge.other);
        Commit c = newCommit(message, author, parents, new TreeMap<>(index));
        branches.put(head, c.id());
        merge = null;
        int changed = changedCount(before, index);
        return Out.of("[" + head + (parent == null ? " (Root-Commit)" : "") + " " + c.id() + "] " + message
                + "\n " + plural(changed, "Datei", "Dateien") + " geändert");
    }

    private Out log(List<String> args) {
        boolean oneline = false, allRefs = false;
        Integer limit = null;
        String ref = null;
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("--oneline")) oneline = true;
            else if (a.equals("--all")) allRefs = true;
            else if (a.equals("--graph")) continue;
            else if (a.equals("-n") && i + 1 < args.size()) limit = parseInt(args.get(++i));
            else if (a.startsWith("-") && parseInt(a.substring(1)) != null) limit = parseInt(a.substring(1));
            else if (a.startsWith("-")) return Out.error("git log: unbekannte Option " + a);
            else ref = a;
        }
        List<String> starts = new ArrayList<>();
        if (allRefs) {
            starts.addAll(branches.values());
            if (remote != null) starts.addAll(remote.values());
        } else if (ref != null) {
            String id = resolve(ref);
            if (id == null) return Out.error("fatal: '" + ref + "' gibt es nicht");
            starts.add(id);
        } else if (headId() != null) {
            starts.add(headId());
        }
        if (starts.isEmpty()) return Out.error("fatal: Branch '" + head + "' hat noch keine Commits");
        List<Commit> list = sorted(reachable(starts));
        if (limit != null && limit < list.size()) list = list.subList(0, limit);
        List<String> out = new ArrayList<>();
        for (Commit c : list) out.add(oneline ? oneline(c) : full(c));
        return Out.of(String.join(oneline ? "\n" : "\n\n", out));
    }

    private String oneline(Commit c) {
        String deco = decorations(c.id());
        return c.id() + (deco.isEmpty() ? "" : " (" + deco + ")") + " " + c.message().split("\n", -1)[0];
    }

    private String full(Commit c) {
        String deco = decorations(c.id());
        StringBuilder s = new StringBuilder("commit ").append(c.id());
        if (!deco.isEmpty()) s.append(" (").append(deco).append(')');
        if (c.parents().size() > 1) s.append("\nMerge: ").append(String.join(" ", c.parents()));
        s.append("\nAutor: ").append(c.author()).append("\n\n    ").append(c.message().replace("\n", "\n    "));
        return s.toString();
    }

    private String decorations(String id) {
        List<String> refs = new ArrayList<>();
        if (id.equals(headId())) refs.add("HEAD -> " + head);
        branches.forEach((name, target) -> {
            if (target.equals(id) && !name.equals(head)) refs.add(name);
        });
        if (remote != null) remote.forEach((name, target) -> {
            if (target.equals(id)) refs.add("origin/" + name);
        });
        tags.forEach((name, target) -> {
            if (target.equals(id)) refs.add("tag: " + name);
        });
        return String.join(", ", refs);
    }

    private Out show(List<String> args) {
        String ref = args.stream().filter(a -> !a.startsWith("-")).findFirst().orElse("HEAD");
        String id = resolve(ref);
        if (id == null) return Out.error(ref.equals("HEAD") ? "fatal: Noch keine Commits" : "fatal: '" + ref + "' gibt es nicht");
        Commit c = commits.get(id);
        SortedMap<String, String> before = c.parents().isEmpty() ? new TreeMap<>() : commits.get(c.parents().getFirst()).tree();
        String diff = diffTrees(before, c.tree(), null);
        return Out.of(full(c) + (diff.isEmpty() ? "" : "\n\n" + diff));
    }

    private Out diff(List<String> args) {
        boolean staged = args.contains("--staged") || args.contains("--cached");
        Set<String> filter = null;
        for (String a : args) {
            if (a.startsWith("-")) continue;
            String spec = repoPath(a);
            if (spec == null) return Out.error("fatal: " + a + " liegt außerhalb des Repositorys");
            if (filter == null) filter = new TreeSet<>();
            filter.add(spec);
        }
        if (staged) return Out.of(diffTrees(headTree(), index, filter));
        SortedMap<String, String> work = working();
        SortedMap<String, String> tracked = new TreeMap<>();
        for (String p : index.keySet()) if (work.containsKey(p)) tracked.put(p, work.get(p));
        return Out.of(diffTrees(index, tracked, filter));
    }

    private Out restore(List<String> args) {
        boolean staged = args.contains("--staged") || args.contains("-S");
        List<String> paths = args.stream().filter(a -> !a.startsWith("-")).toList();
        if (paths.isEmpty()) return Out.error("Welche Datei? Beispiel: git restore index.html");
        SortedMap<String, String> headTree = headTree();
        StringBuilder err = new StringBuilder();
        for (String arg : paths) {
            String spec = repoPath(arg);
            if (spec == null) {
                err.append("fatal: ").append(arg).append(" liegt außerhalb des Repositorys\n");
                continue;
            }
            SortedSet<String> candidates = staged ? matching(spec, index.keySet(), headTree.keySet()) : matching(spec, index.keySet());
            if (candidates.isEmpty()) {
                err.append("Fehler: '").append(arg).append("' kennt Git nicht – nur Dateien, die schon vorgemerkt oder committet sind, lassen sich zurückholen.\n");
                continue;
            }
            for (String p : candidates) {
                if (staged) {
                    if (headTree.containsKey(p)) index.put(p, headTree.get(p));
                    else index.remove(p);
                } else {
                    fs.putFile(fsPath(p), index.get(p));
                }
            }
        }
        return Out.error(err.toString());
    }

    private Out rm(List<String> args) {
        boolean cached = args.contains("--cached");
        List<String> paths = args.stream().filter(a -> !a.startsWith("-")).toList();
        if (paths.isEmpty()) return Out.error("Welche Datei? Beispiel: git rm --cached passwort.txt");
        List<String> out = new ArrayList<>();
        StringBuilder err = new StringBuilder();
        for (String arg : paths) {
            String spec = repoPath(arg);
            SortedSet<String> candidates = spec == null ? new TreeSet<>() : matching(spec, index.keySet());
            if (candidates.isEmpty()) {
                err.append("fatal: '").append(arg).append("' ist nicht in Git\n");
                continue;
            }
            for (String p : candidates) {
                index.remove(p);
                if (!cached) fs.deleteFile(fsPath(p), root);
                out.add("rm '" + p + "'");
            }
        }
        return new Out(String.join("\n", out), err.toString());
    }

    private Out branch(List<String> args) {
        boolean all = args.contains("-a");
        List<String> rest = args.stream().filter(a -> !a.equals("-a") && !a.equals("--list") && !a.equals("-v")).toList();
        if (rest.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (String name : branches.keySet()) out.add((name.equals(head) ? "* " : "  ") + name);
            if (all && remote != null) for (String name : remote.keySet()) out.add("  remotes/origin/" + name);
            return Out.of(String.join("\n", out));
        }
        String first = rest.getFirst();
        if (first.equals("-d") || first.equals("-D")) {
            if (rest.size() < 2) return Out.error("Welcher Branch? Beispiel: git branch -d kontakt");
            String name = rest.get(1);
            if (!branches.containsKey(name)) return Out.error("Fehler: Branch '" + name + "' gibt es nicht.");
            if (name.equals(head)) return Out.error("Fehler: Du bist gerade auf '" + name + "' – wechsle zuerst auf einen anderen Branch.");
            String id = branches.get(name);
            if (first.equals("-d") && (headId() == null || !isAncestor(id, headId()))) {
                return Out.error("Fehler: Branch '" + name + "' ist noch nicht zusammengeführt. (Trotzdem löschen: git branch -D " + name + ")");
            }
            branches.remove(name);
            return Out.of("Branch " + name + " entfernt (war " + id + ").");
        }
        if (first.startsWith("-")) return Out.error("git branch: unbekannte Option " + first);
        String problem = checkName(first);
        if (problem != null) return Out.error(problem);
        if (branches.containsKey(first)) return Out.error("fatal: Branch '" + first + "' gibt es schon");
        String start = rest.size() > 1 ? resolve(rest.get(1)) : headId();
        if (start == null) return Out.error(rest.size() > 1 ? "fatal: '" + rest.get(1) + "' gibt es nicht"
                : "Fehler: Lege zuerst einen Commit an – vorher gibt es nichts, wovon ein Branch abzweigen kann.");
        branches.put(first, start);
        return Out.of("");
    }

    private static String checkName(String name) {
        boolean ok = !name.isEmpty() && !name.startsWith("-") && !name.startsWith("/") && !name.endsWith("/")
                && !name.contains("..") && !name.endsWith(".") && name.chars().allMatch(c ->
                (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '/' || c == '.');
        return ok ? null : "fatal: '" + name + "' ist kein gültiger Name (erlaubt: Buchstaben ohne Umlaute, Ziffern, - _ / .)";
    }

    private Out switchCommand(List<String> args) {
        if (args.isEmpty()) return Out.error("Wohin? Beispiel: git switch main (neuer Branch: git switch -c name)");
        if (args.getFirst().equals("-c") || args.getFirst().equals("--create")) {
            if (args.size() < 2) return Out.error("Name fehlt. Beispiel: git switch -c kontakt");
            return switchTo(args.get(1), true);
        }
        return switchTo(args.getFirst(), false);
    }

    private Out switchTo(String name, boolean create) {
        if (merge != null) return Out.error(MERGING);
        if (create) {
            String problem = checkName(name);
            if (problem != null) return Out.error(problem);
            if (branches.containsKey(name)) return Out.error("fatal: Branch '" + name + "' gibt es schon (wechseln mit: git switch " + name + ")");
            if (headId() != null) branches.put(name, headId());
            head = name;
            return Out.of("Zu neuem Branch '" + name + "' gewechselt");
        }
        if (name.equals(head)) return Out.of("Bereits auf '" + name + "'");
        boolean fromRemote = !branches.containsKey(name) && remote != null && remote.containsKey(name);
        if (!branches.containsKey(name) && !fromRemote) {
            return Out.error("fatal: Branch '" + name + "' gibt es nicht. (Neu anlegen mit: git switch -c " + name + ")");
        }
        if (dirty()) return Out.error(DIRTY);
        if (fromRemote) branches.put(name, remote.get(name));
        checkoutTree(headTree(), commits.get(branches.get(name)).tree());
        head = name;
        return Out.of((fromRemote ? "Branch '" + name + "' folgt jetzt 'origin/" + name + "'.\n" : "") + "Zu Branch '" + name + "' gewechselt");
    }

    private static final String MERGING = "Fehler: Du steckst mitten in einer Zusammenführung. Erst Konflikte lösen und committen (oder abbrechen: git merge --abort).";
    private static final String DIRTY = "Fehler: Du hast Änderungen, die noch nicht committet sind.\nCommitte sie zuerst (git add, git commit) – oder verwirf sie mit: git restore <Datei>";

    private Out checkout(List<String> args) {
        if (args.isEmpty()) return Out.error("Wohin? Beispiel: git checkout main");
        String first = args.getFirst();
        List<String> rest = args.subList(1, args.size());
        if (first.equals("-b")) {
            if (rest.isEmpty()) return Out.error("Name fehlt. Beispiel: git checkout -b kontakt");
            return switchTo(rest.getFirst(), true);
        }
        if (first.equals("--ours") || first.equals("--theirs")) return takeSide(rest, first.equals("--ours"));
        if (first.equals("--")) return restore(rest);
        if (branches.containsKey(first) || (remote != null && remote.containsKey(first))) return switchTo(first, false);
        String spec = repoPath(first);
        if (spec != null && !matching(spec, index.keySet()).isEmpty()) return restore(args);
        return Out.error("Fehler: '" + first + "' ist weder ein Branch noch eine Datei, die Git kennt.");
    }

    private Out takeSide(List<String> paths, boolean ours) {
        if (merge == null) return Out.error("Fehler: --ours und --theirs gibt es nur während eines Merge-Konflikts.");
        if (paths.isEmpty()) return Out.error("Welche Datei? Beispiel: git checkout --theirs index.html");
        StringBuilder err = new StringBuilder();
        SortedMap<String, String> tree = ours ? headTree() : commits.get(merge.other).tree();
        for (String arg : paths) {
            String p = repoPath(arg);
            if (p == null || !merge.all.contains(p)) {
                err.append("Fehler: '").append(arg).append("' hat keinen Konflikt.\n");
                continue;
            }
            String content = tree.get(p);
            if (content == null) fs.deleteFile(fsPath(p), root);
            else fs.putFile(fsPath(p), content);
        }
        return Out.error(err.toString());
    }

    private Out merge(List<String> args) {
        if (args.contains("--abort")) {
            if (merge == null) return Out.error("Es läuft gerade keine Zusammenführung.");
            SortedMap<String, String> current = new TreeMap<>(index);
            for (String p : merge.all) current.putIfAbsent(p, "");
            checkoutTree(current, headTree());
            merge = null;
            return Out.of("Zusammenführung abgebrochen.");
        }
        if (merge != null) return Out.error(MERGING);
        String ref = args.stream().filter(a -> !a.startsWith("-")).findFirst().orElse(null);
        if (ref == null) return Out.error("Welchen Branch? Beispiel: git merge kontakt");
        String other = resolve(ref);
        if (other == null) return Out.error("fatal: '" + ref + "' gibt es nicht");
        return mergeWith(other, ref);
    }

    private Out mergeWith(String other, String label) {
        String headId = headId();
        if (headId == null) {
            checkoutTree(headTree(), commits.get(other).tree());
            branches.put(head, other);
            return Out.of("Fast-forward");
        }
        if (dirty()) return Out.error(DIRTY);
        if (isAncestor(other, headId)) return Out.of("Bereits aktuell.");
        SortedMap<String, String> ours = headTree();
        if (isAncestor(headId, other)) {
            SortedMap<String, String> theirs = commits.get(other).tree();
            checkoutTree(ours, theirs);
            branches.put(head, other);
            return Out.of("Aktualisiere " + headId + ".." + other + "\nFast-forward\n " + plural(changedCount(ours, theirs), "Datei", "Dateien") + " geändert");
        }
        String base = mergeBase(headId, other);
        SortedMap<String, String> baseTree = base == null ? new TreeMap<>() : commits.get(base).tree();
        SortedMap<String, String> theirs = commits.get(other).tree();
        SortedSet<String> keys = new TreeSet<>(ours.keySet());
        keys.addAll(theirs.keySet());
        keys.addAll(baseTree.keySet());
        SortedMap<String, String> merged = new TreeMap<>();
        SortedSet<String> conflicts = new TreeSet<>();
        for (String p : keys) {
            String o = ours.get(p), t = theirs.get(p), b = baseTree.get(p);
            String r;
            if (Objects.equals(o, t)) r = o;
            else if (Objects.equals(o, b)) r = t;
            else if (Objects.equals(t, b)) r = o;
            else {
                conflicts.add(p);
                continue;
            }
            if (r != null) merged.put(p, r);
        }
        checkoutTree(ours, merged);
        if (conflicts.isEmpty()) {
            Commit c = newCommit("Merge branch '" + label + "'", author, List.of(headId, other), new TreeMap<>(merged));
            branches.put(head, c.id());
            return Out.of("Zusammenführung mit der 'ort'-Strategie.\n " + plural(changedCount(ours, merged), "Datei", "Dateien") + " geändert");
        }
        List<String> out = new ArrayList<>();
        for (String p : conflicts) {
            String o = ours.get(p), t = theirs.get(p);
            if (o != null) index.put(p, o);
            fs.putFile(fsPath(p), "<<<<<<< HEAD\n" + withNewline(o) + "=======\n" + withNewline(t) + ">>>>>>> " + label + "\n");
            out.add("KONFLIKT: Beide Seiten haben " + p + " geändert.");
        }
        merge = new Merge(other, label, conflicts);
        out.add("Automatisches Zusammenführen fehlgeschlagen. Löse die Konflikte und committe dann das Ergebnis.");
        out.add("(Tipp: cat " + conflicts.first() + " zeigt die Konfliktstelle)");
        return Out.of(String.join("\n", out));
    }

    private static String withNewline(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.endsWith("\n") ? s : s + "\n";
    }

    private Out push(List<String> args) {
        if (remote == null) return Out.error(NO_REMOTE);
        String[] target = remoteTarget(args);
        if (target[1] != null) return Out.error(target[1]);
        String name = target[0];
        String local = branches.get(name);
        if (local == null) return Out.error("Fehler: Branch '" + name + "' hat noch keine Commits");
        String upstream = remote.get(name);
        if (local.equals(upstream)) return Out.of("Alles aktuell.");
        if (upstream == null) {
            remote.put(name, local);
            return Out.of("Nach origin\n * [neuer Branch]      " + name + " -> " + name);
        }
        if (!isAncestor(upstream, local)) {
            return Out.error("Nach origin\n ! [abgelehnt]        " + name + " -> " + name + " (zuerst holen)"
                    + "\nFehler: Auf dem Server gibt es neue Commits, die du noch nicht hast.\nHole sie zuerst mit: git pull");
        }
        remote.put(name, local);
        return Out.of("Nach origin\n   " + upstream + ".." + local + "  " + name + " -> " + name);
    }

    private Out pull(List<String> args) {
        if (remote == null) return Out.error(NO_REMOTE);
        String[] target = remoteTarget(args);
        if (target[1] != null) return Out.error(target[1]);
        String upstream = remote.get(target[0]);
        if (upstream == null) return Out.error("fatal: Auf dem Server gibt es keinen Branch '" + target[0] + "'");
        Out result = mergeWith(upstream, "origin/" + target[0]);
        return new Out("Von origin" + (result.out().isEmpty() ? "" : "\n" + result.out()), result.err());
    }

    /** [Branch, Fehler] aus "git push|pull [-u] [origin] [branch]". */
    private String[] remoteTarget(List<String> args) {
        List<String> positional = args.stream().filter(a -> !a.startsWith("-")).toList();
        if (!positional.isEmpty() && !positional.getFirst().equals("origin")) {
            return new String[]{null, "fatal: '" + positional.getFirst() + "' ist kein bekannter Server – hier heißt er origin"};
        }
        return new String[]{positional.size() > 1 ? positional.get(1) : head, null};
    }

    private Out tag(List<String> args) {
        List<String> names = args.stream().filter(a -> !a.startsWith("-")).toList();
        if (names.isEmpty()) return Out.of(String.join("\n", tags.keySet()));
        String name = names.getFirst();
        String problem = checkName(name);
        if (problem != null) return Out.error(problem);
        if (tags.containsKey(name)) return Out.error("fatal: Tag '" + name + "' gibt es schon");
        String id = names.size() > 1 ? resolve(names.get(1)) : headId();
        if (id == null) return Out.error("Fehler: Lege zuerst einen Commit an.");
        tags.put(name, id);
        return Out.of("");
    }

    // --- Prüfungen für die Bewertung ---

    /** null, wenn die Bedingung erfüllt ist – sonst eine kurze Beschreibung, was fehlt. */
    public String check(Map<String, Object> check) {
        String type = Cfg.string(check, "check", "");
        String label = Cfg.string(check, "label");
        String missing = switch (type) {
            case "repo" -> {
                String path = Cfg.string(check, "path");
                yield root != null && (path == null || TerminalSimulator.normalize(path).equals(root)) ? null
                        : "Es gibt noch kein Git-Repository" + (path == null ? "" : " im Ordner " + path) + ".";
            }
            case "commits" -> {
                String id = refOrHead(Cfg.string(check, "branch"));
                int min = Objects.requireNonNullElse(Cfg.integer(check, "min"), 1);
                yield id != null && reachable(List.of(id)).size() >= min ? null : "Es braucht mindestens " + plural(min, "Commit", "Commits") + ".";
            }
            case "committed" -> {
                String path = TerminalSimulator.normalize(Cfg.string(check, "path", ""));
                String id = refOrHead(Cfg.string(check, "branch"));
                String content = id == null ? null : commits.get(id).tree().get(path);
                if (content == null) yield path + " ist noch nicht committet.";
                String expected = Cfg.string(check, "content");
                String contains = Cfg.string(check, "contains");
                String notContains = Cfg.string(check, "notContains");
                boolean ok = (expected == null || content.strip().equals(expected.strip()))
                        && (contains == null || content.contains(contains))
                        && (notContains == null || !content.contains(notContains));
                yield ok ? null : "Der committete Inhalt von " + path + " stimmt noch nicht.";
            }
            case "neverCommitted" -> {
                String path = TerminalSimulator.normalize(Cfg.string(check, "path", ""));
                List<String> starts = new ArrayList<>(branches.values());
                if (remote != null) starts.addAll(remote.values());
                boolean found = reachable(starts).stream().anyMatch(id -> commits.get(id).tree().containsKey(path));
                yield found ? path + " darf in keinem Commit stecken." : null;
            }
            case "branch" -> branches.containsKey(Cfg.string(check, "name", "")) ? null : "Branch " + Cfg.string(check, "name", "") + " fehlt.";
            case "head" -> head.equals(Cfg.string(check, "branch", "")) && root != null ? null : "Du solltest am Ende auf Branch " + Cfg.string(check, "branch", "") + " sein.";
            case "merged" -> {
                String from = branches.get(Cfg.string(check, "branch", ""));
                String into = branches.get(Cfg.string(check, "into", "main"));
                yield from != null && into != null && isAncestor(from, into) ? null
                        : Cfg.string(check, "branch", "") + " ist noch nicht in " + Cfg.string(check, "into", "main") + " zusammengeführt.";
            }
            case "clean" -> root != null && merge == null && !dirty() && untracked().isEmpty() ? null : "Es gibt noch Änderungen, die nicht committet sind (git status).";
            case "pushed" -> {
                String name = Cfg.string(check, "branch", "main");
                yield branches.get(name) != null && remote != null && branches.get(name).equals(remote.get(name)) ? null
                        : "Der Server (origin) hat den Stand von " + name + " noch nicht.";
            }
            case "tag" -> {
                String name = Cfg.string(check, "name", "");
                String at = Cfg.string(check, "at");
                String id = tags.get(name);
                yield id != null && (at == null || id.equals(branches.get(at))) ? null : "Tag " + name + " fehlt" + (at == null ? "." : " am neuesten Commit von " + at + ".");
            }
            case "ignored" -> root != null && ignored(TerminalSimulator.normalize(Cfg.string(check, "path", ""))) ? null
                    : Cfg.string(check, "path", "") + " wird noch nicht von .gitignore ausgeschlossen.";
            case "author" -> {
                String id = refOrHead(Cfg.string(check, "branch"));
                yield id != null && commits.get(id).author().equals(Cfg.string(check, "name", "")) ? null : "Der letzte Commit hat noch nicht den richtigen Namen.";
            }
            default -> "Unbekannte Prüfung " + type + ".";
        };
        return missing == null ? null : label != null ? label : missing;
    }

    static final Set<String> CHECKS = Set.of("repo", "commits", "committed", "neverCommitted", "branch", "head", "merged",
            "clean", "pushed", "tag", "ignored", "author");

    private String refOrHead(String branch) {
        return branch == null ? headId() : branches.get(branch);
    }

    private List<String> untracked() {
        List<String> result = new ArrayList<>();
        for (String p : working().keySet()) if (!index.containsKey(p) && !ignored(p)) result.add(p);
        return result;
    }

    // --- Hilfen ---

    private String headId() {
        return branches.get(head);
    }

    private SortedMap<String, String> headTree() {
        String id = headId();
        return id == null ? new TreeMap<>() : new TreeMap<>(commits.get(id).tree());
    }

    private boolean dirty() {
        if (!index.equals(headTree())) return true;
        SortedMap<String, String> work = working();
        for (var e : index.entrySet()) if (!e.getValue().equals(work.get(e.getKey()))) return true;
        return false;
    }

    /** Dateien im Repository-Ordner, Pfade relativ zum Repository. */
    private SortedMap<String, String> working() {
        SortedMap<String, String> result = new TreeMap<>();
        String prefix = root.isEmpty() ? "" : root + "/";
        fs.files().forEach((path, content) -> {
            if (!path.startsWith(prefix)) return;
            String p = path.substring(prefix.length());
            if (!p.startsWith(".git/")) result.put(p, content);
        });
        return result;
    }

    private void checkoutTree(SortedMap<String, String> from, SortedMap<String, String> to) {
        for (String p : from.keySet()) if (!to.containsKey(p)) fs.deleteFile(fsPath(p), root);
        to.forEach((p, content) -> fs.putFile(fsPath(p), content));
        index = new TreeMap<>(to);
    }

    private boolean inRepo() {
        String here = fs.cwdPath();
        return root.isEmpty() || here.equals(root) || here.startsWith(root + "/");
    }

    /** Pfad aus einem Argument relativ zum Repository, null wenn außerhalb. */
    private String repoPath(String arg) {
        String path = fs.resolve(arg);
        if (root.isEmpty()) return path;
        if (path.equals(root)) return "";
        return path.startsWith(root + "/") ? path.substring(root.length() + 1) : null;
    }

    private String fsPath(String repoPath) {
        return root.isEmpty() ? repoPath : root + "/" + repoPath;
    }

    private static String display(String path) {
        return "~" + (path.isEmpty() ? "" : "/" + path);
    }

    @SafeVarargs
    private static SortedSet<String> matching(String spec, Collection<String>... sources) {
        SortedSet<String> result = new TreeSet<>();
        for (Collection<String> source : sources) {
            for (String p : source) if (spec.isEmpty() || p.equals(spec) || p.startsWith(spec + "/")) result.add(p);
        }
        return result;
    }

    /** Wird der Pfad von der .gitignore im Repository-Ordner ausgeschlossen? Muster: name, *.log, ordner/, /pfad. */
    public boolean ignored(String path) {
        if (root == null) return false;
        String content = fs.readFile(fsPath(".gitignore"));
        if (content == null) return false;
        String[] segments = path.split("/");
        List<String> patterns = TerminalSimulator.lines(content);
        for (String raw : patterns.subList(0, Math.min(patterns.size(), MAX_IGNORE_LINES))) {
            String p = trim(raw);
            if (p.isEmpty() || p.startsWith("#")) continue;
            boolean dirOnly = p.endsWith("/");
            while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
            boolean anchored = p.startsWith("/") || p.contains("/");
            while (p.startsWith("/")) p = p.substring(1);
            if (p.isEmpty()) continue;
            String acc = "";
            for (int i = 0; i < segments.length; i++) {
                acc = i == 0 ? segments[0] : acc + "/" + segments[i];
                if (dirOnly && i == segments.length - 1) continue;
                if (TerminalSimulator.glob(p, anchored ? acc : segments[i])) return true;
            }
        }
        return false;
    }

    private static String trim(String s) {
        int start = 0;
        while (start < s.length() && TerminalSimulator.isSpace(s.charAt(start))) start++;
        return TerminalSimulator.trimEnd(s.substring(start));
    }

    private String resolve(String ref) {
        if (ref.equals("HEAD")) return headId();
        if (ref.startsWith("HEAD~") || ref.startsWith("HEAD^")) {
            Integer steps = ref.length() == 5 ? Integer.valueOf(1) : parseInt(ref.substring(5));
            String id = headId();
            for (int i = 0; steps != null && i < steps && id != null; i++) {
                List<String> parents = commits.get(id).parents();
                id = parents.isEmpty() ? null : parents.getFirst();
            }
            return steps == null ? null : id;
        }
        if (branches.containsKey(ref)) return branches.get(ref);
        if (ref.startsWith("origin/") && remote != null && remote.containsKey(ref.substring(7))) return remote.get(ref.substring(7));
        if (tags.containsKey(ref)) return tags.get(ref);
        if (ref.length() >= 4) {
            for (Commit c : sorted(commits.keySet())) if (c.id().startsWith(ref)) return c.id();
        }
        return null;
    }

    private Set<String> reachable(Collection<String> starts) {
        Set<String> seen = new HashSet<>();
        Deque<String> todo = new ArrayDeque<>(starts);
        while (!todo.isEmpty()) {
            String id = todo.pop();
            if (id == null || !seen.add(id)) continue;
            todo.addAll(commits.get(id).parents());
        }
        return seen;
    }

    private boolean isAncestor(String ancestor, String of) {
        return reachable(List.of(of)).contains(ancestor);
    }

    private String mergeBase(String a, String b) {
        Set<String> fromB = reachable(List.of(b));
        Commit best = null;
        for (String id : reachable(List.of(a))) {
            Commit c = commits.get(id);
            if (fromB.contains(id) && (best == null || c.seq() > best.seq())) best = c;
        }
        return best == null ? null : best.id();
    }

    /** Neueste zuerst. */
    private List<Commit> sorted(Collection<String> ids) {
        List<Commit> list = new ArrayList<>();
        for (String id : ids) list.add(commits.get(id));
        list.sort((x, y) -> Integer.compare(y.seq(), x.seq()));
        return list;
    }

    private Commit newCommit(String message, String author, List<String> parents, SortedMap<String, String> tree) {
        seq++;
        StringBuilder s = new StringBuilder().append(seq).append('\n').append(String.join(",", parents)).append('\n')
                .append(author).append('\n').append(message).append('\n');
        tree.forEach((p, content) -> s.append(p).append('\u0000').append(content).append('\n'));
        String id = hash(s.toString());
        while (commits.containsKey(id)) {
            s.append('+');
            id = hash(s.toString());
        }
        Commit c = new Commit(id, message, author, List.copyOf(parents), Collections.unmodifiableSortedMap(new TreeMap<>(tree)), seq);
        commits.put(id, c);
        return c;
    }

    /** FNV-1a (32 Bit), die ersten 7 Hex-Zeichen – genauso in git.ts. */
    static String hash(String s) {
        int h = 0x811c9dc5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        String hex = Integer.toHexString(h);
        return ("0".repeat(8 - hex.length()) + hex).substring(0, 7);
    }

    private static int changedCount(Map<String, String> a, Map<String, String> b) {
        Set<String> keys = new TreeSet<>(a.keySet());
        keys.addAll(b.keySet());
        int n = 0;
        for (String k : keys) if (!Objects.equals(a.get(k), b.get(k))) n++;
        return n;
    }

    private static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    private static Integer parseInt(String s) {
        if (s.isEmpty() || s.length() > 6) return null;
        for (char c : s.toCharArray()) if (c < '0' || c > '9') return null;
        return Integer.parseInt(s);
    }

    /** Unterschiede als vollständige Dateien mit -/+ je Zeile. */
    static String diffTrees(Map<String, String> before, Map<String, String> after, Set<String> filter) {
        SortedSet<String> keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        List<String> out = new ArrayList<>();
        int budget = DIFF_BUDGET;
        int size = 0;
        for (String p : keys) {
            if (filter != null && filter.stream().noneMatch(f -> f.isEmpty() || p.equals(f) || p.startsWith(f + "/"))) continue;
            String a = before.get(p), b = after.get(p);
            if (Objects.equals(a, b)) continue;
            List<String> la = TerminalSimulator.lines(a == null ? "" : a), lb = TerminalSimulator.lines(b == null ? "" : b);
            List<String> file = new ArrayList<>();
            file.add("--- " + (a == null ? "/dev/null" : "a/" + p));
            file.add("+++ " + (b == null ? "/dev/null" : "b/" + p));
            file.addAll(lineDiff(la, lb, budget));
            if ((long) la.size() * lb.size() <= budget) budget -= la.size() * lb.size();
            for (String line : file) size += line.length() + 1;
            if (size > TerminalSimulator.MAX_OUTPUT) {
                out.add("… (Ausgabe gekürzt)");
                break;
            }
            out.addAll(file);
        }
        return String.join("\n", out);
    }

    /** Vergleich über die längste gemeinsame Teilfolge; übersteigt er das Budget, alles alt (-) und alles neu (+). */
    static List<String> lineDiff(List<String> a, List<String> b, int budget) {
        List<String> out = new ArrayList<>();
        int n = a.size(), m = b.size();
        if ((long) n * m > budget) {
            for (String l : a) out.add("-" + l);
            for (String l : b) out.add("+" + l);
            return out;
        }
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a.get(i).equals(b.get(j)) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                out.add(" " + a.get(i));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                out.add("-" + a.get(i++));
            } else {
                out.add("+" + b.get(j++));
            }
        }
        while (i < n) out.add("-" + a.get(i++));
        while (j < m) out.add("+" + b.get(j++));
        return out;
    }
}

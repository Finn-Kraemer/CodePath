package de.codepath.backend.report;

import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.engine.TaskType;
import de.codepath.backend.identity.Course;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.progress.ProgressService;
import de.codepath.backend.progress.ProgressStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Mitnehm-Paket: Bevor der Kurs endet und die Konten gelöscht werden, laden Schülerinnen und Schüler ihren
 * Code, ihre Ergebnisse, den Berufe-Kompass und Links zum Weiterlernen als PDF herunter. Das PDF entsteht
 * im Speicher und wird direkt ausgeliefert – auf dem Server bleibt davon nichts liegen, auch kein Audit-Eintrag.
 */
@Service
@RequiredArgsConstructor
public class TakeHomeService {

    static final int MAX_CODE_CHARS = 20_000;

    /** Immer dabei, auch wenn kein Modul eigene Links hat */
    static final List<TakeHomeRenderer.Link> GENERAL_LINKS = List.of(
            new TakeHomeRenderer.Link("planet-beruf.de – Berufe entdecken", "https://planet-beruf.de/"),
            new TakeHomeRenderer.Link("BERUFENET der Arbeitsagentur", "https://web.arbeitsagentur.de/berufenet/"));

    private final ReportService reportService;
    private final ProgressService progressService;
    private final UserService userService;
    private final TakeHomeRenderer renderer = new TakeHomeRenderer();

    @Transactional(readOnly = true)
    public byte[] render(User user) {
        return renderer.render(collect(user));
    }

    @Transactional(readOnly = true)
    public TakeHomeRenderer.TakeHome collect(User user) {
        List<ReportService.ModuleScore> scores = reportService.scores(user);
        ReportService.Compass compass = ReportService.compass(scores);
        Map<Long, ProgressStatus> status = progressService.statusByTask(user.getId());
        Map<Long, Long> points = progressService.pointsByTask(user.getId());

        List<TakeHomeRenderer.ModuleBlock> modules = new ArrayList<>();
        List<TakeHomeRenderer.CodeBlock> code = new ArrayList<>();
        for (ReportService.ModuleScore score : scores) {
            List<TakeHomeRenderer.TaskLine> tasks = new ArrayList<>();
            for (PublishedTask task : score.tasks()) {
                ProgressStatus s = status.get(task.taskId());
                tasks.add(new TakeHomeRenderer.TaskLine(task.version().getTitle(), statusLabel(s),
                        Math.clamp(points.getOrDefault(task.taskId(), 0L), 0, task.version().getPoints()), task.version().getPoints()));
                if (s == null) continue;
                progressService.latestAttempt(user.getId(), task.taskId())
                        .flatMap(a -> codeOf(task.type(), a.answer()))
                        .ifPresent(text -> code.add(new TakeHomeRenderer.CodeBlock(score.module().getTitle(), task.version().getTitle(),
                                language(task.type()), text)));
            }
            modules.add(new TakeHomeRenderer.ModuleBlock(score.module().getTitle(), score.achieved(), score.max(), tasks));
        }

        return new TakeHomeRenderer.TakeHome(user.getVisibleName(), userService.courseOf(user.getId()).map(Course::getName).orElse(null),
                LocalDate.now(), compass, modules, code, links(scores, compass));
    }

    /** Links der Module, in denen die Person am stärksten war, zuerst; doppelte nur einmal. */
    static List<TakeHomeRenderer.Link> links(List<ReportService.ModuleScore> scores, ReportService.Compass compass) {
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < compass.entries().size(); i++) rank.put(compass.entries().get(i).moduleSlug(), i);
        List<ReportService.ModuleScore> ordered = new ArrayList<>(scores);
        ordered.sort(Comparator.comparingInt(s -> rank.getOrDefault(s.module().getSlug(), Integer.MAX_VALUE)));
        Map<String, TakeHomeRenderer.Link> links = new LinkedHashMap<>();
        for (ReportService.ModuleScore score : ordered) {
            if (score.module().getLearnMore() == null) continue;
            for (Map<String, String> link : score.module().getLearnMore()) {
                String url = link.get("url");
                if (url == null || !url.matches("https?://\\S+")) continue;
                links.putIfAbsent(url, new TakeHomeRenderer.Link(link.getOrDefault("title", url), url, score.module().getTitle()));
            }
        }
        GENERAL_LINKS.forEach(l -> links.putIfAbsent(l.url(), l));
        return List.copyOf(links.values());
    }

    static String statusLabel(ProgressStatus status) {
        if (status == null) return "Offen";
        return switch (status) {
            case SOLVED -> "Gelöst";
            case PENDING_REVIEW -> "Wartet auf Bewertung";
            case FAILED -> "Nicht geschafft";
            case PRACTICED -> "Geübt";
            case OPEN -> "Begonnen";
        };
    }

    static String language(TaskType type) {
        return switch (type) {
            case SQL -> "SQL";
            case TERMINAL -> "Terminal";
            case PRACTICE -> "Text";
            default -> "Python";
        };
    }

    /** Selbst geschriebener Code oder Text aus der letzten Abgabe – nur für Typen, bei denen es den gibt. */
    static Optional<String> codeOf(TaskType type, Map<String, Object> answer) {
        if (answer == null) return Optional.empty();
        String text = switch (type) {
            case CODE -> string(answer.get("code"));
            case SQL -> string(answer.get("query"));
            case TERMINAL -> answer.get("commands") instanceof List<?> commands
                    ? String.join("\n", commands.stream().map(c -> "$ " + c).toList()) : null;
            case FILL_CODE -> answer.get("answers") instanceof List<?> blanks && !blanks.isEmpty()
                    ? "Deine Lücken:\n" + String.join("\n", indexed(blanks)) : null;
            case PRACTICE -> join(plainText(string(answer.get("content"))), string(answer.get("code")));
            default -> null;
        };
        if (text == null || text.isBlank()) return Optional.empty();
        return Optional.of(text.length() > MAX_CODE_CHARS ? text.substring(0, MAX_CODE_CHARS) + "\n… (gekürzt)" : text);
    }

    private static List<String> indexed(List<?> values) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) lines.add((i + 1) + ") " + values.get(i));
        return lines;
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + "\n\n" + b;
    }

    private static String string(Object value) {
        return value instanceof String s ? s : null;
    }

    /** Praxisabgaben kommen als HTML aus dem Editor; im PDF zählt nur der Text. */
    static String plainText(String html) {
        if (html == null) return null;
        return html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</(p|h[1-6]|li|pre|blockquote)>", "\n")
                .replaceAll("(?i)<li>", "• ").replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&amp;", "&").replaceAll("\n{3,}", "\n\n").strip();
    }
}

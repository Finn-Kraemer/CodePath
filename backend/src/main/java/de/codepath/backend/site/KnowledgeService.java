package de.codepath.backend.site;

import de.codepath.backend.classroom.AccessPolicy;
import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.Module;
import de.codepath.backend.engine.MediaKeys;
import de.codepath.backend.identity.User;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.platform.media.MediaUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Wissensdatenbank: Artikel in Markdown, bearbeitet von der Lehrkraft. Ein Artikel ohne Modul ist für alle
 * Angemeldeten sichtbar, einer mit Modul erst, wenn das Modul für den Kurs sichtbar ist. Nicht
 * veröffentlichte Artikel sieht nur die Lehrkraft.
 */
@Service
@RequiredArgsConstructor
public class KnowledgeService implements MediaUsage {

    static final int MAX_BODY = 200_000;
    static final int MAX_HITS = 20;
    static final int SNIPPET = 180;

    public record ArticleSummary(String slug, String title, String icon, String moduleSlug, boolean published) {
    }

    public record ArticleView(String slug, String title, String icon, String body, String moduleSlug, boolean published) {
    }

    public record ArticleAdmin(Long id, String slug, String title, String icon, String body, String moduleSlug,
                               boolean published, LocalDateTime updatedAt) {
    }

    /** Suchtreffer: Ausschnitt rund um die erste Fundstelle im Text (ohne Markdown-Zeichen). */
    public record SearchHit(String slug, String title, String icon, String snippet) {
    }

    public record ArticleUpdate(String slug, String title, String icon, String body, String moduleSlug, Boolean published) {
    }

    private final KnowledgeArticleRepository repository;
    private final ContentService contentService;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ArticleSummary> visible(User user) {
        return visibleArticles(user).stream()
                .map(a -> new ArticleSummary(a.getSlug(), a.getTitle(), a.getIcon(), a.getModuleSlug(), a.isPublished()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ArticleView article(User user, String slug) {
        KnowledgeArticle article = visibleArticles(user).stream().filter(a -> a.getSlug().equals(slug)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Artikel nicht gefunden oder noch nicht freigegeben."));
        return new ArticleView(article.getSlug(), article.getTitle(), article.getIcon(), article.getBody(),
                article.getModuleSlug(), article.isPublished());
    }

    /**
     * Volltextsuche über Titel und Text der Artikel, die diese Person sehen darf. Alle Suchwörter müssen
     * vorkommen (Groß-/Kleinschreibung egal); Treffer im Titel zählen mehr.
     */
    @Transactional(readOnly = true)
    public List<SearchHit> search(User user, String query) {
        List<String> terms = searchTerms(query);
        if (terms.isEmpty()) return List.of();
        record Scored(KnowledgeArticle article, int score) {
        }
        return visibleArticles(user).stream()
                .map(a -> new Scored(a, score(a, terms)))
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(MAX_HITS)
                .map(s -> new SearchHit(s.article().getSlug(), s.article().getTitle(), s.article().getIcon(),
                        snippet(s.article().getBody(), terms)))
                .toList();
    }

    static List<String> searchTerms(String query) {
        if (query == null) return List.of();
        String trimmed = query.strip();
        if (trimmed.length() > 100) trimmed = trimmed.substring(0, 100);
        return Arrays.stream(trimmed.toLowerCase(Locale.GERMAN).split("\\s+"))
                .filter(t -> t.length() >= 2)
                .distinct()
                .toList();
    }

    /** 0, wenn ein Suchwort fehlt; sonst Titeltreffer ×10 plus Fundstellen im Text. */
    static int score(KnowledgeArticle article, List<String> terms) {
        String title = article.getTitle().toLowerCase(Locale.GERMAN);
        String body = article.getBody().toLowerCase(Locale.GERMAN);
        int score = 0;
        for (String term : terms) {
            int inTitle = title.contains(term) ? 10 : 0;
            int inBody = occurrences(body, term);
            if (inTitle + inBody == 0) return 0;
            score += inTitle + Math.min(inBody, 20);
        }
        return score;
    }

    static String snippet(String body, List<String> terms) {
        String plain = body.replaceAll("```[a-zA-Z]*", " ").replaceAll("[#*`>_|\\[\\]]", " ")
                .replaceAll("\\(https?://[^)]*\\)|\\(/[^)]*\\)", " ").replaceAll("\\s+", " ").strip();
        String lower = plain.toLowerCase(Locale.GERMAN);
        int at = terms.stream().mapToInt(lower::indexOf).filter(i -> i >= 0).min().orElse(-1);
        if (at < 0) return plain.length() > SNIPPET ? plain.substring(0, SNIPPET) + " …" : plain;
        int start = Math.max(0, at - SNIPPET / 3);
        int end = Math.min(plain.length(), start + SNIPPET);
        // An Wortgrenzen schneiden
        int firstSpace = plain.indexOf(' ', start);
        if (start > 0 && firstSpace >= 0 && firstSpace < at) start = firstSpace + 1;
        int space = plain.lastIndexOf(' ', end);
        if (end < plain.length() && space > at) end = space;
        return (start > 0 ? "… " : "") + plain.substring(start, end) + (end < plain.length() ? " …" : "");
    }

    private static int occurrences(String text, String term) {
        int count = 0;
        for (int i = text.indexOf(term); i >= 0; i = text.indexOf(term, i + term.length())) count++;
        return count;
    }

    @Transactional(readOnly = true)
    public List<ArticleAdmin> all() {
        return repository.findAllByOrderByPositionAscIdAsc().stream().map(KnowledgeService::admin).toList();
    }

    @Transactional
    public ArticleAdmin create(String title, String slug) {
        String checked = Slugs.require(slug);
        if (repository.existsBySlug(checked)) throw ApiException.conflict("Es gibt schon einen Artikel mit der Adresse " + checked + ".");
        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(Slugs.title(title));
        article.setSlug(checked);
        article.setIcon("📄");
        article.setBody("# " + article.getTitle() + "\n\n");
        article.setPublished(false);
        article.setPosition(nextPosition());
        repository.save(article);
        repository.unmarkDeleted(checked);
        auditService.log("KNOWLEDGE_CREATED", checked, Map.of());
        return admin(article);
    }

    @Transactional
    public ArticleAdmin update(Long id, ArticleUpdate update) {
        KnowledgeArticle article = get(id);
        if (update.slug() != null && !update.slug().equals(article.getSlug())) {
            String checked = Slugs.require(update.slug());
            if (repository.existsBySlug(checked)) throw ApiException.conflict("Es gibt schon einen Artikel mit der Adresse " + checked + ".");
            article.setSlug(checked);
        }
        if (update.title() != null) article.setTitle(Slugs.title(update.title()));
        if (update.icon() != null) {
            if (update.icon().length() > 16) throw ApiException.badRequest("Das Symbol ist zu lang.");
            article.setIcon(update.icon().strip());
        }
        if (update.body() != null) {
            if (update.body().length() > MAX_BODY) throw ApiException.badRequest("Der Artikel ist zu lang.");
            article.setBody(update.body());
        }
        if (update.moduleSlug() != null) {
            String module = update.moduleSlug().isBlank() ? null : update.moduleSlug();
            if (module != null) contentService.module(module);
            article.setModuleSlug(module);
        }
        if (update.published() != null) article.setPublished(update.published());
        article.setUpdatedAt(LocalDateTime.now());
        auditService.log("KNOWLEDGE_UPDATED", article.getSlug(), Map.of());
        return admin(article);
    }

    /** Endgültig löschen; ein mitgelieferter Artikel kommt beim nächsten Start nicht wieder. */
    @Transactional
    public void delete(Long id) {
        KnowledgeArticle article = get(id);
        repository.delete(article);
        repository.markDeleted(article.getSlug());
        auditService.log("KNOWLEDGE_DELETED", article.getSlug(), Map.of());
    }

    @Transactional
    public void reorder(List<Long> ids) {
        Map<Long, KnowledgeArticle> articles = repository.findAll().stream()
                .collect(Collectors.toMap(KnowledgeArticle::getId, Function.identity()));
        int position = 0;
        for (Long id : ids) {
            KnowledgeArticle article = articles.get(id);
            if (article != null) article.setPosition(position++);
        }
    }

    @Override
    public boolean isPublic(String key) {
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> usedBy(String key) {
        return repository.findAll().stream()
                .filter(a -> MediaKeys.find(a.getBody()).contains(key))
                .findFirst()
                .map(a -> "im Wissensartikel „" + a.getTitle() + "“");
    }

    int nextPosition() {
        return repository.findAllByOrderByPositionAscIdAsc().stream().mapToInt(KnowledgeArticle::getPosition).max().orElse(-1) + 1;
    }

    private List<KnowledgeArticle> visibleArticles(User user) {
        List<KnowledgeArticle> articles = repository.findAllByOrderByPositionAscIdAsc();
        if (user.isTeacher()) return articles;
        AccessPolicy.Releases releases = accessPolicy.releases(user);
        Map<String, Boolean> moduleVisible = new HashMap<>();
        return articles.stream()
                .filter(KnowledgeArticle::isPublished)
                .filter(a -> a.getModuleSlug() == null || moduleVisible.computeIfAbsent(a.getModuleSlug(), slug -> {
                    Optional<Module> module = contentService.modules().stream().filter(m -> m.getSlug().equals(slug)).findFirst();
                    if (module.isEmpty()) return false;
                    AccessPolicy.ModuleAccess access = releases.moduleAccess(module.get(), contentService.publishedTasks(module.get()));
                    return access.visible() && !access.blocked();
                }))
                .toList();
    }

    private KnowledgeArticle get(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Artikel nicht gefunden."));
    }

    private static ArticleAdmin admin(KnowledgeArticle a) {
        return new ArticleAdmin(a.getId(), a.getSlug(), a.getTitle(), a.getIcon(), a.getBody(), a.getModuleSlug(),
                a.isPublished(), a.getUpdatedAt());
    }
}

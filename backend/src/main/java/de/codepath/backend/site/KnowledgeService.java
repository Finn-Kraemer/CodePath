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
import java.util.List;
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

    public record ArticleSummary(String slug, String title, String icon, String moduleSlug, boolean published) {
    }

    public record ArticleView(String slug, String title, String icon, String body, String moduleSlug, boolean published) {
    }

    public record ArticleAdmin(Long id, String slug, String title, String icon, String body, String moduleSlug,
                               boolean published, LocalDateTime updatedAt) {
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
                    return module.isPresent() && releases.moduleAccess(module.get(), contentService.publishedTasks(module.get())).visible();
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

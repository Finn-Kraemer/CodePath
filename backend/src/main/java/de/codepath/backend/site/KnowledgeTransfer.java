package de.codepath.backend.site;

import de.codepath.backend.content.ContentService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.transfer.ItemRunner;
import de.codepath.backend.platform.transfer.TransferItem;
import de.codepath.backend.platform.transfer.TransferSection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Wissensartikel im zentralen Export. Ein Artikel, der an ein Modul gekoppelt ist, braucht dieses Modul –
 * sonst wäre er plötzlich für alle sichtbar. Darum kommen Module beim Import zuerst.
 */
@Component
@RequiredArgsConstructor
public class KnowledgeTransfer implements TransferSection {

    public record ArticleExport(String slug, String title, String icon, String body, String moduleSlug, Boolean published) {
    }

    private final KnowledgeArticleRepository repository;
    private final KnowledgeService knowledgeService;
    private final ContentService contentService;
    private final ItemRunner runner;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return "knowledge";
    }

    @Override
    public String label() {
        return "Wissensdatenbank";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    @Transactional(readOnly = true)
    public JsonNode export(Set<String> only) {
        List<ArticleExport> articles = repository.findAllByOrderByPositionAscIdAsc().stream()
                .filter(a -> only.isEmpty() || only.contains(a.getSlug()))
                .map(a -> new ArticleExport(a.getSlug(), a.getTitle(), a.getIcon(), a.getBody(), a.getModuleSlug(), a.isPublished()))
                .toList();
        return objectMapper.valueToTree(articles);
    }

    @Override
    public List<TransferItem> importData(JsonNode data, Long actorId) {
        if (!data.isArray()) return List.of(TransferItem.error(label(), "-", "Erwartet wird eine Liste von Artikeln."));
        List<TransferItem> items = new ArrayList<>();
        for (JsonNode node : data) {
            String name = node.path("slug").asString("?");
            items.add(runner.run(label(), name, () -> importArticle(objectMapper.treeToValue(node, ArticleExport.class))));
        }
        return items;
    }

    private TransferItem.Action importArticle(ArticleExport in) {
        String slug = Slugs.require(in.slug());
        String title = Slugs.title(in.title());
        String icon = in.icon() == null ? "" : in.icon().strip();
        if (icon.length() > 16) throw ApiException.badRequest("Das Symbol ist zu lang.");
        String body = in.body() == null ? "" : in.body();
        if (body.length() > KnowledgeService.MAX_BODY) throw ApiException.badRequest("Der Artikel ist zu lang.");
        String module = in.moduleSlug() == null || in.moduleSlug().isBlank() ? null : in.moduleSlug();
        if (module != null && contentService.modules().stream().noneMatch(m -> m.getSlug().equals(module))) {
            throw ApiException.badRequest("Das Modul „" + module + "“ fehlt – Artikel nicht übernommen.");
        }

        // Ohne Angabe veröffentlicht, wie mitgelieferte Artikel
        boolean published = in.published() == null || in.published();
        Optional<KnowledgeArticle> existing = repository.findBySlug(slug);
        KnowledgeArticle article = existing.orElseGet(KnowledgeArticle::new);
        if (existing.isPresent() && title.equals(article.getTitle()) && icon.equals(article.getIcon()) && body.equals(article.getBody())
                && Objects.equals(module, article.getModuleSlug()) && published == article.isPublished()) {
            return TransferItem.Action.UNCHANGED;
        }
        if (existing.isEmpty()) {
            article.setSlug(slug);
            article.setPosition(knowledgeService.nextPosition());
            repository.unmarkDeleted(slug);
        }
        article.setTitle(title);
        article.setIcon(icon);
        article.setBody(body);
        article.setModuleSlug(module);
        article.setPublished(published);
        article.setUpdatedAt(LocalDateTime.now());
        repository.save(article);
        return existing.isPresent() ? TransferItem.Action.UPDATED : TransferItem.Action.CREATED;
    }
}

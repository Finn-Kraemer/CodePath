package de.codepath.backend.site;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Legt beim Start die mitgelieferten Wissensartikel (content/wissen/*.md) an – nur, was fehlt und nicht
 * bewusst gelöscht wurde. Unveränderte mitgelieferte Artikel bekommen eine neuere Fassung des Textes;
 * im Browser geänderte Artikel werden nie überschrieben.
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class KnowledgeSeeder implements ApplicationRunner {

    private final KnowledgeArticleRepository repository;
    private final KnowledgeService knowledgeService;

    @Value("${codepath.content.seed-on-start:true}")
    private boolean seedOnStart;

    /** Artikel aus einer Markdown-Datei mit Kopf (title, icon, optional module). */
    record Bundled(String slug, String title, String icon, String moduleSlug, String body) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (!seedOnStart) return;
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:content/wissen/*.md");
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
        Set<String> deleted = repository.deletedSlugs();
        int created = 0;
        int updated = 0;
        int position = knowledgeService.nextPosition();
        for (Resource resource : resources) {
            String slug = resource.getFilename().replaceFirst("\\.md$", "");
            if (deleted.contains(slug)) continue;
            Bundled bundled = parse(slug, resource.getContentAsString(StandardCharsets.UTF_8));
            String hash = hash(bundled.body());
            KnowledgeArticle existing = repository.findBySlug(slug).orElse(null);
            if (existing != null) {
                if (refresh(existing, bundled.body(), hash)) updated++;
                continue;
            }
            KnowledgeArticle article = new KnowledgeArticle();
            article.setSlug(bundled.slug());
            article.setTitle(bundled.title());
            article.setIcon(bundled.icon());
            article.setModuleSlug(bundled.moduleSlug());
            article.setBody(bundled.body());
            article.setSeededHash(hash);
            article.setPosition(position++);
            repository.save(article);
            created++;
        }
        if (created > 0) log.info("Wissensdatenbank: {} mitgelieferte Artikel angelegt", created);
        if (updated > 0) log.info("Wissensdatenbank: {} mitgelieferte Artikel aktualisiert", updated);
    }

    /** Neuer Text nur, wenn der alte noch genau der zuletzt mitgelieferte ist. */
    private boolean refresh(KnowledgeArticle article, String body, String hash) {
        String seeded = article.getSeededHash();
        if (seeded == null || seeded.equals(hash) || !seeded.equals(hash(article.getBody()))) return false;
        article.setBody(body);
        article.setSeededHash(hash);
        article.setUpdatedAt(LocalDateTime.now());
        repository.save(article);
        return true;
    }

    /** Wie md5(body) in PostgreSQL (Migration V20). */
    static String hash(String body) {
        return DigestUtils.md5DigestAsHex(body.getBytes(StandardCharsets.UTF_8));
    }

    static Bundled parse(String slug, String text) {
        Map<String, String> head = new LinkedHashMap<>();
        String body = text.replace("\r\n", "\n");
        if (body.startsWith("---\n")) {
            int end = body.indexOf("\n---", 4);
            if (end > 0) {
                for (String line : body.substring(4, end).split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) head.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
                }
                body = body.substring(body.indexOf('\n', end + 1) + 1);
            }
        }
        return new Bundled(slug, head.getOrDefault("title", slug), head.getOrDefault("icon", ""),
                head.get("module"), body.strip() + "\n");
    }
}

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Legt beim Start die mitgelieferten Wissensartikel (content/wissen/*.md) an – nur, was fehlt und nicht
 * bewusst gelöscht wurde. Im Browser geänderte Artikel werden nie überschrieben.
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
        int position = knowledgeService.nextPosition();
        for (Resource resource : resources) {
            String slug = resource.getFilename().replaceFirst("\\.md$", "");
            if (repository.existsBySlug(slug) || deleted.contains(slug)) continue;
            Bundled bundled = parse(slug, resource.getContentAsString(StandardCharsets.UTF_8));
            KnowledgeArticle article = new KnowledgeArticle();
            article.setSlug(bundled.slug());
            article.setTitle(bundled.title());
            article.setIcon(bundled.icon());
            article.setModuleSlug(bundled.moduleSlug());
            article.setBody(bundled.body());
            article.setPosition(position++);
            repository.save(article);
            created++;
        }
        if (created > 0) log.info("Wissensdatenbank: {} mitgelieferte Artikel angelegt", created);
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

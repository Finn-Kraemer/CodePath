package de.codepath.backend.site;

import de.codepath.backend.engine.MediaKeys;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.platform.media.MediaUsage;
import de.codepath.backend.platform.settings.BlockContent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Eigene Seiten der Lehrkraft unter /seiten/&lt;slug&gt;, aufgebaut aus denselben Blöcken wie die Startseite.
 * Jede Seite hat einen Entwurf und (sobald veröffentlicht) eine Live-Fassung.
 */
@Service
@RequiredArgsConstructor
public class PageService implements MediaUsage {

    public record PageSummary(Long id, String slug, String title, Page.Visibility visibility, boolean showInNav,
                              boolean published, boolean changed, LocalDateTime updatedAt) {
    }

    public record PageDetail(PageSummary page, JsonNode published, JsonNode draft) {
    }

    public record PublicPage(String slug, String title, JsonNode content) {
    }

    public record NavLink(String slug, String title) {
    }

    public record PageSettings(String title, String slug, Page.Visibility visibility, Boolean showInNav) {
    }

    private final PageRepository repository;
    private final BlockContent blockContent;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<PageSummary> all() {
        return repository.findAllByOrderByPositionAscIdAsc().stream().map(PageService::summary).toList();
    }

    @Transactional(readOnly = true)
    public PageDetail detail(Long id) {
        Page page = get(id);
        return new PageDetail(summary(page), blockContent.read(page.getPublished()), blockContent.read(page.getDraft()));
    }

    @Transactional
    public PageSummary create(String title, String slug) {
        Page page = new Page();
        page.setTitle(Slugs.title(title));
        page.setSlug(uniqueSlug(Slugs.require(slug), null));
        page.setPosition(repository.findAllByOrderByPositionAscIdAsc().stream().mapToInt(Page::getPosition).max().orElse(-1) + 1);
        page.setDraft(blockContent.starter(page.getTitle()));
        repository.save(page);
        auditService.log("PAGE_CREATED", page.getSlug(), Map.of());
        return summary(page);
    }

    @Transactional
    public PageSummary updateSettings(Long id, PageSettings settings) {
        Page page = get(id);
        if (settings.title() != null) page.setTitle(Slugs.title(settings.title()));
        if (settings.slug() != null) page.setSlug(uniqueSlug(Slugs.require(settings.slug()), page.getId()));
        if (settings.visibility() != null) page.setVisibility(settings.visibility());
        if (settings.showInNav() != null) page.setShowInNav(settings.showInNav());
        page.setUpdatedAt(LocalDateTime.now());
        return summary(page);
    }

    @Transactional
    public void saveDraft(Long id, JsonNode content) {
        Page page = get(id);
        page.setDraft(blockContent.validate(content));
        page.setUpdatedAt(LocalDateTime.now());
    }

    @Transactional
    public void publish(Long id, JsonNode content) {
        Page page = get(id);
        String value = blockContent.validate(content);
        page.setDraft(value);
        page.setPublished(value);
        page.setUpdatedAt(LocalDateTime.now());
        auditService.log("PAGE_PUBLISHED", page.getSlug(), Map.of());
    }

    /** Nimmt die Seite offline; der Entwurf bleibt. */
    @Transactional
    public void unpublish(Long id) {
        Page page = get(id);
        page.setPublished(null);
        page.setUpdatedAt(LocalDateTime.now());
        auditService.log("PAGE_UNPUBLISHED", page.getSlug(), Map.of());
    }

    @Transactional
    public void delete(Long id) {
        Page page = get(id);
        repository.delete(page);
        auditService.log("PAGE_DELETED", page.getSlug(), Map.of());
    }

    /** Neue Reihenfolge (Menü und Liste) als Liste von IDs. */
    @Transactional
    public void reorder(List<Long> ids) {
        Map<Long, Page> pages = repository.findAll().stream()
                .collect(Collectors.toMap(Page::getId, p -> p));
        int position = 0;
        for (Long id : ids) {
            Page page = pages.get(id);
            if (page != null) page.setPosition(position++);
        }
    }

    /** Veröffentlichte Seite für Besucher; nicht angemeldet nur öffentliche Seiten. */
    @Transactional(readOnly = true)
    public PublicPage published(String slug, boolean loggedIn) {
        Page page = repository.findBySlug(slug)
                .filter(p -> p.getPublished() != null)
                .orElseThrow(() -> ApiException.notFound("Seite nicht gefunden."));
        if (!loggedIn && page.getVisibility() != Page.Visibility.PUBLIC) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Bitte anmelden, um diese Seite zu sehen.");
        }
        return new PublicPage(page.getSlug(), page.getTitle(), blockContent.read(page.getPublished()));
    }

    @Transactional(readOnly = true)
    public List<NavLink> navigation(boolean loggedIn) {
        return repository.findAllByOrderByPositionAscIdAsc().stream()
                .filter(p -> p.isShowInNav() && p.getPublished() != null)
                .filter(p -> loggedIn || p.getVisibility() == Page.Visibility.PUBLIC)
                .map(p -> new NavLink(p.getSlug(), p.getTitle()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPublic(String key) {
        return repository.findAll().stream()
                .anyMatch(p -> p.getVisibility() == Page.Visibility.PUBLIC && p.getPublished() != null
                        && MediaKeys.find(p.getPublished()).contains(key));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> usedBy(String key) {
        return repository.findAll().stream()
                .filter(p -> MediaKeys.find(p.getDraft()).contains(key)
                        || (p.getPublished() != null && MediaKeys.find(p.getPublished()).contains(key)))
                .findFirst()
                .map(p -> "auf der Seite „" + p.getTitle() + "“");
    }

    private Page get(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Seite nicht gefunden."));
    }

    private String uniqueSlug(String slug, Long ownId) {
        repository.findBySlug(slug).filter(p -> !p.getId().equals(ownId)).ifPresent(p -> {
            throw ApiException.conflict("Die Adresse /seiten/" + slug + " ist schon vergeben.");
        });
        return slug;
    }

    private static PageSummary summary(Page page) {
        return new PageSummary(page.getId(), page.getSlug(), page.getTitle(), page.getVisibility(), page.isShowInNav(),
                page.getPublished() != null, !page.getDraft().equals(page.getPublished()), page.getUpdatedAt());
    }
}

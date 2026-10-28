package de.codepath.backend.site;

import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.settings.BlockContent;
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

/** Eigene Seiten im zentralen Export; beim Import entscheidet der Slug, ob eine Seite neu ist. */
@Component
@RequiredArgsConstructor
public class PageTransfer implements TransferSection {

    public record PageExport(String slug, String title, String description, Page.Visibility visibility, Boolean showInNav,
                             JsonNode draft, JsonNode published) {
    }

    private final PageRepository repository;
    private final BlockContent blockContent;
    private final ItemRunner runner;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return "pages";
    }

    @Override
    public String label() {
        return "Seiten";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    @Transactional(readOnly = true)
    public JsonNode export(Set<String> only) {
        List<PageExport> pages = repository.findAllByOrderByPositionAscIdAsc().stream()
                .filter(p -> only.isEmpty() || only.contains(p.getSlug()))
                .map(p -> new PageExport(p.getSlug(), p.getTitle(), p.getDescription(), p.getVisibility(), p.isShowInNav(),
                        blockContent.read(p.getDraft()), blockContent.read(p.getPublished())))
                .toList();
        return objectMapper.valueToTree(pages);
    }

    @Override
    public List<TransferItem> importData(JsonNode data, Long actorId) {
        if (!data.isArray()) return List.of(TransferItem.error(label(), "-", "Erwartet wird eine Liste von Seiten."));
        List<TransferItem> items = new ArrayList<>();
        for (JsonNode node : data) {
            String name = node.path("slug").asString("?");
            items.add(runner.run(label(), name, () -> importPage(objectMapper.treeToValue(node, PageExport.class))));
        }
        return items;
    }

    private TransferItem.Action importPage(PageExport in) {
        String slug = Slugs.require(in.slug());
        String title = Slugs.title(in.title());
        String description = in.description() == null ? "" : in.description().strip();
        if (description.length() > 300) throw ApiException.badRequest("Die Kurzbeschreibung ist zu lang (höchstens 300 Zeichen).");
        if (in.draft() == null && in.published() == null) throw ApiException.badRequest("Die Seite hat keinen Inhalt.");
        String published = in.published() == null || in.published().isNull() ? null : blockContent.validate(in.published());
        String draft = in.draft() == null || in.draft().isNull() ? published : blockContent.validate(in.draft());
        Page.Visibility visibility = in.visibility() != null ? in.visibility() : Page.Visibility.LOGGED_IN;
        boolean showInNav = Boolean.TRUE.equals(in.showInNav());

        Optional<Page> existing = repository.findBySlug(slug);
        Page page = existing.orElseGet(Page::new);
        if (existing.isPresent() && title.equals(page.getTitle()) && description.equals(page.getDescription())
                && visibility == page.getVisibility() && showInNav == page.isShowInNav()
                && Objects.equals(blockContent.read(page.getDraft()), blockContent.read(draft))
                && Objects.equals(blockContent.read(page.getPublished()), blockContent.read(published))) {
            return TransferItem.Action.UNCHANGED;
        }
        if (existing.isEmpty()) {
            page.setSlug(slug);
            page.setPosition(repository.findAllByOrderByPositionAscIdAsc().stream().mapToInt(Page::getPosition).max().orElse(-1) + 1);
        }
        page.setTitle(title);
        page.setDescription(description);
        page.setVisibility(visibility);
        page.setShowInNav(showInNav);
        page.setDraft(draft);
        page.setPublished(published);
        page.setUpdatedAt(LocalDateTime.now());
        repository.save(page);
        return existing.isPresent() ? TransferItem.Action.UPDATED : TransferItem.Action.CREATED;
    }
}

package de.codepath.backend.site;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** Eigene Seiten: lesen je nach Sichtbarkeit (auch ohne Anmeldung), verwalten nur für die Lehrkraft. */
@RestController
@RequiredArgsConstructor
public class PageController {

    private final PageService pageService;

    @GetMapping("/api/public/pages")
    public List<PageService.NavLink> navigation(Authentication authentication) {
        return pageService.navigation(loggedIn(authentication));
    }

    @GetMapping("/api/public/pages/{slug}")
    public PageService.PublicPage page(@PathVariable("slug") String slug, Authentication authentication) {
        return pageService.published(slug, loggedIn(authentication));
    }

    @GetMapping("/api/admin/pages")
    public List<PageService.PageSummary> all() {
        return pageService.all();
    }

    @PostMapping("/api/admin/pages")
    public PageService.PageSummary create(@RequestBody Map<String, String> body) {
        return pageService.create(body.get("title"), body.get("slug"));
    }

    @PutMapping("/api/admin/pages/order")
    public ResponseEntity<Void> reorder(@RequestBody List<Long> ids) {
        pageService.reorder(ids);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/pages/{id}")
    public PageService.PageDetail detail(@PathVariable("id") Long id) {
        return pageService.detail(id);
    }

    @PutMapping("/api/admin/pages/{id}")
    public PageService.PageSummary settings(@PathVariable("id") Long id, @RequestBody PageService.PageSettings settings) {
        return pageService.updateSettings(id, settings);
    }

    @PutMapping("/api/admin/pages/{id}/draft")
    public ResponseEntity<Void> saveDraft(@PathVariable("id") Long id, @RequestBody JsonNode content) {
        pageService.saveDraft(id, content);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/pages/{id}/published")
    public ResponseEntity<Void> publish(@PathVariable("id") Long id, @RequestBody JsonNode content) {
        pageService.publish(id, content);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/pages/{id}/published")
    public ResponseEntity<Void> unpublish(@PathVariable("id") Long id) {
        pageService.unpublish(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/pages/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        pageService.delete(id);
        return ResponseEntity.noContent().build();
    }

    static boolean loggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}

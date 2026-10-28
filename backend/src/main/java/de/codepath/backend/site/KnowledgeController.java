package de.codepath.backend.site;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final UserService userService;

    @GetMapping("/api/knowledge")
    public List<KnowledgeService.ArticleSummary> list() {
        return knowledgeService.visible(userService.currentUser());
    }

    @GetMapping("/api/knowledge/{slug}")
    public KnowledgeService.ArticleView article(@PathVariable("slug") String slug) {
        return knowledgeService.article(userService.currentUser(), slug);
    }

    @GetMapping("/api/admin/knowledge")
    public List<KnowledgeService.ArticleAdmin> all() {
        return knowledgeService.all();
    }

    @PostMapping("/api/admin/knowledge")
    public KnowledgeService.ArticleAdmin create(@RequestBody Map<String, String> body) {
        return knowledgeService.create(body.get("title"), body.get("slug"));
    }

    @PutMapping("/api/admin/knowledge/order")
    public ResponseEntity<Void> reorder(@RequestBody List<Long> ids) {
        knowledgeService.reorder(ids);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/knowledge/{id}")
    public KnowledgeService.ArticleAdmin update(@PathVariable("id") Long id, @RequestBody KnowledgeService.ArticleUpdate update) {
        return knowledgeService.update(id, update);
    }

    @DeleteMapping("/api/admin/knowledge/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        knowledgeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

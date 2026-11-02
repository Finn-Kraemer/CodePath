package de.codepath.backend.pathway;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CodePathController {

    private final CodePathService service;
    private final UserService userService;

    @GetMapping("/api/codepaths")
    public List<CodePathService.PathLink> published() {
        return service.published(userService.currentUser());
    }

    @GetMapping("/api/codepaths/{slug}")
    public CodePathService.PathView view(@PathVariable("slug") String slug) {
        return service.view(userService.currentUser(), slug);
    }

    /** Empfohlene nächste Aufgabe; leere Antwort, wenn nichts mehr offen ist. */
    @GetMapping("/api/learning/next")
    public ResponseEntity<CodePathService.Recommendation> next() {
        CodePathService.Recommendation next = service.next(userService.currentUser());
        return next == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(next);
    }

    @GetMapping("/api/admin/codepaths")
    public List<CodePathService.PathSummary> all() {
        return service.all();
    }

    @PostMapping("/api/admin/codepaths")
    public CodePathService.PathAdmin create(@RequestBody Map<String, String> body) {
        return service.create(body.get("title"), body.get("slug"));
    }

    @PutMapping("/api/admin/codepaths/order")
    public ResponseEntity<Void> reorder(@RequestBody List<Long> ids) {
        service.reorder(ids);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/codepaths/{id}")
    public CodePathService.PathAdmin get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PutMapping("/api/admin/codepaths/{id}")
    public CodePathService.PathAdmin update(@PathVariable("id") Long id, @RequestBody CodePathService.PathUpdate update) {
        return service.update(id, update);
    }

    @DeleteMapping("/api/admin/codepaths/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

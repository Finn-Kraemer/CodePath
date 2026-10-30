package de.codepath.backend.pathway;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LearningPathController {

    private final LearningPathService service;
    private final UserService userService;

    @GetMapping("/api/learning-paths")
    public List<LearningPathService.PathLink> published() {
        return service.published(userService.currentUser());
    }

    @GetMapping("/api/learning-paths/{slug}")
    public LearningPathService.PathView view(@PathVariable("slug") String slug,
                                             @RequestParam(name = "student", required = false) String student) {
        return service.view(userService.currentUser(), slug, student);
    }

    /** Empfohlene nächste Aufgabe; leere Antwort, wenn nichts mehr offen ist. */
    @GetMapping("/api/learning/next")
    public ResponseEntity<LearningPathService.Recommendation> next() {
        LearningPathService.Recommendation next = service.next(userService.currentUser());
        return next == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(next);
    }

    @GetMapping("/api/admin/learning-paths")
    public List<LearningPathService.PathSummary> all() {
        return service.all();
    }

    @PostMapping("/api/admin/learning-paths")
    public LearningPathService.PathAdmin create(@RequestBody Map<String, String> body) {
        return service.create(body.get("title"), body.get("slug"));
    }

    @PutMapping("/api/admin/learning-paths/order")
    public ResponseEntity<Void> reorder(@RequestBody List<Long> ids) {
        service.reorder(ids);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/learning-paths/{id}")
    public LearningPathService.PathAdmin get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PutMapping("/api/admin/learning-paths/{id}")
    public LearningPathService.PathAdmin update(@PathVariable("id") Long id, @RequestBody LearningPathService.PathUpdate update) {
        return service.update(id, update);
    }

    @DeleteMapping("/api/admin/learning-paths/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

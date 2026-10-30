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

    @PostMapping("/api/learning-paths/{slug}/choices")
    public LearningPathService.PathView choose(@PathVariable("slug") String slug,
                                               @RequestBody LearningPathService.ChoiceRequest request) {
        return service.choose(userService.currentUser(), slug, request.nodeId(), request.to());
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

    @GetMapping("/api/admin/learning-paths/{id}/choice-stats")
    public List<LearningPathService.ChoiceNodeStats> choiceStats(@PathVariable("id") Long id,
                                                                 @RequestParam(name = "courseId", required = false) Long courseId) {
        return service.choiceStats(id, courseId);
    }

    @PostMapping("/api/admin/learning-paths/{id}/course-actions")
    public Map<String, Integer> courseAction(@PathVariable("id") Long id,
                                             @RequestBody LearningPathService.CoursePathActionRequest request) {
        int affected = service.courseAction(userService.currentUser(), id, request);
        return Map.of("affected", affected);
    }

    @GetMapping("/api/admin/students/{username}/learning-path")
    public LearningPathService.StudentPathAdminView studentPath(@PathVariable("username") String username,
                                                                @RequestParam(name = "slug", required = false) String slug) {
        return service.studentPathView(userService.currentUser(), username, slug);
    }

    @PostMapping("/api/admin/students/{username}/learning-paths/{slug}/choices/reset")
    public ResponseEntity<Void> resetChoice(@PathVariable("username") String username,
                                            @PathVariable("slug") String slug,
                                            @RequestBody LearningPathService.ChoiceResetRequest request) {
        service.resetChoice(userService.currentUser(), username, slug, request.nodeId(), Boolean.TRUE.equals(request.resetProgress()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/students/{username}/learning-paths/{slug}/choices/set")
    public ResponseEntity<Void> setChoice(@PathVariable("username") String username,
                                          @PathVariable("slug") String slug,
                                          @RequestBody LearningPathService.ChoiceRequest request) {
        service.setChoiceForStudent(userService.currentUser(), username, slug, request.nodeId(), request.to());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/students/{username}/learning-paths/{slug}/branches/unlock")
    public ResponseEntity<Void> unlockBranch(@PathVariable("username") String username,
                                             @PathVariable("slug") String slug,
                                             @RequestBody LearningPathService.ChoiceRequest request) {
        service.unlockBranchForStudent(userService.currentUser(), username, slug, request.nodeId(), request.to());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/students/{username}/learning-paths/{slug}/nodes/unlock")
    public ResponseEntity<Void> unlockNode(@PathVariable("username") String username,
                                           @PathVariable("slug") String slug,
                                           @RequestBody LearningPathService.NodeUnlockRequest request) {
        service.unlockNodeForStudent(userService.currentUser(), username, slug, request.nodeId());
        return ResponseEntity.noContent().build();
    }
}

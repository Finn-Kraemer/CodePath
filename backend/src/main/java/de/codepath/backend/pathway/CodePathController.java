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
    public CodePathService.PathView view(@PathVariable("slug") String slug,
                                             @RequestParam(name = "student", required = false) String student) {
        return service.view(userService.currentUser(), slug, student);
    }

    @PostMapping("/api/codepaths/{slug}/choices")
    public CodePathService.PathView choose(@PathVariable("slug") String slug,
                                               @RequestBody CodePathService.ChoiceRequest request) {
        return service.choose(userService.currentUser(), slug, request.nodeId(), request.to());
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

    @GetMapping("/api/admin/codepaths/{id}/choice-stats")
    public List<CodePathService.ChoiceNodeStats> choiceStats(@PathVariable("id") Long id,
                                                                 @RequestParam(name = "courseId", required = false) Long courseId) {
        return service.choiceStats(id, courseId);
    }

    @GetMapping("/api/admin/codepaths/{id}/branch-stats")
    public List<CodePathService.BranchConditionStats> branchStats(@PathVariable("id") Long id,
                                                                      @RequestParam(name = "courseId", required = false) Long courseId) {
        return service.branchStats(id, courseId);
    }

    @GetMapping("/api/admin/codepaths/{id}/gate-stats")
    public List<CodePathService.GateStudentStat> gateStats(@PathVariable("id") Long id,
                                                              @RequestParam(name = "courseId", required = false) Long courseId) {
        return service.gateStats(id, courseId);
    }

    @PostMapping("/api/admin/codepaths/{id}/course-actions")
    public Map<String, Integer> courseAction(@PathVariable("id") Long id,
                                             @RequestBody CodePathService.CoursePathActionRequest request) {
        int affected = service.courseAction(userService.currentUser(), id, request);
        return Map.of("affected", affected);
    }

    @GetMapping("/api/admin/students/{username}/codepath")
    public CodePathService.StudentPathAdminView studentPath(@PathVariable("username") String username,
                                                                @RequestParam(name = "slug", required = false) String slug) {
        return service.studentPathView(userService.currentUser(), username, slug);
    }

    @PostMapping("/api/admin/students/{username}/codepaths/{slug}/choices/reset")
    public ResponseEntity<Void> resetChoice(@PathVariable("username") String username,
                                            @PathVariable("slug") String slug,
                                            @RequestBody CodePathService.ChoiceResetRequest request) {
        service.resetChoice(userService.currentUser(), username, slug, request.nodeId(), Boolean.TRUE.equals(request.resetProgress()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/students/{username}/codepaths/{slug}/conditions/reset")
    public ResponseEntity<Void> resetBranchCondition(@PathVariable("username") String username,
                                                     @PathVariable("slug") String slug,
                                                     @RequestParam(name = "edgeKey", required = false) String edgeKey) {
        service.resetBranchCondition(userService.currentUser(), username, slug, edgeKey);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/students/{username}/codepaths/{slug}/choices/set")
    public ResponseEntity<Void> setChoice(@PathVariable("username") String username,
                                          @PathVariable("slug") String slug,
                                          @RequestBody CodePathService.ChoiceRequest request) {
        service.setChoiceForStudent(userService.currentUser(), username, slug, request.nodeId(), request.to());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/students/{username}/codepaths/{slug}/branches/unlock")
    public ResponseEntity<Void> unlockBranch(@PathVariable("username") String username,
                                             @PathVariable("slug") String slug,
                                             @RequestBody CodePathService.ChoiceRequest request) {
        service.unlockBranchForStudent(userService.currentUser(), username, slug, request.nodeId(), request.to());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/students/{username}/codepaths/{slug}/nodes/unlock")
    public ResponseEntity<Void> unlockNode(@PathVariable("username") String username,
                                           @PathVariable("slug") String slug,
                                           @RequestBody CodePathService.NodeUnlockRequest request) {
        service.unlockNodeForStudent(userService.currentUser(), username, slug, request.nodeId());
        return ResponseEntity.noContent().build();
    }
}

package de.codepath.backend.review;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final UserService userService;

    public record ApproveRequest(Map<String, Integer> scores, Boolean halfPoints, String comment) {
    }

    public record RejectRequest(String comment, boolean lockTask) {
    }

    @GetMapping("/submissions")
    public List<ReviewService.SubmissionSummary> pending() {
        return reviewService.pending();
    }

    @GetMapping("/submissions/{id}")
    public ReviewService.SubmissionDetail detail(@PathVariable("id") Long id) {
        return reviewService.detail(id);
    }

    @PutMapping("/submissions/{id}/approve")
    public ReviewService.SubmissionDetail approve(@PathVariable("id") Long id, @RequestBody(required = false) ApproveRequest request) {
        ApproveRequest r = request != null ? request : new ApproveRequest(null, false, null);
        return reviewService.approve(id, r.scores(), r.halfPoints(), r.comment(), userService.currentUser());
    }

    @PutMapping("/submissions/{id}/reject")
    public ReviewService.SubmissionDetail reject(@PathVariable("id") Long id, @RequestBody(required = false) RejectRequest request) {
        RejectRequest r = request != null ? request : new RejectRequest(null, false);
        return reviewService.reject(id, r.comment(), r.lockTask(), userService.currentUser());
    }

    @GetMapping("/students/{username}/submissions")
    public List<ReviewService.SubmissionSummary> forStudent(@PathVariable("username") String username) {
        return reviewService.forStudent(username);
    }
}

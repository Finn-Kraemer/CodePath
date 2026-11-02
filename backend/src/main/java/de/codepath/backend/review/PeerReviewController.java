package de.codepath.backend.review;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Peer-Review: Schüler unter /api/peer-reviews, Lehrkraft sieht die Bewertungen je Abgabe. */
@RestController
@RequiredArgsConstructor
public class PeerReviewController {

    private final PeerReviewService peerReviewService;
    private final UserService userService;

    @GetMapping("/api/peer-reviews/{moduleSlug}/{taskSlug}")
    public PeerReviewService.PeerState state(@PathVariable("moduleSlug") String moduleSlug, @PathVariable("taskSlug") String taskSlug) {
        return peerReviewService.state(userService.currentUser(), moduleSlug, taskSlug);
    }

    @PostMapping("/api/peer-reviews/{moduleSlug}/{taskSlug}/assign")
    public PeerReviewService.PeerState assign(@PathVariable("moduleSlug") String moduleSlug, @PathVariable("taskSlug") String taskSlug) {
        return peerReviewService.assign(userService.currentUser(), moduleSlug, taskSlug);
    }

    @PutMapping("/api/peer-reviews/assignments/{id}")
    public PeerReviewService.PeerState submit(@PathVariable("id") Long id, @RequestBody PeerReviewService.ScoreRequest request) {
        return peerReviewService.submit(userService.currentUser(), id, request);
    }

    @GetMapping("/api/admin/submissions/{id}/peer-reviews")
    public List<PeerReviewService.TeacherPeerReview> forSubmission(@PathVariable("id") Long id) {
        return peerReviewService.forSubmission(id);
    }
}

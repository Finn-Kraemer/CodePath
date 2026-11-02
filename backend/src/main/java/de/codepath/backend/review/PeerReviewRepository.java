package de.codepath.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface PeerReviewRepository extends JpaRepository<PeerReview, Long> {

    List<PeerReview> findByReviewerIdAndTaskIdOrderByIdAsc(Long reviewerId, Long taskId);

    List<PeerReview> findByPracticeReviewIdIn(Collection<Long> practiceReviewIds);

    List<PeerReview> findByPracticeReviewId(Long practiceReviewId);
}

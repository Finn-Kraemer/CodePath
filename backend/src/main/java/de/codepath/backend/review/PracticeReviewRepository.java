package de.codepath.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PracticeReviewRepository extends JpaRepository<PracticeReview, Long> {

    List<PracticeReview> findByStatusOrderByIdAsc(ReviewStatus status);

    List<PracticeReview> findByAttemptIdInOrderByIdDesc(Collection<Long> attemptIds);

    Optional<PracticeReview> findByAttemptId(Long attemptId);
}

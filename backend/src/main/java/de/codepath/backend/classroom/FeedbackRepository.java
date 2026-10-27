package de.codepath.backend.classroom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    boolean existsByUserId(Long userId);

    List<Feedback> findAllByOrderBySubmittedAtDesc();
}

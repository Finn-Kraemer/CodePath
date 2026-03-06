package de.codepath.backend.features.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneralFeedbackRepository extends JpaRepository<GeneralFeedback, Long> {
    boolean existsByUserId(Long userId);
}

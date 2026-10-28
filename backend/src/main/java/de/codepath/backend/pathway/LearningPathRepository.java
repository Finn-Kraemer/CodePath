package de.codepath.backend.pathway;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {

    Optional<LearningPath> findBySlug(String slug);

    List<LearningPath> findAllByOrderByPositionAscIdAsc();
}

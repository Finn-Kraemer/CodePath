package de.codepath.backend.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationTemplateRepository extends JpaRepository<EvaluationTemplate, Long> {

    List<EvaluationTemplate> findAllByOrderByKindAscNameAsc();

    Optional<EvaluationTemplate> findBySlug(String slug);

    boolean existsBySlug(String slug);
}

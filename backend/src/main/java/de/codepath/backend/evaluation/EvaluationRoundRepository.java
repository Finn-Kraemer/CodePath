package de.codepath.backend.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EvaluationRoundRepository extends JpaRepository<EvaluationRound, Long> {

    List<EvaluationRound> findAllByOrderByCreatedAtDesc();

    List<EvaluationRound> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    List<EvaluationRound> findByCourseIdAndStatusInOrderByCreatedAtDesc(Long courseId, Collection<RoundStatus> statuses);
}

package de.codepath.backend.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationResponseRepository extends JpaRepository<EvaluationResponse, Long> {

    List<EvaluationResponse> findByRoundId(Long roundId);

    List<EvaluationResponse> findByRoundIdAndSubjectId(Long roundId, Long subjectId);

    Optional<EvaluationResponse> findByRoundIdAndPartAndSubjectId(Long roundId, EvaluationPart part, Long subjectId);

    long countByRoundIdAndPart(Long roundId, EvaluationPart part);
}

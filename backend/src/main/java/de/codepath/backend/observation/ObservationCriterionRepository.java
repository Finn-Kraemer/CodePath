package de.codepath.backend.observation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObservationCriterionRepository extends JpaRepository<ObservationCriterion, Long> {

    List<ObservationCriterion> findByBoardIdOrderByPositionAscIdAsc(Long boardId);

    long countByBoardId(Long boardId);
}

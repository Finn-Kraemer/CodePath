package de.codepath.backend.observation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ObservationBoardRepository extends JpaRepository<ObservationBoard, Long> {

    Optional<ObservationBoard> findByCourseId(Long courseId);

    List<ObservationBoard> findByCourseIdIsNullOrderByNameAscIdAsc();

    List<ObservationBoard> findByCourseIdIsNotNull();

    Optional<ObservationBoard> findFirstByCourseIdIsNullAndNameOrderByIdAsc(String name);

    void deleteByCourseId(Long courseId);
}

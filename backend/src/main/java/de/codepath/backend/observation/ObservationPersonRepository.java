package de.codepath.backend.observation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ObservationPersonRepository extends JpaRepository<ObservationPerson, Long> {

    List<ObservationPerson> findByBoardId(Long boardId);

    Optional<ObservationPerson> findByBoardIdAndSeatId(Long boardId, String seatId);

    long countByBoardIdAndUserIdIsNull(Long boardId);
}

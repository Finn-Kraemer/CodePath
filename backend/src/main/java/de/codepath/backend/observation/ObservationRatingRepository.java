package de.codepath.backend.observation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ObservationRatingRepository extends JpaRepository<ObservationRating, ObservationRating.Key> {

    List<ObservationRating> findByPersonIdIn(Collection<Long> personIds);

    List<ObservationRating> findByPersonId(Long personId);

    List<ObservationRating> findByCriterionId(Long criterionId);
}

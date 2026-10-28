package de.codepath.backend.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByJoinCodeIgnoreCase(String joinCode);

    boolean existsByJoinCode(String joinCode);

    List<Course> findAllByOrderByCreatedAtDesc();
}

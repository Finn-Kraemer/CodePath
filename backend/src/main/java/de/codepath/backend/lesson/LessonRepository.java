package de.codepath.backend.lesson;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findAllByOrderByPositionAscIdAsc();

    Optional<Lesson> findFirstByTitleAndCourseIdIsNullOrderByIdAsc(String title);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lesson l where l.id = :id")
    Optional<Lesson> findByIdForUpdate(Long id);
}

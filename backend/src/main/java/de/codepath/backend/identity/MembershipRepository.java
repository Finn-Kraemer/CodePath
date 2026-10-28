package de.codepath.backend.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @Query("select m.userId from Membership m where m.course.id = :courseId")
    List<Long> findUserIdsByCourseId(Long courseId);

    long countByCourseId(Long courseId);
}

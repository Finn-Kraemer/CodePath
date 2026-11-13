package de.codepath.backend.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @Query("select m.userId from Membership m where m.course.id = :courseId")
    List<Long> findUserIdsByCourseId(Long courseId);

    long countByCourseId(Long courseId);

    @Modifying
    @Query(value = "update memberships set course_id = :target where course_id = :source", nativeQuery = true)
    int moveAll(Long source, Long target);
}

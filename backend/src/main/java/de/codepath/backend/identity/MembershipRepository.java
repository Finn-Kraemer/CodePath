package de.codepath.backend.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @Query("select m.userId from Membership m where m.course.id = :courseId")
    List<Long> findUserIdsByCourseId(Long courseId);

    long countByCourseId(Long courseId);

    @Query("select m.course.id from Membership m where m.userId = :userId")
    Optional<Long> findCourseIdByUserId(Long userId);

    @Query("select u.username from User u, Membership m where m.userId = u.id and m.course.id in :courseIds")
    List<String> findUsernamesByCourseIds(Collection<Long> courseIds);
}

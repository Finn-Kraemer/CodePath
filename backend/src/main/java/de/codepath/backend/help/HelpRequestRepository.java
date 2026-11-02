package de.codepath.backend.help;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface HelpRequestRepository extends JpaRepository<HelpRequest, Long> {

    Optional<HelpRequest> findFirstByUserIdAndStatusIn(Long userId, Collection<HelpRequest.Status> statuses);

    List<HelpRequest> findByStatusInOrderByCreatedAtAsc(Collection<HelpRequest.Status> statuses);

    List<HelpRequest> findByStatusInAndResolvedAtAfterOrderByResolvedAtDesc(Collection<HelpRequest.Status> statuses, LocalDateTime after);

    @Modifying
    @Query("update HelpRequest h set h.draft = null, h.lastOutput = null where h.status in :statuses and h.resolvedAt < :before and (h.draft is not null or h.lastOutput is not null)")
    int clearDrafts(Collection<HelpRequest.Status> statuses, LocalDateTime before);

    /** Je Aufgabe: Zahl der Rufe, davon selbst gelöst, Summe der Wartezeit in Sekunden (nur erledigte) */
    @Query(value = """
            select task_id, count(*),
                   count(*) filter (where resolution = 'SELF_SOLVED'),
                   coalesce(avg(extract(epoch from (resolved_at - created_at))) filter (where resolution = 'TEACHER'), 0)
            from help_requests group by task_id""", nativeQuery = true)
    List<Object[]> statisticsByTask();

    @Modifying
    @Query("delete from HelpRequest h where h.courseId = :courseId or h.userId in :userIds")
    int deleteByCourseOrUsers(Long courseId, Collection<Long> userIds);
}

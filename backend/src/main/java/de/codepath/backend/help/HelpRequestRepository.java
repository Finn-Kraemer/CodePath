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

    @Modifying
    @Query("delete from HelpRequest h where h.courseId = :courseId or h.userId in :userIds")
    int deleteByCourseOrUsers(Long courseId, Collection<Long> userIds);
}

package de.codepath.backend.progress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ProgressRepository extends JpaRepository<Progress, Progress.Key> {

    List<Progress> findByUserId(Long userId);

    List<Progress> findByUserIdAndTaskIdIn(Long userId, Collection<Long> taskIds);

    List<Progress> findByTaskId(Long taskId);

    @Query("select p.userId, count(p) from Progress p where p.status = de.codepath.backend.progress.ProgressStatus.SOLVED and p.userId in :userIds group by p.userId")
    List<Object[]> countSolvedByUser(Collection<Long> userIds);

    long countByUserIdAndStatus(Long userId, ProgressStatus status);
}

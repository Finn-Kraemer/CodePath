package de.codepath.backend.progress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface PointsEntryRepository extends JpaRepository<PointsEntry, Long> {

    /**
     * Bucht Punkte; der eindeutige Index auf (user_id, task_id) für SOLVED/PARTIAL/REVIEW sorgt dafür,
     * dass es für das Lösen einer Aufgabe genau einmal Punkte gibt.
     *
     * @return 1 = gebucht, 0 = gab es schon
     */
    @Modifying
    @Query(value = """
            insert into points_entries (user_id, task_id, amount, reason, note, created_at, created_by)
            values (:userId, :taskId, :amount, :reason, :note, now(), :createdBy)
            on conflict do nothing""", nativeQuery = true)
    int insertOnce(Long userId, Long taskId, int amount, String reason, String note, Long createdBy);

    @Modifying
    @Query(value = """
            insert into points_entries (user_id, task_id, amount, reason, note, created_at, created_by)
            values (:userId, :taskId, :amount, 'CORRECTION', :note, now(), :createdBy)""", nativeQuery = true)
    void insertCorrection(Long userId, Long taskId, int amount, String note, Long createdBy);

    @Modifying
    @Query(value = """
            insert into points_entries (user_id, path_id, node_id, amount, reason, note, created_at, cancelled)
            values (:userId, :pathId, :nodeId, :amount, 'PATH_BONUS', :note, now(), false)
            on conflict do nothing""", nativeQuery = true)
    int insertBonusOnce(Long userId, Long pathId, String nodeId, int amount, String note);

    @Query("""
            select p from PointsEntry p
            where p.userId = :userId
              and p.pathId = :pathId
              and p.nodeId = :nodeId
              and p.reason = de.codepath.backend.progress.PointsReason.PATH_BONUS
              and p.cancelled = false""")
    java.util.Optional<PointsEntry> findActiveBonus(Long userId, Long pathId, String nodeId);

    @Query("""
            select p from PointsEntry p
            where p.userId = :userId
              and p.pathId = :pathId
              and p.reason = de.codepath.backend.progress.PointsReason.PATH_BONUS
              and p.cancelled = false""")
    List<PointsEntry> findActiveBonuses(Long userId, Long pathId);

    @Modifying
    @Query(value = "update points_entries set cancelled = true where id = :id", nativeQuery = true)
    void markBonusCancelled(Long id);

    @Modifying
    @Query(value = """
            insert into points_entries (user_id, path_id, node_id, amount, reason, note, created_at, created_by)
            values (:userId, :pathId, :nodeId, :amount, 'CORRECTION', :note, now(), :createdBy)""", nativeQuery = true)
    void insertBonusCancellation(Long userId, Long pathId, String nodeId, int amount, String note, Long createdBy);

    @Query("select coalesce(sum(p.amount), 0) from PointsEntry p where p.userId = :userId")
    long total(Long userId);

    @Query("select p.userId, sum(p.amount) from PointsEntry p where p.userId in :userIds group by p.userId")
    List<Object[]> totals(Collection<Long> userIds);

    @Query("select p.taskId, sum(p.amount) from PointsEntry p where p.userId = :userId and p.taskId is not null group by p.taskId")
    List<Object[]> byTask(Long userId);

    @Query("select coalesce(sum(p.amount), 0) from PointsEntry p where p.userId = :userId and p.taskId = :taskId")
    long forTask(Long userId, Long taskId);

    boolean existsByUserIdAndTaskIdAndReasonIn(Long userId, Long taskId, Collection<PointsReason> reasons);

    List<PointsEntry> findByUserIdOrderByCreatedAtDesc(Long userId);
}

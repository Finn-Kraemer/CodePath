package de.codepath.backend.pathway;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface LearningPathBranchResultRepository extends JpaRepository<LearningPathBranchResult, Long> {
    List<LearningPathBranchResult> findByUserIdAndPathId(Long userId, Long pathId);
    List<LearningPathBranchResult> findByPathId(Long pathId);
    List<LearningPathBranchResult> findByPathIdIn(Collection<Long> pathIds);
    void deleteByUserIdAndPathIdAndEdgeKey(Long userId, Long pathId, String edgeKey);
    void deleteByUserIdAndPathId(Long userId, Long pathId);
    void deleteByPathId(Long pathId);
    void deleteByUserIdIn(Collection<Long> userIds);

    /** Einmal entschieden, bleibt es dabei: parallele Auswertungen schreiben kein zweites Ergebnis. */
    @Modifying
    @Query(value = """
            insert into learning_path_branch_results (user_id, path_id, edge_key, matched, rule, detail, decided_at)
            values (:userId, :pathId, :edgeKey, :matched, :rule, :detail, now())
            on conflict (user_id, path_id, edge_key) do nothing""", nativeQuery = true)
    int insertOnce(Long userId, Long pathId, String edgeKey, boolean matched, String rule, String detail);
}

package de.codepath.backend.pathway;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CodePathBranchResultRepository extends JpaRepository<CodePathBranchResult, Long> {
    List<CodePathBranchResult> findByUserIdAndPathId(Long userId, Long pathId);
    List<CodePathBranchResult> findByPathId(Long pathId);
    List<CodePathBranchResult> findByPathIdIn(Collection<Long> pathIds);
    void deleteByUserIdAndPathIdAndEdgeKey(Long userId, Long pathId, String edgeKey);
    void deleteByUserIdAndPathId(Long userId, Long pathId);
    void deleteByPathId(Long pathId);
    void deleteByUserIdIn(Collection<Long> userIds);
}

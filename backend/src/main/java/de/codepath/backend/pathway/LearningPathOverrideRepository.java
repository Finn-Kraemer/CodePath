package de.codepath.backend.pathway;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface LearningPathOverrideRepository extends JpaRepository<LearningPathOverride, Long> {

    List<LearningPathOverride> findByUserIdAndPathId(Long userId, Long pathId);

    List<LearningPathOverride> findByUserIdAndPathIdIn(Long userId, Collection<Long> pathIds);

    List<LearningPathOverride> findByPathIdIn(Collection<Long> pathIds);

    Optional<LearningPathOverride> findByUserIdAndPathIdAndNodeIdAndKindAndTarget(
            Long userId, Long pathId, String nodeId, String kind, String target);

    void deleteByUserIdAndPathIdAndNodeId(Long userId, Long pathId, String nodeId);

    void deleteByUserIdAndPathIdAndNodeIdAndKindAndTarget(
            Long userId, Long pathId, String nodeId, String kind, String target);

    void deleteByUserIdIn(Collection<Long> userIds);
}

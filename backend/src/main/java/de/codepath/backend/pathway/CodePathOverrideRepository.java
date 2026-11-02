package de.codepath.backend.pathway;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface CodePathOverrideRepository extends JpaRepository<CodePathOverride, Long> {

    List<CodePathOverride> findByUserIdAndPathId(Long userId, Long pathId);

    List<CodePathOverride> findByUserIdAndPathIdIn(Long userId, Collection<Long> pathIds);

    List<CodePathOverride> findByPathIdIn(Collection<Long> pathIds);

    Optional<CodePathOverride> findByUserIdAndPathIdAndNodeIdAndKindAndTarget(
            Long userId, Long pathId, String nodeId, String kind, String target);

    void deleteByUserIdAndPathIdAndNodeId(Long userId, Long pathId, String nodeId);

    void deleteByUserIdAndPathIdAndNodeIdAndKindAndTarget(
            Long userId, Long pathId, String nodeId, String kind, String target);

    void deleteByUserIdIn(Collection<Long> userIds);
}

package de.codepath.backend.pathway;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface CodePathChoiceRepository extends JpaRepository<CodePathChoice, Long> {

    List<CodePathChoice> findByUserIdAndPathId(Long userId, Long pathId);

    List<CodePathChoice> findByUserIdAndPathIdIn(Long userId, Collection<Long> pathIds);

    List<CodePathChoice> findByUserId(Long userId);

    List<CodePathChoice> findByPathId(Long pathId);

    List<CodePathChoice> findByPathIdIn(Collection<Long> pathIds);

    Optional<CodePathChoice> findByUserIdAndPathIdAndNodeId(Long userId, Long pathId, String nodeId);

    void deleteByUserIdIn(Collection<Long> userIds);

    void deleteByUserIdAndPathIdAndNodeId(Long userId, Long pathId, String nodeId);

    void deleteByPathIdAndNodeId(Long pathId, String nodeId);
}

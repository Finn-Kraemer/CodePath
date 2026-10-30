package de.codepath.backend.pathway;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface LearningPathChoiceRepository extends JpaRepository<LearningPathChoice, Long> {

    List<LearningPathChoice> findByUserIdAndPathId(Long userId, Long pathId);

    List<LearningPathChoice> findByUserId(Long userId);

    List<LearningPathChoice> findByPathId(Long pathId);

    Optional<LearningPathChoice> findByUserIdAndPathIdAndNodeId(Long userId, Long pathId, String nodeId);

    void deleteByUserIdIn(Collection<Long> userIds);

    void deleteByUserIdAndPathIdAndNodeId(Long userId, Long pathId, String nodeId);

    void deleteByPathIdAndNodeId(Long pathId, String nodeId);
}

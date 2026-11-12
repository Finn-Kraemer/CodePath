package de.codepath.backend.classroom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserReleaseRepository extends JpaRepository<UserRelease, Long> {

    List<UserRelease> findByUserId(Long userId);

    List<UserRelease> findByUserIdIn(Collection<Long> userIds);

    Optional<UserRelease> findByUserIdAndModuleId(Long userId, Long moduleId);

    Optional<UserRelease> findByUserIdAndTaskId(Long userId, Long taskId);
}

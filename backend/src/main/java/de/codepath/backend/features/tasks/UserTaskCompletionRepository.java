package de.codepath.backend.features.tasks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserTaskCompletionRepository extends JpaRepository<UserTaskCompletion, UserTaskCompletionId> {
    List<UserTaskCompletion> findByUserId(Long userId);
    @Query("SELECT COUNT(c) FROM UserTaskCompletion c WHERE c.user.id = :userId AND c.isCompleted = true")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(c) FROM UserTaskCompletion c WHERE c.user.id = :userId AND c.task.module.id = :moduleId AND c.isCompleted = true")
    long countByUserIdAndTaskModuleId(@Param("userId") Long userId, @Param("moduleId") Long moduleId);

    @Query("SELECT COUNT(c) > 0 FROM UserTaskCompletion c WHERE c.user.id = :userId AND c.task.id = :taskId AND c.isCompleted = true")
    boolean isCompleted(@Param("userId") Long userId, @Param("taskId") Long taskId);
    boolean existsByUserIdAndTaskId(Long userId, Long taskId);
    
    // Explicit naming to avoid ambiguity with @IdClass
    boolean existsByUser_IdAndTask_Id(Long userId, Long taskId);
    java.util.Optional<UserTaskCompletion> findByUser_IdAndTask_Id(Long userId, Long taskId);

    @Query("SELECT u.user.id, COUNT(u) FROM UserTaskCompletion u WHERE u.user.id IN :userIds AND u.isCompleted = true GROUP BY u.user.id")
    List<Object[]> countTasksByUserIds(@Param("userIds") List<Long> userIds);
}

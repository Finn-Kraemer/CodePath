package de.codepath.backend.features.tasks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserTaskCompletionRepository extends JpaRepository<UserTaskCompletion, UserTaskCompletionId> {
    List<UserTaskCompletion> findByUserId(Long userId);
    long countByUserId(Long userId);
    long countByUserIdAndTaskModuleId(Long userId, Long moduleId);
    boolean existsByUserIdAndTaskId(Long userId, Long taskId);
    
    // Explicit naming to avoid ambiguity with @IdClass
    boolean existsByUser_IdAndTask_Id(Long userId, Long taskId);

    @Query("SELECT u.user.id, COUNT(u) FROM UserTaskCompletion u WHERE u.user.id IN :userIds GROUP BY u.user.id")
    List<Object[]> countTasksByUserIds(@Param("userIds") List<Long> userIds);
}

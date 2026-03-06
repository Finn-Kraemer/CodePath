package de.codepath.backend.features.tasks;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Optional<Task> findByModuleIdAndSlug(Long moduleId, String slug);
    List<Task> findByModuleIdOrderBySortOrderAsc(Long moduleId);
    long countByModuleId(Long moduleId);
}

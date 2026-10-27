package de.codepath.backend.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByModuleIdAndSlug(Long moduleId, String slug);

    @Query("select t from Task t left join fetch t.currentVersion where t.module.id = :moduleId order by t.sortOrder, t.id")
    List<Task> findByModuleWithVersion(Long moduleId);

    @Query("select t from Task t join fetch t.module left join fetch t.currentVersion order by t.module.sortOrder, t.sortOrder, t.id")
    List<Task> findAllWithVersion();
}

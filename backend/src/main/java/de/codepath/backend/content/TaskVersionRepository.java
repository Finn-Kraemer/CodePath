package de.codepath.backend.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TaskVersionRepository extends JpaRepository<TaskVersion, Long> {

    List<TaskVersion> findByTaskIdOrderByVersionDesc(Long taskId);

    @Query("select coalesce(max(v.version), 0) from TaskVersion v where v.task.id = :taskId")
    int maxVersion(Long taskId);

    Optional<TaskVersion> findFirstByTaskIdAndPublishedAtIsNullOrderByVersionDesc(Long taskId);
}

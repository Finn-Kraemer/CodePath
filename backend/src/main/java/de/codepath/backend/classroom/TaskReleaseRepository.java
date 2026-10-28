package de.codepath.backend.classroom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskReleaseRepository extends JpaRepository<TaskRelease, TaskRelease.Key> {
    List<TaskRelease> findByCourseId(Long courseId);
}

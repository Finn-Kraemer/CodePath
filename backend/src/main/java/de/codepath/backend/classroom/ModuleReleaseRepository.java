package de.codepath.backend.classroom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModuleReleaseRepository extends JpaRepository<ModuleRelease, ModuleRelease.Key> {
    List<ModuleRelease> findByCourseId(Long courseId);
}

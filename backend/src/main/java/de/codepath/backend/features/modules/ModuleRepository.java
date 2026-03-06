package de.codepath.backend.features.modules;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ModuleRepository extends JpaRepository<Module, Long> {
    Optional<Module> findBySlug(String slug);
    List<Module> findByIsUnlockedTrue(Sort sort);
}

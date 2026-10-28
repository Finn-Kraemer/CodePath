package de.codepath.backend.site;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Long> {

    Optional<Page> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Page> findAllByOrderByPositionAscIdAsc();
}

package de.codepath.backend.pathway;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CodePathRepository extends JpaRepository<CodePath, Long> {

    Optional<CodePath> findBySlug(String slug);

    List<CodePath> findAllByOrderByPositionAscIdAsc();
}

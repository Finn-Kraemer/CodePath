package de.codepath.backend.worksheet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorksheetRepository extends JpaRepository<Worksheet, Long> {

    List<Worksheet> findAllByOrderByUpdatedAtDescIdDesc();

    Optional<Worksheet> findFirstByTitleOrderByIdAsc(String title);
}

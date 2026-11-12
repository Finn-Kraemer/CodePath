package de.codepath.backend.journal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalPromptRepository extends JpaRepository<JournalPrompt, Long> {
}

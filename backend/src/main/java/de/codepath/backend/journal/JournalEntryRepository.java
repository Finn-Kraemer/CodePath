package de.codepath.backend.journal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<JournalEntry> findByUserIdAndModuleId(Long userId, Long moduleId);

    List<JournalEntry> findBySharedTrueAndUserIdInOrderByUpdatedAtDesc(Collection<Long> userIds);
}

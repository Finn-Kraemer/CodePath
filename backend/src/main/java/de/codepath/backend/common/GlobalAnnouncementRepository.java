package de.codepath.backend.common;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GlobalAnnouncementRepository extends JpaRepository<GlobalAnnouncement, Long> {
    Optional<GlobalAnnouncement> findFirstByOrderByUpdatedAtDesc();
}

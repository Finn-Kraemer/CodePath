package de.codepath.backend.classroom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    Optional<Announcement> findFirstByOrderByUpdatedAtDesc();
}

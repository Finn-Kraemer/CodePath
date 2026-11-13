package de.codepath.backend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AttendanceEntryRepository extends JpaRepository<AttendanceEntry, AttendanceEntry.Key> {

    List<AttendanceEntry> findBySessionId(Long sessionId);

    List<AttendanceEntry> findBySessionIdIn(Collection<Long> sessionIds);

    List<AttendanceEntry> findByUserId(Long userId);
}

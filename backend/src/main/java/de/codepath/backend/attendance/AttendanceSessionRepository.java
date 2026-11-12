package de.codepath.backend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    List<AttendanceSession> findByCourseIdOrderByDateAscIdAsc(Long courseId);

    void deleteByCourseId(Long courseId);
}

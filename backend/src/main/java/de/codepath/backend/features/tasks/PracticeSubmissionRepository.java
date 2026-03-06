package de.codepath.backend.features.tasks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PracticeSubmissionRepository extends JpaRepository<PracticeSubmission, Long> {
    List<PracticeSubmission> findByStatus(SubmissionStatus status);
    
    @Query("SELECT p FROM PracticeSubmission p JOIN FETCH p.user JOIN FETCH p.task t JOIN FETCH t.module WHERE p.status = :status")
    List<PracticeSubmission> findByStatusWithDetails(@Param("status") SubmissionStatus status);
    
    List<PracticeSubmission> findByUserId(Long userId);

    java.util.Optional<PracticeSubmission> findTopByUser_IdAndTask_IdOrderBySubmittedAtDesc(Long userId, Long taskId);

    boolean existsByUser_IdAndTask_IdAndStatus(Long userId, Long taskId, SubmissionStatus status);

    long countByUser_IdAndStatus(Long id, SubmissionStatus submissionStatus);
}

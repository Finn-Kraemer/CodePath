package de.codepath.backend.progress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    Optional<Attempt> findFirstByUserIdAndTaskIdOrderByCreatedAtDesc(Long userId, Long taskId);

    List<Attempt> findByUserIdAndTaskIdOrderByCreatedAtDesc(Long userId, Long taskId);

    /** Je Aufgabe: Anzahl Versuche, falsche Versuche, verschiedene Schüler. */
    @Query("""
            select a.taskId, count(a),
                   sum(case when a.outcome = de.codepath.backend.engine.Evaluation.Outcome.WRONG
                             or a.outcome = de.codepath.backend.engine.Evaluation.Outcome.PARTIAL then 1 else 0 end),
                   count(distinct a.userId)
            from Attempt a group by a.taskId""")
    List<Object[]> statisticsByTask();
}

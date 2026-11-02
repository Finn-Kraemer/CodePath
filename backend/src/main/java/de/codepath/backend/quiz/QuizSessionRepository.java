package de.codepath.backend.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {

    Optional<QuizSession> findFirstByPhaseNot(QuizSession.Phase phase);
}

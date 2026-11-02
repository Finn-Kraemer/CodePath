package de.codepath.backend.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {

    List<QuizAnswer> findBySessionIdAndQuestionIndex(Long sessionId, int questionIndex);

    List<QuizAnswer> findBySessionId(Long sessionId);

    Optional<QuizAnswer> findBySessionIdAndUserIdAndQuestionIndex(Long sessionId, Long userId, int questionIndex);

    long countBySessionIdAndUserIdAndCorrectTrue(Long sessionId, Long userId);
}

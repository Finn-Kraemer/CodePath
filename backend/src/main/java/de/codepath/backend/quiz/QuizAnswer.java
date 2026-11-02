package de.codepath.backend.quiz;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** Antwort einer Person auf eine Quizfrage. Die Auswertung am Beamer ist anonym. */
@Entity
@Table(name = "quiz_answers")
@Getter @Setter @NoArgsConstructor
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_index", nullable = false)
    private int questionIndex;

    @Column(nullable = false)
    private String selected;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt = LocalDateTime.now();

    public List<String> selectedIds() {
        return Arrays.stream(selected.split(",")).filter(s -> !s.isBlank()).toList();
    }
}

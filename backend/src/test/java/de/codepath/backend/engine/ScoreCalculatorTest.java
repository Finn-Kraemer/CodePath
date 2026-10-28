package de.codepath.backend.engine;

import de.codepath.backend.progress.ScoreCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreCalculatorTest {

    final AttemptRules rules = new AttemptRules(3, 25, 20, true);

    @Test
    void firstTryWithoutHintGetsBonus() {
        assertEquals(12, ScoreCalculator.points(10, 1.0, 0, rules, true, false));
        assertEquals(10, ScoreCalculator.points(10, 1.0, 0, rules, false, false));
    }

    @Test
    void hintsCostPointsButNeverEverything() {
        assertEquals(8, ScoreCalculator.points(10, 1.0, 1, rules, true, false));
        assertEquals(3, ScoreCalculator.points(10, 1.0, 5, rules, true, false));
        assertEquals(1, ScoreCalculator.points(2, 1.0, 5, rules, false, false));
    }

    @Test
    void revealedSolutionGivesNothingAndPartialScoresScale() {
        assertEquals(0, ScoreCalculator.points(10, 1.0, 0, rules, true, true));
        assertEquals(5, ScoreCalculator.points(10, 0.5, 0, rules, false, false));
        assertEquals(0, ScoreCalculator.points(10, 0.0, 0, rules, false, false));
    }
}

package de.codepath.backend.engine.handlers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogicCircuitTest {

    static List<Boolean> table(String expression, List<String> inputs) {
        return LogicCircuit.truthTable(LogicCircuit.parse(expression, inputs), inputs);
    }

    @Test
    void parsesWithPrecedenceAndAlternativeSymbols() {
        List<String> abc = List.of("A", "B", "C");
        // Mehrheitsschaltung, Zeilen 000 … 111
        assertEquals(List.of(false, false, false, true, false, true, true, true), table("(A AND B) OR (A AND C) OR (B AND C)", abc));
        assertEquals(table("(A AND B) OR (NOT C)", abc), table("A & B | !C", abc));
        assertEquals(table("A OR (B AND C)", abc), table("A + B · C", abc));
        assertEquals(List.of(true, true, true, false), table("A NAND B", List.of("A", "B")));
        assertEquals(List.of(true, false), table("¬A", List.of("A")));
    }

    @Test
    void rejectsBrokenExpressions() {
        List<String> abc = List.of("A", "B", "C");
        assertThrows(IllegalArgumentException.class, () -> table("A AND D", abc));
        assertThrows(IllegalArgumentException.class, () -> table("(A AND B", abc));
        assertThrows(IllegalArgumentException.class, () -> table("A AND", abc));
        assertThrows(IllegalArgumentException.class, () -> table("A B", abc));
        assertThrows(IllegalArgumentException.class, () -> table("NOT ".repeat(80) + "A", abc));
        assertThrows(IllegalArgumentException.class, () -> table("", abc));
    }

    @Test
    void handlerValidatesExpressionAndSolution() {
        LogicCircuitHandler handler = new LogicCircuitHandler();
        assertTrue(handler.validate(handler.template(), 10).isEmpty());
        assertFalse(handler.validate(Map.of("inputs", List.of("A", "B"), "expression", "A AND",
                "solution", Map.of("gates", List.of(), "output", "A")), 10).isEmpty());
        assertFalse(handler.validate(Map.of("inputs", List.of("A", "B"), "expression", "A AND B",
                "solution", Map.of("gates", List.of(), "output", "A")), 10).isEmpty(), "Musterschaltung falsch");
        assertFalse(handler.validate(Map.of("inputs", List.of("a"), "expression", "a",
                "solution", Map.of("gates", List.of(), "output", "a")), 10).isEmpty(), "Eingänge groß");
    }
}

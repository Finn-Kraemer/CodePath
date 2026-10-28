package de.codepath.backend.engine.handlers;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Gemeinsame Fälle für den Terminal-Simulator: Das Frontend (terminal.test.ts) spielt dieselbe Datei durch.
 * Weichen die beiden Modelle voneinander ab, sieht die Schülerin im Browser etwas anderes, als der Server bewertet.
 */
class TerminalCasesTest {

    static final Path CASES = Path.of("../frontend/src/lib/tasks/terminal-cases.json");

    @TestFactory
    @SuppressWarnings("unchecked")
    List<DynamicTest> backendMatchesSharedCases() throws Exception {
        ObjectMapper json = JsonMapper.builder().build();
        List<Map<String, Object>> cases = json.readValue(Files.readString(CASES), List.class);
        List<DynamicTest> tests = new ArrayList<>();
        for (Map<String, Object> c : cases) {
            tests.add(DynamicTest.dynamicTest((String) c.get("name"), () -> {
                TerminalSimulator sim = new TerminalSimulator(
                        (List<String>) c.getOrDefault("initialDirectories", List.of()),
                        (Map<String, String>) c.getOrDefault("initialFiles", Map.of()),
                        (Map<String, Object>) c.getOrDefault("git", Map.of()));
                for (Map<String, Object> step : (List<Map<String, Object>>) c.get("steps")) {
                    assertEquals(step.get("out"), sim.execute((String) step.get("cmd")), "Befehl: " + step.get("cmd"));
                }
                assertEquals(c.get("directories"), new ArrayList<>(sim.directories()));
                assertEquals(c.get("files"), new TreeMap<>(sim.files()));
            }));
        }
        return tests;
    }
}

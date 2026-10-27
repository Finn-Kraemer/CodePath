package de.codepath.backend.features.tasks;

import java.util.*;

/**
 * Erzeugt die öffentliche Sicht auf eine Aufgabenkonfiguration.
 *
 * Arbeitet mit einer Whitelist je Aufgabentyp: Nur Felder, die der Browser zum Anzeigen braucht,
 * verlassen den Server. Lösungsfelder (correct, blanks, expectedOutput, solutionSql, correctOrder,
 * expectedState) und Hinweise (hint, support) werden nie ausgeliefert.
 */
public final class TaskConfigFilter {

    private static final Map<TaskType, Set<String>> PUBLIC_FIELDS = Map.of(
            TaskType.MULTIPLE_CHOICE, Set.of("options"),
            TaskType.FILL_BLANK, Set.of("template"),
            TaskType.FILL_CODE, Set.of("template"),
            TaskType.CODE, Set.of("starterCode"),
            TaskType.SORTING, Set.of(),
            TaskType.SQL, Set.of("starterSql"),
            TaskType.TERMINAL, Set.of("initialFs"),
            TaskType.PRACTICE, Set.of("instructions", "submissionNote")
    );

    private TaskConfigFilter() {
    }

    /**
     * @param seed bestimmt die Mischreihenfolge bei Sortieraufgaben; pro Schüler und Aufgabe stabil,
     *             damit sich die Reihenfolge beim Neuladen nicht ändert
     */
    public static Map<String, Object> publicConfig(Task task, long seed) {
        Map<String, Object> config = task.getConfig() != null ? task.getConfig() : Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : PUBLIC_FIELDS.getOrDefault(task.getType(), Set.of())) {
            if (config.containsKey(field)) {
                result.put(field, config.get(field));
            }
        }

        if (task.getType() == TaskType.SORTING && config.get("items") instanceof List<?> items) {
            List<?> correctOrder = config.get("correctOrder") instanceof List<?> l ? l : List.of();
            result.put("items", shuffledItems(items, correctOrder, seed));
        }
        return result;
    }

    public static Optional<String> hint(Task task) {
        Map<String, Object> config = task.getConfig() != null ? task.getConfig() : Map.of();
        Object hint = config.get("support") != null ? config.get("support") : config.get("hint");
        return Optional.ofNullable(hint).map(Object::toString);
    }

    /**
     * Sortier-Elemente werden mit ihrer ursprünglichen Position als ID ausgeliefert, aber gemischt –
     * sonst stünde die Lösung bereits in der Reihenfolge der Liste.
     */
    private static List<Map<String, Object>> shuffledItems(List<?> items, List<?> correctOrder, long seed) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            result.add(Map.of("id", i, "text", String.valueOf(items.get(i))));
        }
        if (result.size() < 2) return result;

        Random random = new Random(seed);
        List<String> solution = correctOrder.stream().map(String::valueOf).toList();
        // Nicht zufällig die richtige Reihenfolge ausliefern
        for (int attempt = 0; attempt < 10; attempt++) {
            Collections.shuffle(result, random);
            List<String> ids = result.stream().map(item -> String.valueOf(item.get("id"))).toList();
            if (!ids.equals(solution)) break;
        }
        return result;
    }
}

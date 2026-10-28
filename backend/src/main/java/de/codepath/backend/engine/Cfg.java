package de.codepath.backend.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typsicheres Lesen aus den JSON-Konfigurationen und Antworten (die als Map ankommen).
 * Lesemethoden geben bei falschem Typ einen leeren Wert zurück statt zu werfen – Schülerantworten
 * können beliebig kaputt sein.
 */
public final class Cfg {

    private Cfg() {
    }

    public static String string(Map<String, Object> map, String key) {
        return map != null && map.get(key) instanceof String s ? s : null;
    }

    public static String string(Map<String, Object> map, String key, String fallback) {
        String value = string(map, key);
        return value != null ? value : fallback;
    }

    public static boolean bool(Map<String, Object> map, String key) {
        return map != null && Boolean.TRUE.equals(map.get(key));
    }

    public static Integer integer(Map<String, Object> map, String key) {
        return map != null && map.get(key) instanceof Number n ? n.intValue() : null;
    }

    public static List<Object> list(Map<String, Object> map, String key) {
        if (map != null && map.get(key) instanceof List<?> l) {
            return new ArrayList<>(l);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objects(Map<String, Object> map, String key) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list(map, key)) {
            if (item instanceof Map<?, ?> m) {
                result.add((Map<String, Object>) m);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Map<String, Object> map, String key) {
        return map != null && map.get(key) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** Liste von Strings; Nicht-Strings werden zu Text (Zahlen) oder ausgelassen. */
    public static List<String> strings(Map<String, Object> map, String key) {
        List<String> result = new ArrayList<>();
        for (Object item : list(map, key)) {
            if (item instanceof String || item instanceof Number) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    /** Sammelt Prüfmeldungen für {@link TaskHandler#validate}. */
    public static final class Problems {
        private final List<String> problems = new ArrayList<>();

        public Problems require(boolean condition, String message) {
            if (!condition) problems.add(message);
            return this;
        }

        public Problems requireText(Map<String, Object> map, String key) {
            String value = string(map, key);
            return require(value != null && !value.isBlank(), "Feld '" + key + "' fehlt oder ist leer");
        }

        public Problems requireUniqueIds(List<Map<String, Object>> items, String what) {
            List<String> ids = items.stream().map(i -> string(i, "id")).toList();
            require(ids.stream().allMatch(Objects::nonNull), "Jedes Element in '" + what + "' braucht eine id");
            require(ids.stream().distinct().count() == ids.size(), "IDs in '" + what + "' müssen eindeutig sein");
            return this;
        }

        public List<String> list() {
            return List.copyOf(problems);
        }
    }
}

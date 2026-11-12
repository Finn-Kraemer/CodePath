package de.codepath.backend.evaluation;

import de.codepath.backend.platform.ApiException;

import java.util.*;

/** Prüft Kriterienlisten und Antworten. Ungültiges wird mit einer verständlichen Meldung abgelehnt. */
final class Criteria {

    static final int MAX_CRITERIA = 60;
    static final int MAX_LABEL = 300;
    static final int MAX_HELP = 1000;
    static final int MAX_SHORT_LABEL = 60;
    static final int MAX_OPTIONS = 20;
    static final int MAX_OPTION = 200;
    static final int MAX_TEXT = 5000;
    static final int DEFAULT_TEXT = 1000;
    static final int MAX_STEPS = 11;
    static final Set<String> STYLES = Set.of("NUMBERS", "STARS");
    private static final String ID_PATTERN = "[A-Za-z0-9_-]{1,40}";

    private Criteria() {
    }

    /** Bereinigt eine Kriterienliste: fehlende IDs werden vergeben, nicht passende Felder entfernt. */
    static List<EvaluationCriterion> normalize(List<EvaluationCriterion> criteria) {
        if (criteria == null) return null;
        if (criteria.size() > MAX_CRITERIA) {
            throw ApiException.badRequest("Ein Bogen darf höchstens " + MAX_CRITERIA + " Kriterien haben.");
        }
        Set<String> ids = new HashSet<>();
        criteria.forEach(c -> {
            if (c != null && c.id() != null && !c.id().isBlank()) ids.add(c.id().strip());
        });
        Set<String> seen = new HashSet<>();
        List<EvaluationCriterion> result = new ArrayList<>();
        int counter = 1;
        for (EvaluationCriterion c : criteria) {
            if (c == null) continue;
            String id = c.id() == null ? "" : c.id().strip();
            if (id.isEmpty()) {
                do {
                    id = "k" + counter++;
                } while (ids.contains(id) || seen.contains(id));
            }
            if (!id.matches(ID_PATTERN)) {
                throw ApiException.badRequest("Ungültige Kriterium-ID „" + id + "“ (Buchstaben, Ziffern, - und _).");
            }
            if (!seen.add(id)) throw ApiException.badRequest("Die Kriterium-ID „" + id + "“ kommt doppelt vor.");
            result.add(normalize(id, c));
        }
        if (result.stream().noneMatch(EvaluationCriterion::answerable)) {
            throw ApiException.badRequest("Der Bogen braucht mindestens ein Kriterium, das beantwortet werden kann.");
        }
        return List.copyOf(result);
    }

    private static EvaluationCriterion normalize(String id, EvaluationCriterion c) {
        if (c.type() == null) throw ApiException.badRequest("Bitte für jedes Kriterium einen Typ wählen.");
        String label = text(c.label(), MAX_LABEL, "Die Beschriftung");
        if (label == null) throw ApiException.badRequest("Jedes Kriterium braucht eine Beschriftung.");
        String help = text(c.help(), MAX_HELP, "Die Erläuterung");
        Boolean required = c.type() == CriterionType.HEADING ? null : c.isRequired();
        return switch (c.type()) {
            case HEADING -> new EvaluationCriterion(id, c.type(), label, help, null, null, null, null, null, null, null, null, null, null);
            case YES_NO -> new EvaluationCriterion(id, c.type(), label, help, required, null, null, null, null, null, null, null, null, null);
            case TEXT -> {
                int max = c.maxLength() == null ? DEFAULT_TEXT : c.maxLength();
                if (max < 1 || max > MAX_TEXT) {
                    throw ApiException.badRequest("„" + label + "“: Die Länge muss zwischen 1 und " + MAX_TEXT + " Zeichen liegen.");
                }
                yield new EvaluationCriterion(id, c.type(), label, help, required, null, null, null, null, null, null, null, null, max);
            }
            case SCALE -> {
                int min = c.min() == null ? 1 : c.min();
                int max = c.max() == null ? 5 : c.max();
                if (min < 0 || max <= min || max - min + 1 > MAX_STEPS || max > 10) {
                    throw ApiException.badRequest("„" + label + "“: Die Skala muss zwischen 0 und 10 liegen und 2 bis " + MAX_STEPS + " Stufen haben.");
                }
                List<String> labels = null;
                if (c.labels() != null && c.labels().stream().anyMatch(l -> l != null && !l.isBlank())) {
                    if (c.labels().size() != max - min + 1) {
                        throw ApiException.badRequest("„" + label + "“: Es braucht genau eine Beschriftung je Stufe.");
                    }
                    labels = c.labels().stream().map(l -> {
                        String value = text(l, MAX_SHORT_LABEL, "Eine Stufen-Beschriftung");
                        return value == null ? "" : value;
                    }).toList();
                }
                String style = c.style() == null || c.style().isBlank() ? "NUMBERS" : c.style().strip().toUpperCase(Locale.ROOT);
                if (!STYLES.contains(style)) throw ApiException.badRequest("„" + label + "“: Unbekannte Darstellung.");
                yield new EvaluationCriterion(id, c.type(), label, help, required, min, max,
                        text(c.minLabel(), MAX_SHORT_LABEL, "Die Beschriftung links"),
                        text(c.maxLabel(), MAX_SHORT_LABEL, "Die Beschriftung rechts"), labels, style, null, null, null);
            }
            case CHOICE -> {
                List<String> options = new ArrayList<>();
                if (c.options() != null) {
                    for (String option : c.options()) {
                        String value = text(option, MAX_OPTION, "Eine Option");
                        if (value == null) continue;
                        if (options.contains(value)) throw ApiException.badRequest("„" + label + "“: Die Option „" + value + "“ kommt doppelt vor.");
                        options.add(value);
                    }
                }
                if (options.size() < 2 || options.size() > MAX_OPTIONS) {
                    throw ApiException.badRequest("„" + label + "“: Eine Auswahl braucht 2 bis " + MAX_OPTIONS + " Optionen.");
                }
                yield new EvaluationCriterion(id, c.type(), label, help, required, null, null, null, null, null, null,
                        List.copyOf(options), Boolean.TRUE.equals(c.multiple()), null);
            }
        };
    }

    /**
     * Prüft Antworten gegen die Kriterien und gibt eine bereinigte Fassung zurück: unbekannte Kriterien
     * fallen weg, Texte werden getrimmt, leere Antworten weggelassen.
     */
    static Map<String, Object> answers(List<EvaluationCriterion> criteria, Map<String, Object> raw) {
        Map<String, Object> input = raw == null ? Map.of() : raw;
        Map<String, Object> clean = new LinkedHashMap<>();
        for (EvaluationCriterion c : criteria) {
            if (!c.answerable()) continue;
            Object value = answer(c, input.get(c.id()));
            if (value == null) {
                if (c.isRequired()) throw ApiException.badRequest("Bitte beantworte: „" + c.label() + "“");
                continue;
            }
            clean.put(c.id(), value);
        }
        return clean;
    }

    private static Object answer(EvaluationCriterion c, Object value) {
        if (value == null) return null;
        return switch (c.type()) {
            case HEADING -> null;
            case SCALE -> {
                if (!(value instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())
                        || n.intValue() < c.min() || n.intValue() > c.max()) {
                    throw ApiException.badRequest("„" + c.label() + "“: Bitte einen Wert von " + c.min() + " bis " + c.max() + " wählen.");
                }
                yield n.intValue();
            }
            case YES_NO -> {
                if (!(value instanceof Boolean b)) throw ApiException.badRequest("„" + c.label() + "“: Bitte Ja oder Nein wählen.");
                yield b;
            }
            case TEXT -> {
                if (!(value instanceof String s)) throw ApiException.badRequest("„" + c.label() + "“: Text erwartet.");
                String text = s.strip();
                if (text.isEmpty()) yield null;
                if (text.length() > c.maxLength()) {
                    throw ApiException.badRequest("„" + c.label() + "“: höchstens " + c.maxLength() + " Zeichen.");
                }
                yield text;
            }
            case CHOICE -> {
                if (Boolean.TRUE.equals(c.multiple())) {
                    if (!(value instanceof Collection<?> list)) throw ApiException.badRequest("„" + c.label() + "“: Liste erwartet.");
                    List<String> chosen = new ArrayList<>();
                    for (Object o : list) {
                        if (!(o instanceof String s) || !c.options().contains(s)) {
                            throw ApiException.badRequest("„" + c.label() + "“: Unbekannte Option.");
                        }
                        if (!chosen.contains(s)) chosen.add(s);
                    }
                    // In der Reihenfolge der Optionen speichern
                    chosen.sort(Comparator.comparingInt(c.options()::indexOf));
                    yield chosen.isEmpty() ? null : List.copyOf(chosen);
                }
                if (!(value instanceof String s) || !c.options().contains(s)) {
                    throw ApiException.badRequest("„" + c.label() + "“: Unbekannte Option.");
                }
                yield s;
            }
        };
    }

    private static String text(String value, int max, String what) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.strip();
        if (trimmed.length() > max) throw ApiException.badRequest(what + " ist zu lang (höchstens " + max + " Zeichen).");
        return trimmed;
    }
}

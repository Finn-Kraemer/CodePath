package de.codepath.backend.evaluation;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Ein Kriterium eines Bewertungsbogens. Welche Felder gelten, hängt vom Typ ab:
 * SCALE nutzt min/max, minLabel/maxLabel, labels (je Stufe) und style (NUMBERS, STARS),
 * CHOICE nutzt options und multiple, TEXT nutzt maxLength.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationCriterion(String id, CriterionType type, String label, String help, Boolean required,
                        Integer min, Integer max, String minLabel, String maxLabel, List<String> labels, String style,
                        List<String> options, Boolean multiple, Integer maxLength) {

    public boolean isRequired() {
        return Boolean.TRUE.equals(required);
    }

    public boolean answerable() {
        return type != CriterionType.HEADING;
    }
}

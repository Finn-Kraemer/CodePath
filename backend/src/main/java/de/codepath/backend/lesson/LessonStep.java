package de.codepath.backend.lesson;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Ein Schritt eines Stundenablaufs. Je nach {@code type} sind nur einige Felder belegt:
 * <ul>
 *   <li>RELEASE: {@code moduleSlug}, optional {@code taskSlug}; {@code action} UNLOCK/LOCK (Modul) bzw.
 *   SHOW/HIDE/MODULE (Aufgabe); optional {@code deadline} „HH:mm“ oder ISO-Zeitpunkt, „-“ entfernt die Frist</li>
 *   <li>QUIZ: {@code text} als Titel, {@code taskIds} (Auswahl-Aufgaben)</li>
 *   <li>WORKSHEET: {@code worksheetId} – nur Verweis zum Drucken</li>
 *   <li>EVALUATION: {@code roundId}, {@code action} OPEN/CLOSED</li>
 *   <li>ANNOUNCEMENT: {@code text}, {@code displayMode} HEADER/OVERLAY (leerer Text blendet aus)</li>
 *   <li>NOTE: {@code text} – nur für die Lehrkraft</li>
 * </ul>
 * {@code minutes} ist die geplante Dauer (Timer in der Ansicht „Stunde läuft“).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LessonStep(String id, Type type, String title, Integer minutes, String moduleSlug, String taskSlug,
                         String action, String deadline, List<Long> taskIds, Long roundId, Long worksheetId,
                         String text, String displayMode) {

    public enum Type { RELEASE, QUIZ, WORKSHEET, EVALUATION, ANNOUNCEMENT, NOTE }
}

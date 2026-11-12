package de.codepath.backend.progress;

import de.codepath.backend.identity.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Übernahme in einen neuen Kurs ohne Fortschritt: Versuche, Fortschritt und Punkte der Mitglieder löschen. */
@Component
@RequiredArgsConstructor
class CoursePromotionListener {

    private final PointsEntryRepository pointsRepository;
    private final AttemptRepository attemptRepository;
    private final ProgressRepository progressRepository;

    @EventListener
    @Transactional
    public void onCoursePromoted(CourseService.CoursePromotedEvent event) {
        if (event.options().keepProgress() || event.userIds().isEmpty()) return;
        pointsRepository.deleteByUserIds(event.userIds());
        attemptRepository.deleteByUserIds(event.userIds());
        progressRepository.deleteByUserIds(event.userIds());
    }
}

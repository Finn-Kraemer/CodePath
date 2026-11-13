package de.codepath.backend.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Löscht archivierte Kurse nach Ablauf ihrer Frist – wie „Kurs beenden“, nur automatisch. */
@Component
@RequiredArgsConstructor
@Slf4j
class CourseArchiveJob {

    private final CourseService courseService;

    @Scheduled(fixedRate = 3_600_000, initialDelay = 120_000)
    void purge() {
        int purged = courseService.purgeExpired(LocalDateTime.now());
        if (purged > 0) log.info("{} archivierte Kurse nach Ablauf der Frist gelöscht", purged);
    }
}

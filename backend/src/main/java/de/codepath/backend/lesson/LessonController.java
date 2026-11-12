package de.codepath.backend.lesson;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Stundenabläufe – nur für Lehrkräfte (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final LessonService service;

    /** expectedStep: der Schritt, den die Oberfläche als nächsten zeigt (verhindert doppeltes Auslösen) */
    public record NextRequest(Integer expectedStep) {
    }

    /** Abläufe eines Kurses und alle Vorlagen (ohne Kurs); ohne courseId alle. */
    @GetMapping
    public List<LessonService.LessonSummary> list(@RequestParam(name = "courseId", required = false) Long courseId) {
        return service.list(courseId);
    }

    @PostMapping
    public LessonService.LessonDetail create(@RequestBody(required = false) LessonService.LessonUpdate update) {
        return service.create(update);
    }

    @GetMapping("/{id}")
    public LessonService.LessonDetail get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public LessonService.LessonDetail update(@PathVariable("id") Long id, @RequestBody LessonService.LessonUpdate update) {
        return service.update(id, update);
    }

    @PostMapping("/{id}/copy")
    public LessonService.LessonDetail duplicate(@PathVariable("id") Long id) {
        return service.duplicate(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }

    @PutMapping("/order")
    public List<LessonService.LessonSummary> reorder(@RequestBody List<Long> ids) {
        return service.reorder(ids);
    }

    @PostMapping("/{id}/start")
    public LessonService.LessonDetail start(@PathVariable("id") Long id) {
        return service.start(id);
    }

    @PostMapping("/{id}/next")
    public LessonService.LessonDetail next(@PathVariable("id") Long id, @RequestBody(required = false) NextRequest request) {
        return service.next(id, request == null ? null : request.expectedStep());
    }

    @PostMapping("/{id}/finish")
    public LessonService.LessonDetail finish(@PathVariable("id") Long id) {
        return service.finish(id);
    }

    @PostMapping("/{id}/reset")
    public LessonService.LessonDetail reset(@PathVariable("id") Long id) {
        return service.reset(id);
    }
}

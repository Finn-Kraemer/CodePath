package de.codepath.backend.journal;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Lerntagebuch: eigene Notizen unter /api/journal, geteilte Einträge und Leitfragen für die Lehrkraft. */
@RestController
@RequiredArgsConstructor
public class JournalController {

    private final JournalService service;
    private final UserService userService;

    /** taskSlug leer = Notiz zum Modul; leerer Text löscht; shared null = unverändert */
    public record SaveRequest(String taskSlug, String text, Boolean shared) {
    }

    public record PromptsRequest(String prompts) {
    }

    @GetMapping("/api/journal")
    public List<JournalService.JournalEntryView> all() {
        return service.all(userService.currentUser());
    }

    @GetMapping("/api/journal/modules/{slug}")
    public JournalService.ModuleJournal module(@PathVariable("slug") String slug) {
        return service.module(userService.currentUser(), slug);
    }

    /** 204, wenn die Notiz durch leeren Text gelöscht wurde. */
    @PutMapping("/api/journal/modules/{slug}")
    public ResponseEntity<JournalService.JournalEntryView> save(@PathVariable("slug") String slug, @RequestBody SaveRequest request) {
        JournalService.JournalEntryView saved = service.save(userService.currentUser(), slug, request.taskSlug(), request.text(), request.shared());
        return saved == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(saved);
    }

    @DeleteMapping("/api/journal/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(userService.currentUser(), id);
    }

    /** Nur Einträge, die Schülerinnen und Schüler mit der Lehrkraft geteilt haben. */
    @GetMapping("/api/admin/journal/shared")
    public List<JournalService.SharedEntry> shared(@RequestParam(name = "courseId", required = false) Long courseId,
                                                   @RequestParam(name = "username", required = false) String username) {
        return service.shared(courseId, username);
    }

    @GetMapping("/api/admin/modules/{id}/journal-prompts")
    public Map<String, List<String>> prompts(@PathVariable("id") Long moduleId) {
        return Map.of("prompts", service.prompts(moduleId));
    }

    @PutMapping("/api/admin/modules/{id}/journal-prompts")
    public Map<String, List<String>> setPrompts(@PathVariable("id") Long moduleId, @RequestBody PromptsRequest request) {
        return Map.of("prompts", service.setPrompts(moduleId, request.prompts()));
    }
}

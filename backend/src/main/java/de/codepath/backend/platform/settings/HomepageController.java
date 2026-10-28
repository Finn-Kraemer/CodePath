package de.codepath.backend.platform.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/** Startseite: lesen für alle (auch ohne Anmeldung), bearbeiten nur für die Lehrkraft. */
@RestController
@RequiredArgsConstructor
public class HomepageController {

    private final HomepageService homepageService;

    /** content ist null, solange die Lehrkraft nichts veröffentlicht hat – dann gilt die Standardseite. */
    @GetMapping("/api/public/homepage")
    public Map<String, JsonNode> homepage() {
        Map<String, JsonNode> result = new HashMap<>();
        result.put("content", homepageService.published());
        return result;
    }

    @GetMapping("/api/admin/homepage")
    public HomepageService.HomepageState state() {
        return homepageService.state();
    }

    @PutMapping("/api/admin/homepage/draft")
    public ResponseEntity<Void> saveDraft(@RequestBody JsonNode content) {
        homepageService.saveDraft(content);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/homepage/published")
    public ResponseEntity<Void> publish(@RequestBody JsonNode content) {
        homepageService.publish(content);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/homepage")
    public ResponseEntity<Void> reset() {
        homepageService.reset();
        return ResponseEntity.noContent().build();
    }
}

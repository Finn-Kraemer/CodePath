package de.codepath.backend.platform.events;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class EventController {

    private final RealtimeUpdateService realtimeUpdateService;

    @GetMapping(path = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(Authentication authentication) {
        // Die Rolle steht in der Session; platform kennt das identity-Paket nicht
        boolean teacher = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return realtimeUpdateService.subscribe(authentication != null ? authentication.getName() : null, teacher);
    }
}

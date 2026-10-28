package de.codepath.backend.progress;

import de.codepath.backend.classroom.AccessPolicy;
import de.codepath.backend.content.ContentEditorService;
import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.engine.Cfg;
import de.codepath.backend.engine.TaskContext;
import de.codepath.backend.engine.TaskType;
import de.codepath.backend.engine.handlers.DevToolsHandler;
import de.codepath.backend.engine.lab.LabScenario;
import de.codepath.backend.engine.lab.LabScenarios;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Liefert die Übungsseiten des Browser-Labors aus (eigener Tab, /api/lab/{aufgabe}/…).
 * Schüler bekommen das Labor nur für Aufgaben aus sichtbaren Modulen, mit ihren persönlichen Codes;
 * die Lehrkraft sieht den Entwurf mit den Codes der Vorschau. Die Labor-Schnittstellen ändern nichts
 * an echten Daten – deshalb sind sie von CSRF ausgenommen (Schüler sollen Anfragen von Hand senden).
 */
@RestController
@RequiredArgsConstructor
public class LabController {

    static final int MAX_BODY = 10_000;
    private static final TaskContext PREVIEW = new TaskContext(0);

    private final UserService userService;
    private final ContentService contentService;
    private final ContentEditorService editor;
    private final AccessPolicy accessPolicy;
    private final DevToolsHandler handler;
    private final LabScenarios scenarios;

    @RequestMapping({"/api/lab/{taskId}", "/api/lab/{taskId}/**"})
    public ResponseEntity<String> lab(HttpServletRequest request) throws IOException {
        String prefix = "/api/lab/";
        String rest = request.getRequestURI().substring(request.getContextPath().length() + prefix.length());
        int slash = rest.indexOf('/');
        String idText = slash < 0 ? rest : rest.substring(0, slash);
        String path = slash < 0 ? "" : rest.substring(slash + 1);
        long taskId;
        try {
            taskId = Long.parseLong(idText);
        } catch (NumberFormatException e) {
            throw ApiException.notFound("Labor nicht gefunden.");
        }

        User user = userService.currentUser();
        PublishedTask task;
        TaskContext context;
        if (user.isTeacher()) {
            task = editor.previewTask(taskId);
            context = PREVIEW;
        } else {
            task = contentService.publishedTask(taskId);
            if (task.task().isArchived()) throw ApiException.notFound("Labor nicht gefunden.");
            accessPolicy.requireTaskVisible(user, task);
            context = TaskContext.of(user.getId(), task.taskId());
        }
        if (task.type() != TaskType.DEVTOOLS) throw ApiException.notFound("Labor nicht gefunden.");
        LabScenario scenario = scenarios.find(Cfg.string(task.spec().config(), "scenario"))
                .orElseThrow(() -> ApiException.notFound("Labor nicht gefunden."));

        byte[] body = request.getInputStream().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) throw ApiException.badRequest("Anfrage zu groß.");

        LabScenario.LabResponse response = scenario.handle(new LabScenario.LabRequest(
                request.getContextPath() + prefix + taskId, request.getMethod(), path,
                query(request), headers(request), new String(body, StandardCharsets.UTF_8),
                handler.codes(context, scenario)));

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status())
                .contentType(MediaType.parseMediaType(response.contentType()))
                .cacheControl(CacheControl.noStore());
        if (response.contentType().startsWith("text/html")) {
            // Nur eigene Skripte und Anfragen an CodePath; nicht in fremde Seiten einbettbar
            builder.header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
        }
        response.headers().forEach(builder::header);
        return builder.body(response.body());
    }

    private static Map<String, String> query(HttpServletRequest request) {
        Map<String, String> query = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> query.put(k, v.length > 0 ? v[0] : ""));
        return query;
    }

    private static Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name));
        }
        return headers;
    }
}

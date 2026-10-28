package de.codepath.backend.report;

import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final TakeHomeService takeHomeService;
    private final UserService userService;
    private final SettingsService settings;

    @GetMapping("/api/reports/my-report")
    public ResponseEntity<byte[]> myReport() {
        User user = userService.currentUser();
        if (!user.isTeacher() && !settings.isEnabled(SettingsService.CERTIFICATES_ENABLED)) {
            throw ApiException.forbidden("Die Zeugnisse sind noch nicht freigegeben.");
        }
        return file(reportService.certificate(user), "Teilnahmebestaetigung_" + user.getUsername() + ".pdf", MediaType.APPLICATION_PDF);
    }

    /** Mitnehm-Paket der angemeldeten Person; entsteht bei jedem Abruf neu und wird nicht gespeichert. */
    @GetMapping("/api/reports/take-home")
    public ResponseEntity<byte[]> takeHome() {
        User user = userService.currentUser();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Mitnehm-Paket_" + user.getUsername() + ".pdf")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .body(takeHomeService.render(user));
    }

    /** Berufe-Kompass der angemeldeten Person. */
    @GetMapping("/api/compass")
    public ReportService.Compass compass() {
        return reportService.compass(userService.currentUser());
    }

    @GetMapping("/api/admin/students/{username}/report")
    public ResponseEntity<byte[]> studentReport(@PathVariable("username") String username) {
        User user = userService.byUsername(username);
        return file(reportService.certificate(user), "Teilnahmebestaetigung_" + user.getUsername() + ".pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/api/admin/courses/{id}/certificates")
    public ResponseEntity<byte[]> courseCertificates(@PathVariable("id") Long courseId) {
        return file(reportService.courseCertificates(courseId), "Teilnahmebestaetigungen_Kurs_" + courseId + ".zip",
                MediaType.parseMediaType("application/zip"));
    }

    private static ResponseEntity<byte[]> file(byte[] content, String name, MediaType type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name)
                .contentType(type)
                .body(content);
    }
}

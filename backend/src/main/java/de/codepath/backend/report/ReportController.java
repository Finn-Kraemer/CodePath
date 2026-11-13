package de.codepath.backend.report;

import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.pdf.PdfBundles;
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

    /** Alle Teilnahmebestätigungen eines Kurses: format=zip (Standard) oder pdf (eine Datei zum Drucken). */
    @GetMapping("/api/admin/courses/{id}/certificates")
    public ResponseEntity<byte[]> courseCertificates(@PathVariable("id") Long courseId,
                                                     @RequestParam(name = "format", required = false) String format,
                                                     @RequestParam(name = "duplex", defaultValue = "false") boolean duplex,
                                                     @RequestParam(name = "inline", defaultValue = "false") boolean inline) {
        return PdfBundles.respond("Teilnahmebestaetigungen_Kurs_" + courseId, reportService.courseCertificates(courseId),
                PdfBundles.Format.parse(format), duplex, inline);
    }

    /** Mitnehm-Pakete aller Schülerinnen und Schüler eines Kurses: format=zip (Standard) oder pdf. */
    @GetMapping("/api/admin/courses/{id}/take-home")
    public ResponseEntity<byte[]> courseTakeHome(@PathVariable("id") Long courseId,
                                                 @RequestParam(name = "format", required = false) String format,
                                                 @RequestParam(name = "duplex", defaultValue = "false") boolean duplex,
                                                 @RequestParam(name = "inline", defaultValue = "false") boolean inline) {
        return PdfBundles.respond("Mitnehm-Pakete_Kurs_" + courseId, takeHomeService.course(courseId),
                PdfBundles.Format.parse(format), duplex, inline);
    }

    private static ResponseEntity<byte[]> file(byte[] content, String name, MediaType type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name)
                .contentType(type)
                .body(content);
    }
}

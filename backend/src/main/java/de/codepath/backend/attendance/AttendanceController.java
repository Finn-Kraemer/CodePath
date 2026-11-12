package de.codepath.backend.attendance;

import de.codepath.backend.identity.CourseService;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.pdf.PdfBundles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Anwesenheit: Verwaltung nur für Lehrkräfte, eigene Anwesenheit für Schüler nur wenn freigeschaltet. */
@RestController
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService service;
    private final CourseService courseService;
    private final UserService userService;

    public record SessionRequest(Long courseId, LocalDate date, String title) {
    }

    public record EntryRequest(String status, String note) {
    }

    public record SettingsRequest(Boolean onCertificate, Boolean visibleToStudents) {
    }

    @GetMapping("/api/admin/attendance")
    public AttendanceService.AttendanceOverview overview(@RequestParam(name = "courseId", required = false) Long courseId) {
        return service.overview(resolveCourse(courseId));
    }

    @PostMapping("/api/admin/attendance/sessions")
    public AttendanceService.AttendanceSessionDetail create(@RequestBody SessionRequest request) {
        return service.create(resolveCourse(request.courseId()), request.date(), request.title());
    }

    @GetMapping("/api/admin/attendance/sessions/{id}")
    public AttendanceService.AttendanceSessionDetail detail(@PathVariable("id") Long id) {
        return service.detail(id);
    }

    @PutMapping("/api/admin/attendance/sessions/{id}")
    public AttendanceService.AttendanceSessionDetail update(@PathVariable("id") Long id, @RequestBody SessionRequest request) {
        return service.update(id, request.date(), request.title());
    }

    @DeleteMapping("/api/admin/attendance/sessions/{id}")
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }

    @PutMapping("/api/admin/attendance/sessions/{id}/entries/{userId}")
    public AttendanceService.AttendanceEntryView setEntry(@PathVariable("id") Long id, @PathVariable("userId") Long userId,
                                                @RequestBody EntryRequest request) {
        AttendanceStatus status;
        try {
            status = AttendanceStatus.valueOf(request.status());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("status: PRESENT, ABSENT, EXCUSED oder LATE");
        }
        return service.setEntry(id, userId, status, request.note());
    }

    @GetMapping("/api/admin/attendance/export.csv")
    public ResponseEntity<byte[]> csv(@RequestParam(name = "courseId", required = false) Long courseId) {
        Long id = resolveCourse(courseId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Anwesenheit_Kurs_" + id + ".csv", StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(service.csv(id).getBytes(StandardCharsets.UTF_8));
    }

    /** Je Person ein PDF: format=zip (Standard) oder pdf (eine Datei zum Drucken). */
    @GetMapping("/api/admin/attendance/export")
    public ResponseEntity<byte[]> pdfs(@RequestParam(name = "courseId", required = false) Long courseId,
                                       @RequestParam(name = "format", required = false) String format,
                                       @RequestParam(name = "duplex", defaultValue = "false") boolean duplex,
                                       @RequestParam(name = "inline", defaultValue = "false") boolean inline) {
        Long id = resolveCourse(courseId);
        return PdfBundles.respond("Anwesenheit_Kurs_" + id, service.pdfs(id), PdfBundles.Format.parse(format), duplex, inline);
    }

    @GetMapping("/api/admin/students/{username}/attendance")
    public AttendanceService.AttendancePersonView student(@PathVariable("username") String username) {
        return service.personView(userService.byUsername(username));
    }

    @GetMapping("/api/admin/settings/attendance")
    public AttendanceService.AttendanceSettings settings() {
        return service.settings();
    }

    @PutMapping("/api/admin/settings/attendance")
    public AttendanceService.AttendanceSettings updateSettings(@RequestBody SettingsRequest request) {
        return service.updateSettings(request.onCertificate(), request.visibleToStudents());
    }

    /** Eigene Anwesenheit, wenn die Lehrkraft sie sichtbar gemacht hat (sonst 403). */
    @GetMapping("/api/attendance/me")
    public AttendanceService.AttendancePersonView own() {
        return service.own(userService.currentUser());
    }

    /** Ob die eigene Anwesenheit sichtbar ist (für das Profil, ohne 403). */
    @GetMapping("/api/attendance/status")
    public java.util.Map<String, Boolean> status() {
        return java.util.Map.of("visible", service.settings().visibleToStudents());
    }

    private Long resolveCourse(Long courseId) {
        if (courseId != null) return courseId;
        return courseService.activeCourses().stream().findFirst()
                .orElseThrow(() -> ApiException.badRequest("Bitte zuerst einen Kurs anlegen."))
                .getId();
    }
}

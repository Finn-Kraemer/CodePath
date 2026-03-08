package de.codepath.backend.features.report;

import de.codepath.backend.users.User;
import de.codepath.backend.users.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final UserService userService;

    @GetMapping("/my-report")
    public ResponseEntity<byte[]> downloadMyReport() {
        User user = userService.getCurrentUser();
        byte[] pdf = reportService.generateStudentReport(user);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=CodePath_Zeugnis_" + user.getUsername() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}

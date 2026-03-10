package de.codepath.backend.features.report;

import de.codepath.backend.features.admin.AdminService;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRole;
import de.codepath.backend.users.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    private final AdminService adminService;

    @GetMapping("/my-report")
    public ResponseEntity<byte[]> downloadMyReport() {
        User user = userService.getCurrentUser();
        
        // Only allow if certificates are enabled or user is admin
        if (!adminService.areCertificatesEnabled() && user.getRole() != UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        byte[] pdf = reportService.generateStudentReport(user);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=CodePath_Zeugnis_" + user.getUsername() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}

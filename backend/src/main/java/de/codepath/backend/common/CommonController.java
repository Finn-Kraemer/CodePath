package de.codepath.backend.common;

import de.codepath.backend.features.admin.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class CommonController {

    private final GlobalAnnouncementRepository announcementRepository;
    private final AdminService adminService;

    @GetMapping("/announcement")
    public Map<String, String> getAnnouncement() {
        return announcementRepository.findFirstByOrderByUpdatedAtDesc()
                .map(a -> Map.of(
                    "content", a.getContent(), 
                    "displayMode", a.getDisplayMode().name(),
                    "updatedAt", a.getUpdatedAt().toString()))
                .orElse(Map.of("content", "", "displayMode", "HEADER"));
    }

    @GetMapping("/settings/certificates")
    public Map<String, Boolean> getCertificateStatus() {
        return Map.of("enabled", adminService.areCertificatesEnabled());
    }
}

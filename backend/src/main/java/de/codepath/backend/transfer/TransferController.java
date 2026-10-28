package de.codepath.backend.transfer;

import de.codepath.backend.identity.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Zentraler Export und Import für die Lehrkraft (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final UserService userService;

    @GetMapping("/sections")
    public List<TransferService.SectionInfo> sections() {
        return transferService.sections();
    }

    /**
     * @param sections Bereiche durch Komma getrennt, leer für alle
     * @param only     Slugs einzelner Einträge durch Komma getrennt, z. B. eine Seite
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(name = "sections", defaultValue = "") String sections,
                                         @RequestParam(name = "only", defaultValue = "") String only) {
        List<String> keys = split(sections).stream().toList();
        String name = "codepath-export-" + (keys.size() == 1 ? keys.getFirst() + "-" : "") + LocalDate.now() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(transferService.export(keys, split(only)));
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TransferService.TransferReport importFile(@RequestParam("file") MultipartFile file) throws IOException {
        return transferService.importFile(file.getBytes(), userService.currentUser().getId());
    }

    private static Set<String> split(String value) {
        return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}

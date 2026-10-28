package de.codepath.backend.content;

import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;
    private final UserService userService;

    /** Bilder für angemeldete Nutzer. Der Inhalt zu einem Schlüssel ändert sich nie – daher lange cachen. */
    @GetMapping("/api/media/{key}")
    public ResponseEntity<byte[]> media(@PathVariable("key") String key) {
        MediaFile file = mediaService.get(key).orElseThrow(() -> ApiException.notFound("Bild nicht gefunden."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(file.getData());
    }

    @GetMapping("/api/admin/media")
    public List<MediaService.MediaInfo> list() {
        return mediaService.list();
    }

    @PostMapping(path = "/api/admin/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MediaService.MediaInfo upload(@RequestParam("file") MultipartFile file) throws IOException {
        return mediaService.store(file.getBytes(), file.getOriginalFilename(), userService.currentUser().getId());
    }

    @DeleteMapping("/api/admin/media/{key}")
    public ResponseEntity<Void> delete(@PathVariable("key") String key) {
        mediaService.delete(key);
        return ResponseEntity.noContent().build();
    }
}

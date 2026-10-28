package de.codepath.backend.content;

import de.codepath.backend.engine.MediaKeys;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.platform.media.MediaUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Bilder für Aufgaben. Erlaubt sind nur PNG, JPEG, GIF und WebP – erkannt am Inhalt, nicht an
 * Endung oder Content-Type des Browsers. SVG ist bewusst ausgeschlossen (kann Skripte enthalten).
 */
@Service
@RequiredArgsConstructor
public class MediaService {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_PIXELS = 4096;

    public record MediaInfo(String key, String url, String contentType, String originalName, int size,
                            Integer width, Integer height) {
    }

    private final MediaRepository repository;
    private final AuditService auditService;
    private final List<MediaUsage> mediaUsages;

    /** Geprüftes Bild: Schlüssel, Endung, Maße. */
    public record Inspected(String key, String extension, int[] size) {
    }

    /**
     * Prüft ein Bild, ohne es zu speichern. Bewusst ohne Transaktion: Eine Ablehnung hier darf eine
     * laufende Transaktion (z. B. den Import eines ganzen Pakets) nicht zum Rollback markieren.
     */
    public Inspected inspect(byte[] data) {
        if (data == null || data.length == 0) throw ApiException.badRequest("Die Datei ist leer.");
        if (data.length > MAX_BYTES) throw ApiException.badRequest("Bilder dürfen höchstens 2 MB groß sein.");
        String extension = detectExtension(data);
        if (extension == null) throw ApiException.badRequest("Erlaubt sind PNG, JPEG, GIF und WebP.");
        int[] size = dimensions(data, extension);
        if (size != null && (size[0] > MAX_PIXELS || size[1] > MAX_PIXELS)) {
            throw ApiException.badRequest("Das Bild ist zu groß (höchstens " + MAX_PIXELS + " Pixel pro Seite).");
        }
        return new Inspected(sha256(data) + "." + extension, extension, size);
    }

    @Transactional
    public MediaInfo store(byte[] data, String originalName, Long actorId) {
        return save(inspect(data), data, originalName, actorId);
    }

    @Transactional
    public MediaInfo save(Inspected inspected, byte[] data, String originalName, Long actorId) {
        Optional<MediaFile> existing = repository.findById(inspected.key());
        if (existing.isPresent()) return info(existing.get());

        MediaFile file = new MediaFile();
        file.setKey(inspected.key());
        file.setContentType(contentType(inspected.extension()));
        file.setOriginalName(cleanName(originalName));
        file.setSize(data.length);
        file.setData(data);
        if (inspected.size() != null) {
            file.setWidth(inspected.size()[0]);
            file.setHeight(inspected.size()[1]);
        }
        file.setCreatedBy(actorId);
        repository.save(file);
        auditService.log("MEDIA_UPLOADED", inspected.key(), Map.of("size", data.length));
        return info(file);
    }

    @Transactional(readOnly = true)
    public Optional<MediaFile> get(String key) {
        if (!MediaKeys.isKey(key)) return Optional.empty();
        return repository.findById(key);
    }

    @Transactional(readOnly = true)
    public List<MediaInfo> list() {
        return repository.listInfo().stream()
                .map(i -> new MediaInfo(i.getKey(), url(i.getKey()), i.getContentType(), i.getOriginalName(), i.getSize(), i.getWidth(), i.getHeight()))
                .toList();
    }

    /** Welche der Schlüssel fehlen in der Datenbank? */
    @Transactional(readOnly = true)
    public Set<String> missing(Collection<String> keys) {
        if (keys.isEmpty()) return Set.of();
        Set<String> missing = new LinkedHashSet<>(keys);
        missing.removeAll(repository.existingKeys(keys));
        return missing;
    }

    @Transactional
    public void delete(String key) {
        MediaFile file = get(key).orElseThrow(() -> ApiException.notFound("Bild nicht gefunden."));
        if (repository.countReferences(key) > 0) {
            throw ApiException.conflict("Das Bild wird noch in einer Aufgabe (oder einer früheren Version) verwendet.");
        }
        for (MediaUsage usage : mediaUsages) {
            Optional<String> where = usage.usedBy(key);
            if (where.isPresent()) throw ApiException.conflict("Das Bild wird noch " + where.get() + " verwendet.");
        }
        repository.delete(file);
        auditService.log("MEDIA_DELETED", key, Map.of());
    }

    public static String url(String key) {
        return "/api/media/" + key;
    }

    static String detectExtension(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "png";
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "jpg";
        if (d.length >= 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') return "gif";
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "webp";
        return null;
    }

    static String contentType(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg" -> "image/jpeg";
            case "gif" -> "image/gif";
            default -> "image/webp";
        };
    }

    /** Breite und Höhe; ein Bild, das sich nicht lesen lässt, wird abgelehnt. WebP liest Java nicht – dort null. */
    private static int[] dimensions(byte[] data, String extension) {
        if (extension.equals("webp")) return null;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) throw ApiException.badRequest("Das Bild ist beschädigt.");
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException e) {
            throw ApiException.badRequest("Das Bild ist beschädigt.");
        }
    }

    private static String cleanName(String name) {
        if (name == null) return null;
        String base = name.replaceAll(".*[/\\\\]", "").replaceAll("[^\\p{L}\\p{N} ._()-]", "_");
        return base.length() > 200 ? base.substring(0, 200) : base;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MediaInfo info(MediaFile f) {
        return new MediaInfo(f.getKey(), url(f.getKey()), f.getContentType(), f.getOriginalName(), f.getSize(), f.getWidth(), f.getHeight());
    }
}

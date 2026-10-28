package de.codepath.backend.transfer;

import de.codepath.backend.content.ContentEditorService;
import de.codepath.backend.content.MediaService;
import de.codepath.backend.engine.MediaKeys;
import de.codepath.backend.platform.ApiException;
import de.codepath.backend.platform.audit.AuditService;
import de.codepath.backend.platform.transfer.TransferItem;
import de.codepath.backend.platform.transfer.TransferSection;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Zentraler Export und Import. Eine Exportdatei ist ein ZIP mit {@code codepath-export.json} (alle gewählten
 * Bereiche) und den verwendeten Bildern unter {@code media/}. Der Import erkennt selbst, was er bekommt:
 * <ul>
 *     <li>CodePath-Export als ZIP oder als reine JSON-Datei,</li>
 *     <li>ein Content-Paket (Ordner mit module.json und tasks/), wie es der bisherige Inhalte-Export erzeugt.</li>
 * </ul>
 * Jeder Eintrag landet mit Ergebnis (angelegt, überarbeitet, unverändert, Fehler) im Bericht.
 */
@Service
public class TransferService {

    static final String FORMAT = "codepath-export";
    static final int VERSION = 1;
    static final String MANIFEST = "codepath-export.json";
    static final long MAX_BYTES = 50L * 1024 * 1024;
    static final int MAX_ENTRIES = 5000;
    static final String MEDIA_AREA = "Bilder";

    public record SectionInfo(String key, String label) {
    }

    /** detected: was die Datei war, z. B. "CodePath-Export (ZIP)" */
    public record TransferReport(String detected, int created, int updated, int unchanged, int errors, List<TransferItem> items) {
        static TransferReport of(String detected, List<TransferItem> items) {
            Map<TransferItem.Action, Integer> counts = new EnumMap<>(TransferItem.Action.class);
            items.forEach(i -> counts.merge(i.action(), 1, Integer::sum));
            return new TransferReport(detected, counts.getOrDefault(TransferItem.Action.CREATED, 0), counts.getOrDefault(TransferItem.Action.UPDATED, 0),
                    counts.getOrDefault(TransferItem.Action.UNCHANGED, 0), counts.getOrDefault(TransferItem.Action.ERROR, 0), items);
        }
    }

    private final List<TransferSection> sections;
    private final MediaService mediaService;
    private final ContentEditorService contentEditor;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public TransferService(List<TransferSection> sections, MediaService mediaService, ContentEditorService contentEditor,
                           AuditService auditService, ObjectMapper objectMapper) {
        this.sections = sections.stream().sorted(Comparator.comparingInt(TransferSection::order)).toList();
        this.mediaService = mediaService;
        this.contentEditor = contentEditor;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<SectionInfo> sections() {
        return sections.stream().map(s -> new SectionInfo(s.key(), s.label())).toList();
    }

    // ---------------------------------------------------------------- Export

    /**
     * @param keys Bereiche; leer für alle
     * @param only Slugs einzelner Einträge (z. B. eine Seite); leer für alle Einträge der Bereiche
     */
    public byte[] export(Collection<String> keys, Set<String> only) {
        Set<String> known = new HashSet<>();
        sections.forEach(s -> known.add(s.key()));
        for (String key : keys) if (!known.contains(key)) throw ApiException.badRequest("Unbekannter Bereich: " + key);

        ObjectNode manifest = objectMapper.createObjectNode();
        manifest.put("format", FORMAT);
        manifest.put("version", VERSION);
        manifest.put("createdAt", LocalDateTime.now().withNano(0).toString());
        ObjectNode content = manifest.putObject("sections");
        for (TransferSection section : sections) {
            if (!keys.isEmpty() && !keys.contains(section.key())) continue;
            content.set(section.key(), section.export(only));
        }
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(MANIFEST));
            zip.write((json + "\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (String key : MediaKeys.find(json)) {
                var image = mediaService.get(key);
                if (image.isEmpty()) continue;
                zip.putNextEntry(new ZipEntry("media/" + key));
                zip.write(image.get().getData());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw ApiException.badRequest("Export fehlgeschlagen.");
        }
        auditService.log("DATA_EXPORTED", String.join(",", keys.isEmpty() ? List.of("alle") : keys), Map.of());
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- Import

    public TransferReport importFile(byte[] data, Long actorId) {
        if (data == null || data.length == 0) throw ApiException.badRequest("Die Datei ist leer.");
        TransferReport report;
        if (isZip(data)) {
            report = importZip(data, actorId);
        } else if (looksLikeJson(data)) {
            JsonNode manifest = parse(data);
            if (!isManifest(manifest)) {
                throw ApiException.badRequest("Die JSON-Datei ist kein CodePath-Export (\"format\": \"" + FORMAT + "\" fehlt).");
            }
            report = importManifest("CodePath-Export (JSON)", manifest, Map.of(), actorId);
        } else {
            throw ApiException.badRequest("Die Datei wurde nicht erkannt. Erwartet wird ein CodePath-Export (ZIP oder JSON) oder ein Content-Paket (ZIP).");
        }
        auditService.log("DATA_IMPORTED", report.detected(), Map.of("created", report.created(), "updated", report.updated(),
                "unchanged", report.unchanged(), "errors", report.errors()));
        return report;
    }

    private TransferReport importZip(byte[] data, Long actorId) {
        Map<String, byte[]> files = unzip(data);
        Optional<String> manifestPath = files.keySet().stream()
                .filter(p -> p.equals(MANIFEST) || p.endsWith("/" + MANIFEST))
                .min(Comparator.comparingInt(String::length));
        if (manifestPath.isPresent()) {
            String root = manifestPath.get().substring(0, manifestPath.get().length() - MANIFEST.length());
            JsonNode manifest = parse(files.get(manifestPath.get()));
            if (!isManifest(manifest)) throw ApiException.badRequest(MANIFEST + " ist kein gültiger CodePath-Export.");
            Map<String, byte[]> media = new TreeMap<>();
            files.forEach((path, bytes) -> {
                if (path.startsWith(root + "media/") && path.indexOf('/', root.length() + 6) < 0) media.put(path.substring(root.length() + 6), bytes);
            });
            return importManifest("CodePath-Export (ZIP)", manifest, media, actorId);
        }
        if (files.keySet().stream().anyMatch(p -> p.equals("module.json") || p.endsWith("/module.json"))) {
            var report = contentEditor.importZip(new ByteArrayInputStream(data), actorId);
            return TransferReport.of("Content-Paket (Module und Aufgaben)", report.items());
        }
        throw ApiException.badRequest("Das ZIP enthält weder " + MANIFEST + " noch ein Content-Paket (module.json).");
    }

    private TransferReport importManifest(String detected, JsonNode manifest, Map<String, byte[]> media, Long actorId) {
        int version = manifest.path("version").asInt(0);
        if (version < 1 || version > VERSION) {
            throw ApiException.badRequest("Die Exportdatei hat Version " + version + "; diese CodePath-Version kann Version 1 bis " + VERSION + " lesen.");
        }
        List<TransferItem> items = new ArrayList<>(importMedia(media, actorId));
        JsonNode content = manifest.path("sections");
        Set<String> known = new HashSet<>();
        for (TransferSection section : sections) {
            known.add(section.key());
            JsonNode data = content.get(section.key());
            if (data == null || data.isNull()) continue;
            try {
                items.addAll(section.importData(data, actorId));
            } catch (RuntimeException e) {
                items.add(TransferItem.error(section.label(), "-", "Bereich konnte nicht gelesen werden: " + e.getMessage()));
            }
        }
        for (Map.Entry<String, JsonNode> entry : content.properties()) {
            if (!known.contains(entry.getKey())) {
                items.add(TransferItem.error(entry.getKey(), "-", "Unbekannter Bereich – übersprungen (Datei aus einer neueren Version?)"));
            }
        }
        return TransferReport.of(detected, items);
    }

    /** Bilder zuerst, damit Seiten, Artikel und Aufgaben sie beim Prüfen schon finden. */
    private List<TransferItem> importMedia(Map<String, byte[]> media, Long actorId) {
        List<TransferItem> items = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : media.entrySet()) {
            String key = entry.getKey();
            try {
                if (!MediaKeys.isKey(key)) throw ApiException.badRequest("Kein gültiger Bildname.");
                MediaService.Inspected inspected = mediaService.inspect(entry.getValue());
                if (!inspected.key().equals(key)) throw ApiException.badRequest("Inhalt passt nicht zum Namen.");
                boolean isNew = !mediaService.missing(List.of(key)).isEmpty();
                mediaService.save(inspected, entry.getValue(), key, actorId);
                items.add(isNew ? TransferItem.created(MEDIA_AREA, key) : TransferItem.unchanged(MEDIA_AREA, key));
            } catch (ApiException e) {
                items.add(TransferItem.error(MEDIA_AREA, key, e.getMessage()));
            }
        }
        return items;
    }

    private Map<String, byte[]> unzip(byte[] data) {
        Map<String, byte[]> files = new TreeMap<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw ApiException.badRequest("Die Datei enthält zu viele Einträge.");
                if (entry.isDirectory() || entry.getName().contains("..")) continue;
                byte[] content = zip.readNBytes((int) Math.min(MAX_BYTES - total + 1, Integer.MAX_VALUE - 8));
                total += content.length;
                if (total > MAX_BYTES) throw ApiException.badRequest("Die Datei ist zu groß (höchstens 50 MB entpackt).");
                files.put(entry.getName().replace('\\', '/'), content);
            }
        } catch (IOException e) {
            throw ApiException.badRequest("Das ZIP-Archiv konnte nicht gelesen werden.");
        }
        return files;
    }

    private JsonNode parse(byte[] data) {
        try {
            return objectMapper.readTree(new String(data, StandardCharsets.UTF_8).replaceFirst("^﻿", ""));
        } catch (JacksonException e) {
            throw ApiException.badRequest("Die Datei ist kein gültiges JSON: " + e.getOriginalMessage());
        }
    }

    private static boolean isManifest(JsonNode node) {
        return node != null && node.isObject() && FORMAT.equals(node.path("format").asString("")) && node.path("sections").isObject();
    }

    static boolean isZip(byte[] data) {
        return data.length >= 4 && data[0] == 'P' && data[1] == 'K' && data[2] == 3 && data[3] == 4;
    }

    static boolean looksLikeJson(byte[] data) {
        for (int i = 0; i < Math.min(data.length, 64); i++) {
            int b = data[i] & 0xff;
            if (b == '{') return true;
            if (!(Character.isWhitespace(b) || b == 0xEF || b == 0xBB || b == 0xBF)) return false;
        }
        return false;
    }
}

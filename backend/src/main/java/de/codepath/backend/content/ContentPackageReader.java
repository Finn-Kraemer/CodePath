package de.codepath.backend.content;

import de.codepath.backend.platform.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Liest Content-Pakete aus dem Classpath (Startinhalt im JAR) oder aus einem hochgeladenen ZIP.
 * Erwartete Struktur: &lt;modul&gt;/module.json, &lt;modul&gt;/tasks/*.json und &lt;modul&gt;/media/*, optional unter einem
 * gemeinsamen Wurzelordner.
 */
@Component
@RequiredArgsConstructor
public class ContentPackageReader {

    private static final long MAX_ZIP_BYTES = 20 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 2000;

    private final ObjectMapper objectMapper;

    public List<ContentPackage.ModuleBundle> fromClasspath(String root) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Map<String, byte[]> files = new TreeMap<>();
        for (Resource resource : resolver.getResources("classpath*:" + root + "/*/module.json")) {
            files.put(relativePath(resource, root), resource.getContentAsByteArray());
        }
        for (Resource resource : resolver.getResources("classpath*:" + root + "/*/tasks/*.json")) {
            files.put(relativePath(resource, root), resource.getContentAsByteArray());
        }
        for (Resource resource : resolver.getResources("classpath*:" + root + "/*/media/*")) {
            files.put(relativePath(resource, root), resource.getContentAsByteArray());
        }
        return parse(files);
    }

    private static String relativePath(Resource resource, String root) throws IOException {
        String url = resource.getURL().toString();
        int index = url.lastIndexOf("/" + root + "/");
        return url.substring(index + root.length() + 2);
    }

    public List<ContentPackage.ModuleBundle> fromZip(InputStream input) {
        Map<String, byte[]> files = new TreeMap<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) throw ApiException.badRequest("Das Paket enthält zu viele Dateien.");
                if (entry.isDirectory() || !(entry.getName().endsWith(".json") || isMediaPath(entry.getName()))) continue;
                byte[] content = zip.readNBytes((int) Math.min(MAX_ZIP_BYTES - total + 1, Integer.MAX_VALUE - 8));
                total += content.length;
                if (total > MAX_ZIP_BYTES) throw ApiException.badRequest("Das Paket ist zu groß (höchstens 20 MB entpackt).");
                files.put(entry.getName(), content);
            }
        } catch (IOException e) {
            throw ApiException.badRequest("Das ZIP-Archiv konnte nicht gelesen werden.");
        }
        // Einen gemeinsamen Wurzelordner (z. B. "content/") abschneiden
        Map<String, byte[]> normalized = new TreeMap<>();
        files.forEach((path, bytes) -> {
            List<String> parts = new ArrayList<>(List.of(path.split("/")));
            int folder = Math.max(parts.lastIndexOf("tasks"), parts.lastIndexOf("media"));
            int moduleIndex = folder >= 1 ? folder - 1 : parts.size() - 2;
            if (moduleIndex < 0) return;
            normalized.put(String.join("/", parts.subList(moduleIndex, parts.size())), bytes);
        });
        return parse(normalized);
    }

    private static boolean isMediaPath(String name) {
        return name.contains("/media/") && name.matches(".*\\.(png|jpg|gif|webp)");
    }

    private List<ContentPackage.ModuleBundle> parse(Map<String, byte[]> files) {
        Map<String, ContentPackage.ModuleFile> modules = new TreeMap<>();
        Map<String, List<ContentPackage.TaskFile>> tasks = new TreeMap<>();
        Map<String, Map<String, byte[]>> media = new TreeMap<>();
        for (var entry : files.entrySet()) {
            String[] parts = entry.getKey().split("/");
            try {
                if (parts.length == 2 && parts[1].equals("module.json")) {
                    modules.put(parts[0], objectMapper.readValue(entry.getValue(), ContentPackage.ModuleFile.class));
                } else if (parts.length == 3 && parts[1].equals("tasks")) {
                    tasks.computeIfAbsent(parts[0], k -> new ArrayList<>())
                            .add(objectMapper.readValue(entry.getValue(), ContentPackage.TaskFile.class));
                } else if (parts.length == 3 && parts[1].equals("media")) {
                    media.computeIfAbsent(parts[0], k -> new TreeMap<>()).put(parts[2], entry.getValue());
                }
            } catch (RuntimeException e) {
                throw ApiException.badRequest("Datei " + entry.getKey() + " ist kein gültiges JSON: " + e.getMessage());
            }
        }
        List<ContentPackage.ModuleBundle> bundles = new ArrayList<>();
        modules.forEach((dir, module) -> bundles.add(new ContentPackage.ModuleBundle(module,
                tasks.getOrDefault(dir, List.of()), media.getOrDefault(dir, Map.of()))));
        bundles.sort(Comparator.comparing(b -> Optional.ofNullable(b.module().sortOrder()).orElse(0)));
        return bundles;
    }
}

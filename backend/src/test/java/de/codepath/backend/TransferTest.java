package de.codepath.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Zentraler Export und Import: Erkennung der Datei und Bericht je Eintrag. */
class TransferTest extends IntegrationTestBase {

    @BeforeEach
    void clear() {
        jdbc.update("delete from pages");
        jdbc.update("delete from learning_paths");
        jdbc.update("delete from knowledge_articles where slug like 'transfer-%'");
    }

    byte[] export(String query) throws Exception {
        return getAs(teacher(), "/api/admin/transfer/export" + query).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    JsonNode importFile(String name, byte[] data, int expectedStatus) throws Exception {
        return body(mvc.perform(multipart("/api/admin/transfer/import").file(new MockMultipartFile("file", name, "application/octet-stream", data))
                .with(teacher()).with(csrf())).andExpect(status().is(expectedStatus)));
    }

    static Map<String, byte[]> unzip(byte[] zip) throws Exception {
        Map<String, byte[]> files = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) files.put(entry.getName(), in.readAllBytes());
        }
        return files;
    }

    static List<String> actions(JsonNode report, String area) {
        List<String> result = new ArrayList<>();
        for (JsonNode item : report.get("items")) {
            if (item.get("area").asString().equals(area)) result.add(item.get("name").asString() + ":" + item.get("action").asString());
        }
        return result;
    }

    @Test
    void exportAndImportReportCreatedUpdatedAndUnchanged() throws Exception {
        long page = body(postAs(teacher(), "/api/admin/pages", Map.of("title", "Anfahrt", "slug", "anfahrt"))).get("id").asLong();
        byte[] image = MediaTest.png(12, 8, 0x336699);
        String key = body(mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "bild.png", "image/png", image))
                .with(teacher()).with(csrf()))).get("key").asString();
        putAs(teacher(), "/api/admin/pages/" + page + "/published", PagesTest.blocks(Map.of("id", "i", "type", "image", "media", key)))
                .andExpect(status().isNoContent());
        long article = body(postAs(teacher(), "/api/admin/knowledge", Map.of("title", "Transfer", "slug", "transfer-test"))).get("id").asLong();
        long blockId = body(postAs(teacher(), "/api/admin/learning-paths", Map.of("title", "Baustein DB", "slug", "db-baustein")).andExpect(status().isOk())).get("id").asLong();
        putAs(teacher(), "/api/admin/learning-paths/" + blockId, Map.of(
                "templateOnly", true,
                "graph", Map.of(
                        "nodes", List.of(LearningPathTest.node("b1", "MODULE", "datenbanken", null, null, 0)),
                        "edges", List.of()))).andExpect(status().isOk());
        long pathId = body(postAs(teacher(), "/api/admin/learning-paths", Map.of("title", "Standard", "slug", "standard")).andExpect(status().isOk())).get("id").asLong();
        Map<String, Object> orGate = new java.util.HashMap<>(LearningPathTest.node("or1", "OR", null, null, "Wahl", 100));
        orGate.put("min", 2);
        putAs(teacher(), "/api/admin/learning-paths/" + pathId, Map.of(
                "enforce", true,
                "hideOverview", true,
                "unlockRestAfter", "m3",
                "graph", Map.of(
                        "nodes", List.of(
                                LearningPathTest.node("c1", "CHOICE", null, null, "Weiche", 0),
                                LearningPathTest.node("m1", "MODULE", "git", null, null, 50),
                                LearningPathTest.pathNode("m2", "db-baustein", 50),
                                orGate,
                                LearningPathTest.bossNode("m3", "TASK", "git", "g3", "Ziel-Boss", 200, 80, "TOTAL", null, "TEACHER", null)),
                        "edges", List.of(
                                Map.of("from", "c1", "to", "m1", "required", true, "label", "Git-Weg", "hint", "Tipp A"),
                                Map.of("from", "c1", "to", "m2", "required", true, "label", "DB-Weg"),
                                LearningPathTest.requiredEdge("m1", "or1"),
                                LearningPathTest.requiredEdge("m2", "or1"),
                                LearningPathTest.requiredEdge("or1", "m3"))))).andExpect(status().isOk());

        byte[] zip = export("");
        Map<String, byte[]> files = unzip(zip);
        assertTrue(files.containsKey("media/" + key), "Verwendete Bilder werden mitgepackt");
        JsonNode manifest = json.readTree(files.get("codepath-export.json"));
        assertEquals("codepath-export", manifest.get("format").asString());
        for (String section : List.of("settings", "modules", "homepage", "pages", "knowledge", "learningPaths")) {
            assertTrue(manifest.at("/sections").has(section), section);
        }
        assertEquals("db-baustein", manifest.at("/sections/learningPaths/0/slug").asString());
        assertTrue(manifest.at("/sections/learningPaths/0/templateOnly").asBoolean());
        assertTrue(manifest.at("/sections/learningPaths/1/enforce").asBoolean());
        assertTrue(manifest.at("/sections/learningPaths/1/hideOverview").asBoolean());
        assertEquals("m3", manifest.at("/sections/learningPaths/1/unlockRestAfter").asString());
        assertEquals("CHOICE", manifest.at("/sections/learningPaths/1/graph/nodes/0/kind").asString());
        assertEquals("PATH", manifest.at("/sections/learningPaths/1/graph/nodes/2/kind").asString());
        assertEquals("db-baustein", manifest.at("/sections/learningPaths/1/graph/nodes/2/path").asString());
        assertEquals("OR", manifest.at("/sections/learningPaths/1/graph/nodes/3/kind").asString());
        assertEquals(2, manifest.at("/sections/learningPaths/1/graph/nodes/3/min").asInt());
        assertTrue(manifest.at("/sections/learningPaths/1/graph/nodes/4/boss").asBoolean());
        assertEquals(80, manifest.at("/sections/learningPaths/1/graph/nodes/4/requirement/minPercent").asInt());
        assertEquals("TOTAL", manifest.at("/sections/learningPaths/1/graph/nodes/4/requirement/mode").asString());
        assertTrue(manifest.at("/sections/learningPaths/1/graph/edges/0/required").asBoolean());
        assertEquals("Git-Weg", manifest.at("/sections/learningPaths/1/graph/edges/0/label").asString());
        assertEquals("Tipp A", manifest.at("/sections/learningPaths/1/graph/edges/0/hint").asString());

        // Seite und Lernpfad weg, Artikel geändert → angelegt, überarbeitet, Rest unverändert
        mvc.perform(delete("/api/admin/pages/" + page).with(teacher()).with(csrf())).andExpect(status().isNoContent());
        jdbc.update("delete from learning_paths");
        putAs(teacher(), "/api/admin/knowledge/" + article, Map.of("body", "geändert")).andExpect(status().isOk());

        JsonNode report = importFile("export.zip", zip, 200);
        assertEquals("CodePath-Export (ZIP)", report.get("detected").asString());
        assertEquals(List.of("anfahrt:CREATED"), actions(report, "Seiten"));
        assertEquals(List.of("db-baustein:CREATED", "standard:CREATED"), actions(report, "Lernpfade"));
        assertTrue(jdbc.queryForObject("select hide_overview from learning_paths where slug = 'standard'", Boolean.class));
        String importedGraph = jdbc.queryForObject("select graph from learning_paths where slug = 'standard'", String.class);
        assertTrue(importedGraph.contains("\"boss\":true"));
        assertTrue(importedGraph.contains("\"minPercent\":80"));
        assertTrue(actions(report, "Wissensdatenbank").contains("transfer-test:UPDATED"));
        assertTrue(actions(report, "Module").stream().allMatch(a -> a.endsWith(":UNCHANGED")));
        assertTrue(actions(report, "Aufgaben").stream().allMatch(a -> a.endsWith(":UNCHANGED")));
        assertTrue(actions(report, "Bilder").contains(key + ":UNCHANGED"));
        assertEquals(0, report.get("errors").asInt());
        assertEquals(key, body(getAs(teacher(), "/api/public/pages/anfahrt")).at("/content/blocks/0/media").asString());

        JsonNode again = importFile("export.zip", zip, 200);
        assertEquals(0, again.get("created").asInt());
        assertEquals(0, again.get("updated").asInt());
        assertTrue(again.get("unchanged").asInt() > 100);
    }

    @Test
    void singleSectionAsJsonAndErrorsDoNotStopTheRest() throws Exception {
        postAs(teacher(), "/api/admin/pages", Map.of("title", "Impressum", "slug", "impressum")).andExpect(status().isOk());
        JsonNode manifest = json.readTree(unzip(export("?sections=pages&only=impressum")).get("codepath-export.json"));
        assertEquals(Set.of("pages"), iterator(manifest.get("sections").propertyNames()));
        assertEquals(1, manifest.at("/sections/pages").size());

        String file = """
                {"format": "codepath-export", "version": 1, "sections": {
                  "pages": [{"slug": "neu", "title": "Neu", "draft": {"blocks": []}},
                            {"slug": "../kaputt", "title": "Kaputt", "draft": {"blocks": []}}],
                  "knowledge": [{"slug": "transfer-ohne-modul", "title": "Ohne", "body": "x", "moduleSlug": "gibt-es-nicht", "published": true}],
                  "settings": {"feedback_enabled": true, "geheim": true},
                  "zukunft": []
                }}""";
        JsonNode report = importFile("seiten.json", file.getBytes(StandardCharsets.UTF_8), 200);
        assertEquals("CodePath-Export (JSON)", report.get("detected").asString());
        assertEquals(List.of("neu:CREATED", "../kaputt:ERROR"), actions(report, "Seiten"));
        assertEquals(List.of("transfer-ohne-modul:ERROR"), actions(report, "Wissensdatenbank"));
        assertEquals(List.of("-:ERROR"), actions(report, "zukunft"));
        assertTrue(actions(report, "Einstellungen").contains("geheim:ERROR"));
        assertEquals(4, report.get("errors").asInt());
        assertEquals(2, body(getAs(teacher(), "/api/admin/pages")).size(), "Der gültige Eintrag ist trotz Fehlern angelegt");
        assertEquals(0, jdbc.queryForObject("select count(*) from knowledge_articles where slug = 'transfer-ohne-modul'", Integer.class));
    }

    @Test
    void recognisesContentPackagesAndRejectsUnknownFiles() throws Exception {
        byte[] content = getAs(teacher(), "/api/admin/content/export").andReturn().getResponse().getContentAsByteArray();
        JsonNode report = importFile("codepath-content.zip", content, 200);
        assertEquals("Content-Paket (Module und Aufgaben)", report.get("detected").asString());
        assertEquals(0, report.get("errors").asInt());
        assertTrue(report.get("unchanged").asInt() > 100);

        importFile("bild.png", MediaTest.png(4, 4, 0), 400);
        importFile("leer.json", "{\"hallo\": 1}".getBytes(StandardCharsets.UTF_8), 400);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("notizen.txt"));
            zip.write("nichts".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        importFile("fremd.zip", out.toByteArray(), 400);
        importFile("neu.json", "{\"format\": \"codepath-export\", \"version\": 99, \"sections\": {}}".getBytes(StandardCharsets.UTF_8), 400);
    }

    static Set<String> iterator(Iterable<String> names) {
        Set<String> result = new HashSet<>();
        names.forEach(result::add);
        return result;
    }
}

package de.codepath.backend;

import de.codepath.backend.engine.MediaKeys;
import de.codepath.backend.identity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Bilder hochladen, ausliefern, löschen und in Content-Paketen mitnehmen. */
class MediaTest extends IntegrationTestBase {

    static byte[] png(int width, int height, int rgb) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, rgb);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    JsonNode upload(byte[] data, String name, int expectedStatus) throws Exception {
        var result = mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", name, "image/png", data))
                .with(teacher()).with(csrf())).andExpect(status().is(expectedStatus));
        return body(result);
    }

    @Test
    void uploadDeduplicatesAndServesToLoggedInUsers() throws Exception {
        byte[] image = png(40, 20, 0x123456);
        JsonNode info = upload(image, "../../geheim/Bild 1.png", 200);
        String key = info.get("key").asText();
        assertTrue(MediaKeys.isKey(key));
        assertEquals(40, info.get("width").asInt());
        assertEquals("Bild 1.png", info.get("originalName").asText());
        assertEquals(key, upload(image, "nochmal.png", 200).get("key").asText(), "Gleicher Inhalt, gleicher Schlüssel");

        User student = student("lena", course("8a"));
        mvc.perform(get("/api/media/" + key).with(as(student)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(image));
        mvc.perform(get("/api/media/" + key)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/media/../../etc/passwd").with(as(student))).andExpect(status().is4xxClientError());

        // Nur die Lehrkraft lädt hoch
        mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "x.png", "image/png", image))
                .with(as(student)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void rejectsEverythingButRealImages() throws Exception {
        upload("<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8), "x.svg", 400);
        upload("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8), "x.png", 400);
        // PNG-Kopf, aber kein Bild
        upload(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0}, "kaputt.png", 400);
        upload(new byte[3 * 1024 * 1024], "gross.png", 400);
        upload(png(5000, 1, 0), "breit.png", 400);
    }

    @Test
    void referencedImagesCannotBeDeleted() throws Exception {
        String unused = upload(png(3, 3, 0xABCDEF), "frei.png", 200).get("key").asText();
        mvc.perform(delete("/api/admin/media/" + unused).with(teacher()).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/media/" + unused).with(teacher())).andExpect(status().isNotFound());

        // Das Bild der mitgelieferten Clean-Desk-Aufgabe ist in Verwendung
        String used = jdbc.queryForObject("select config->>'image' from task_versions v join tasks t on t.current_version_id = v.id "
                + "where t.slug = 's15'", String.class);
        mvc.perform(delete("/api/admin/media/" + used).with(teacher()).with(csrf())).andExpect(status().isConflict());
    }

    @Test
    void exportCarriesImagesAndImportChecksThem() throws Exception {
        byte[] zip = mvc.perform(get("/api/admin/content/export").with(teacher())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        boolean found = false;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().startsWith("content/it-sicherheit/media/") && MediaKeys.isKey(entry.getName().substring(28))) found = true;
            }
        }
        assertTrue(found, "Export enthält das Bild beim Modul");

        // Ein Paket, dessen Bild nicht zum Namen passt, wird abgelehnt – die Aufgabe dazu auch
        String fake = "0".repeat(64) + ".png";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var zipOut = new java.util.zip.ZipOutputStream(out)) {
            zipOut.putNextEntry(new ZipEntry("bilder/module.json"));
            zipOut.write("{\"slug\":\"bilder\",\"title\":\"Bilder\"}".getBytes(StandardCharsets.UTF_8));
            zipOut.putNextEntry(new ZipEntry("bilder/media/" + fake));
            zipOut.write(png(2, 2, 0));
            zipOut.putNextEntry(new ZipEntry("bilder/tasks/01-bild.json"));
            zipOut.write(("{\"slug\":\"bild\",\"type\":\"IMAGE_HOTSPOT\",\"title\":\"Bild\",\"difficulty\":\"EASY\",\"points\":5,"
                    + "\"config\":{\"image\":\"" + fake + "\",\"alt\":\"Test\",\"targets\":[{\"id\":\"a\",\"x\":1,\"y\":1,\"w\":10,\"h\":10}]}}")
                    .getBytes(StandardCharsets.UTF_8));
        }
        JsonNode report = body(mvc.perform(multipart("/api/admin/content/import")
                .file(new MockMultipartFile("file", "bilder.zip", "application/zip", out.toByteArray())).with(teacher()).with(csrf()))
                .andExpect(status().isOk()));
        assertEquals(0, report.get("tasksCreated").asInt(), report.toString());
        assertEquals(2, report.get("errors").size(), report.toString());
    }
}

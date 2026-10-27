package de.codepath.backend;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schreibt die OpenAPI-Beschreibung nach frontend/src/lib/api/openapi.json. Daraus erzeugt
 * "pnpm gen:api" die TypeScript-Typen; ändert sich die API, fällt ein Bruch im Frontend-Build auf.
 */
class OpenApiExportTest extends IntegrationTestBase {

    @Test
    void exportOpenApi() throws Exception {
        String raw = getAs(teacher(), "/api/admin/openapi").andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode spec = json.readTree(raw);
        assertTrue(spec.get("paths").has("/api/modules/{moduleSlug}/{taskSlug}/submit"));
        Path target = Path.of("..", "frontend", "src", "lib", "api", "openapi.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, json.writerWithDefaultPrettyPrinter().writeValueAsString(spec) + "\n");
    }
}

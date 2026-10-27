package de.codepath.backend.platform.web;

import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;

/**
 * OpenAPI-Beschreibung als Quelle der Frontend-Typen. Antwort-Records enthalten immer alle Felder
 * (ggf. null) – deshalb werden ihre Eigenschaften als "required" markiert, Anfragen bleiben optional.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenApiCustomizer requiredResponseFields() {
        return openApi -> {
            openApi.info(new Info().title("CodePath API").version("2"));
            openApi.servers(null);
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                if (name.endsWith("Request") || schema.getProperties() == null) return;
                schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
            });
        };
    }
}

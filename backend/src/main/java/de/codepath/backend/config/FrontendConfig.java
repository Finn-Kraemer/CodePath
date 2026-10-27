package de.codepath.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Liefert das statisch gebaute SvelteKit-Frontend aus (Frontend im JAR, ein Container).
 * Unbekannte Pfade außerhalb von /api fallen auf index.html zurück, damit das Client-Routing greift.
 */
@Configuration
public class FrontendConfig implements WebMvcConfigurer {

    private static final String STATIC = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Dateinamen unter _app/immutable enthalten einen Hash und ändern sich nie
        registry.addResourceHandler("/_app/immutable/**")
                .addResourceLocations(STATIC + "_app/immutable/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    static class SpaFallbackResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/") || hasFileExtension(resourcePath)) {
                return null;
            }
            Resource index = new ClassPathResource("static/index.html");
            return index.exists() ? index : null;
        }

        private static boolean hasFileExtension(String path) {
            int slash = path.lastIndexOf('/');
            return path.indexOf('.', slash + 1) >= 0;
        }
    }
}

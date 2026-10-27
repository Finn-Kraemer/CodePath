package de.codepath.backend.content;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Spielt beim Start die mitgelieferten Content-Pakete ein – aber nur, was fehlt. Die Datenbank ist die
 * Quelle der Wahrheit (ADR-5): Im Editor geänderte Aufgaben werden nicht überschrieben.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class ContentSeeder implements ApplicationRunner {

    private final ContentPackageReader reader;
    private final ContentImporter importer;

    @Value("${codepath.content.seed-on-start:true}")
    private boolean seedOnStart;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!seedOnStart) return;
        var bundles = reader.fromClasspath("content");
        if (bundles.isEmpty()) {
            log.warn("Keine Content-Pakete im Classpath gefunden");
            return;
        }
        var report = importer.importBundles(bundles, ContentImporter.Mode.FILL_MISSING, null);
        log.info("Startinhalt: {} Module, {} Aufgaben neu, {} Versionen veröffentlicht, {} unverändert",
                report.modulesCreated(), report.tasksCreated(), report.versionsPublished(), report.unchanged());
        report.errors().forEach(e -> log.error("Content-Paket: {}", e));
    }
}

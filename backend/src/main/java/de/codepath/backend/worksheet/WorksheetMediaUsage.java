package de.codepath.backend.worksheet;

import de.codepath.backend.engine.MediaKeys;
import de.codepath.backend.platform.media.MediaReference;
import de.codepath.backend.platform.media.MediaUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bilder in Aufgabenblättern (Logo, Bild-Blöcke) für die Medienverwaltung: schützt sie vor dem
 * Löschen; ohne Anmeldung werden sie nie ausgeliefert. Eigene Bean, weil der WorksheetService
 * selbst den MediaService braucht.
 */
@Component
@RequiredArgsConstructor
public class WorksheetMediaUsage implements MediaUsage {

    private final WorksheetRepository repository;

    @Override
    public boolean isPublic(String key) {
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> usedBy(String key) {
        return repository.findAll().stream()
                .filter(w -> MediaKeys.find(w.getContent()).contains(key))
                .findFirst()
                .map(w -> "im Aufgabenblatt „" + w.getTitle() + "“");
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaReference> references() {
        List<MediaReference> references = new ArrayList<>();
        for (Worksheet w : repository.findAllByOrderByUpdatedAtDescIdDesc()) {
            for (String key : MediaKeys.find(w.getContent())) {
                references.add(new MediaReference(key, "WORKSHEET", "Aufgabenblatt „" + w.getTitle() + "“", "/admin/worksheets/" + w.getId()));
            }
        }
        return references;
    }
}

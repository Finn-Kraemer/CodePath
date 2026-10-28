package de.codepath.backend.platform.media;

import java.util.Optional;

/**
 * Stellen außerhalb der Aufgaben, die hochgeladene Bilder verwenden (Startseite, eigene Seiten,
 * Wissensdatenbank). Die Medienverwaltung fragt alle Beans dieses Typs, bevor sie ein Bild ohne
 * Anmeldung ausliefert oder löscht.
 */
public interface MediaUsage {

    /** Darf das Bild ohne Anmeldung ausgeliefert werden (öffentlich veröffentlichter Inhalt)? */
    boolean isPublic(String key);

    /** Wo das Bild verwendet wird, z. B. "auf der Startseite" – leer, wenn nirgends. */
    Optional<String> usedBy(String key);
}

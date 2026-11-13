package de.codepath.backend.platform.media;

/**
 * Eine Stelle, an der ein hochgeladenes Bild verwendet wird – für die Medienverwaltung.
 *
 * @param key   Schlüssel des Bildes
 * @param kind  Art der Stelle: TASK, HOMEPAGE, PAGE, KNOWLEDGE oder WORKSHEET
 * @param label lesbare Beschreibung, z. B. "Seite „Anfahrt“"
 * @param href  Adresse des Editors im Frontend, z. B. "/admin/pages/3"
 */
public record MediaReference(String key, String kind, String label, String href) {
}

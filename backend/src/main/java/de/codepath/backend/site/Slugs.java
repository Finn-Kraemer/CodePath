package de.codepath.backend.site;

import de.codepath.backend.platform.ApiException;

/** Adressen für Seiten und Artikel: Kleinbuchstaben, Ziffern und Bindestriche. */
final class Slugs {

    private Slugs() {
    }

    static String require(String slug) {
        if (slug == null || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 80) {
            throw ApiException.badRequest("Die Adresse darf nur Kleinbuchstaben, Ziffern und Bindestriche enthalten (höchstens 80 Zeichen).");
        }
        return slug;
    }

    static String title(String title) {
        if (title == null || title.isBlank()) throw ApiException.badRequest("Bitte einen Titel angeben.");
        String trimmed = title.strip();
        if (trimmed.length() > 200) throw ApiException.badRequest("Der Titel ist zu lang (höchstens 200 Zeichen).");
        return trimmed;
    }
}

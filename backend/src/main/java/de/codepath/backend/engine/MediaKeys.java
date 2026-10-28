package de.codepath.backend.engine;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schlüssel hochgeladener Bilder: SHA-256 des Inhalts (hex) plus Endung, z. B. {@code 3fa2…c1.png}.
 * Aufgaben verweisen in ihrer Konfiguration oder im Markdown ({@code /api/media/<schlüssel>}) darauf.
 */
public final class MediaKeys {

    public static final String REGEX = "[0-9a-f]{64}\\.(?:png|jpg|gif|webp)";
    private static final Pattern PATTERN = Pattern.compile("(?<![0-9a-f])" + REGEX);

    private MediaKeys() {
    }

    public static boolean isKey(String value) {
        return value != null && value.matches(REGEX);
    }

    /** Alle Schlüssel, die in einem Text (JSON oder Markdown) vorkommen. */
    public static Set<String> find(String text) {
        Set<String> keys = new LinkedHashSet<>();
        if (text == null) return keys;
        Matcher matcher = PATTERN.matcher(text);
        while (matcher.find()) keys.add(matcher.group());
        return keys;
    }
}

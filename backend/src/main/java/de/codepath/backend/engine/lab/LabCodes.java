package de.codepath.backend.engine.lab;

import de.codepath.backend.engine.TaskContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Codes für das Browser-Labor: je Person, Aufgabe und Teil verschieden (HMAC über den Seed des
 * {@link TaskContext}). Abschreiben vom Nachbarn hilft nicht, und ohne das Geheimnis lässt sich kein
 * Code vorhersagen. Alphabet ohne verwechselbare Zeichen (kein 0/O, 1/I).
 */
public final class LabCodes {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 4;

    private final byte[] secret;
    private final long seed;
    private final String scenario;

    public LabCodes(byte[] secret, TaskContext context, String scenario) {
        this.secret = secret;
        this.seed = context.seed();
        this.scenario = scenario;
    }

    /** Teil {@code index} der Lösung (ab 0). */
    public String part(int index) {
        return derive("teil:" + index, LENGTH);
    }

    /** Weiterer Wert, der nicht Teil der Lösung ist (z. B. ein Schlüssel, den man unterwegs findet). */
    public String extra(String name, int length) {
        return derive("extra:" + name, length);
    }

    public List<String> parts(int count) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) parts.add(part(i));
        return parts;
    }

    private String derive(String purpose, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] hash = mac.doFinal((seed + ":" + scenario + ":" + purpose).getBytes(StandardCharsets.UTF_8));
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < length; i++) code.append(ALPHABET.charAt((hash[i] & 0xFF) % ALPHABET.length()));
            return code.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Eingabe vereinheitlichen: Groß, ohne Leerzeichen; Bindestriche trennen die Teile. */
    public static List<String> split(String input, int parts) {
        if (input == null) return List.of();
        String clean = input.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9-]", "");
        List<String> result = new ArrayList<>();
        if (clean.contains("-")) {
            for (String part : clean.split("-+")) if (!part.isEmpty()) result.add(part);
        } else if (clean.length() == parts * LENGTH) {
            for (int i = 0; i < parts; i++) result.add(clean.substring(i * LENGTH, (i + 1) * LENGTH));
        } else if (!clean.isEmpty()) {
            result.add(clean);
        }
        return result;
    }
}

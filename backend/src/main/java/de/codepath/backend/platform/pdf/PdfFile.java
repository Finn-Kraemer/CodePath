package de.codepath.backend.platform.pdf;

/**
 * Ein erzeugtes PDF.
 *
 * @param filename Dateiname im ZIP bzw. beim Herunterladen
 * @param title    Lesezeichen in der Sammel-PDF, z. B. der Name der Person
 */
public record PdfFile(String filename, String title, byte[] content) {
}

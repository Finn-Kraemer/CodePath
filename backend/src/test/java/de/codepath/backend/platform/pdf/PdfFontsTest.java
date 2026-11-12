package de.codepath.backend.platform.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Eingebettete Unicode-Schriften: Sonderzeichen, fremde Schriften und Emojis bleiben erhalten. */
class PdfFontsTest {

    static final String SAMPLE = "→ ≤ ∑ λ Łódź 東京";

    static String text(byte[] pdf) throws Exception {
        try (PdfReader reader = new PdfReader(pdf)) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) sb.append(extractor.getTextFromPage(page)).append('\n');
            return sb.toString();
        }
    }

    static byte[] pdf(String... lines) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);
        document.open();
        for (String line : lines) {
            document.add(PdfFonts.paragraph(line, PdfFonts.font(PdfFonts.NORMAL, 11, Color.BLACK)));
            document.add(PdfFonts.paragraph(line, PdfFonts.font(PdfFonts.BOLD, 11, Color.BLACK)));
            document.add(PdfFonts.paragraph(line, PdfFonts.mono(9, Color.BLACK)));
        }
        document.close();
        return out.toByteArray();
    }

    @Test
    void unicodeSurvivesTextExtraction() throws Exception {
        String text = text(pdf(SAMPLE, "Box ┌─┐ │x│ └─┘ ≠ ⇒ ✓", "Modul 🚀 Raketen"));
        for (String part : SAMPLE.split(" ")) assertTrue(text.contains(part), "fehlt: " + part + " in " + text);
        assertTrue(text.contains("┌─┐"));
        assertTrue(text.contains("≠ ⇒ ✓"));
        assertTrue(text.contains("🚀"), "Emoji aus der Ausweichschrift");
        assertFalse(text.contains("?"), "kein Ersatzzeichen");
    }

    @Test
    void onlyUsedGlyphsAreEmbedded() {
        // Teilmengen statt ganzer Schriften: auch mit chinesischen Zeichen bleibt die Datei klein
        assertTrue(pdf(SAMPLE).length < 120_000, "PDF zu groß");
    }

    @Test
    void cleanRemovesOnlyControlCharacters() {
        assertEquals("a    b\nc→😀", PdfFonts.clean("a\tb\r\nc\u0007→😀"));
        assertEquals("", PdfFonts.clean(null));
    }
}

package de.codepath.backend.platform.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.FontSelector;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Die eine Schriftkonfiguration für alle PDFs (Teilnahmebestätigung, Mitnehm-Paket, Bewertungsbögen,
 * Aufgabenblätter, Anwesenheit). Statt der PDF-Standardschriften (nur Windows-1252) werden freie
 * Unicode-Schriften eingebettet – nur die tatsächlich benutzten Zeichen:
 * <ul>
 *   <li>DejaVu Sans, Sans Mono und Serif für Latein, Griechisch, Kyrillisch, Pfeile, mathematische
 *   Zeichen und Box-Drawing</li>
 *   <li>Noto Sans JP als Ausweichschrift für chinesische und japanische Schriftzeichen</li>
 *   <li>Noto Emoji (schwarzweiß) als Ausweichschrift für Emojis</li>
 * </ul>
 * Texte mit Zeichen, die die Hauptschrift nicht kennt, setzt {@link #phrase} bzw. {@link #paragraph}
 * aus mehreren Schriften zusammen. Lizenzen: {@code resources/fonts/LICENSE-*.txt}.
 */
public final class PdfFonts {

    public enum Family { SANS, SERIF, MONO }

    public static final int NORMAL = Font.NORMAL;
    public static final int BOLD = Font.BOLD;
    public static final int ITALIC = Font.ITALIC;

    private enum Face {
        SANS("DejaVuSans.ttf"), SANS_BOLD("DejaVuSans-Bold.ttf"), SANS_ITALIC("DejaVuSans-Oblique.ttf"),
        SERIF("DejaVuSerif.ttf"), SERIF_BOLD("DejaVuSerif-Bold.ttf"), SERIF_ITALIC("DejaVuSerif-Italic.ttf"),
        MONO("DejaVuSansMono.ttf"), MONO_BOLD("DejaVuSansMono-Bold.ttf"),
        CJK("NotoSansJP-Regular.ttf"), EMOJI("NotoEmoji-Regular.ttf");

        final String file;

        Face(String file) {
            this.file = file;
        }
    }

    private static final Map<Face, BaseFont> CACHE = new EnumMap<>(Face.class);

    private PdfFonts() {
    }

    private static synchronized BaseFont load(Face face) {
        return CACHE.computeIfAbsent(face, f -> {
            try (InputStream in = PdfFonts.class.getResourceAsStream("/fonts/" + f.file)) {
                if (in == null) throw new IllegalStateException("Schrift fehlt: " + f.file);
                return BaseFont.createFont(f.file, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (DocumentException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** Grundschrift einer Familie und eines Schnitts (für Vorlagen mit {@code setFontAndSize}). */
    public static BaseFont baseFont(Family family, int style) {
        return load(face(family, style));
    }

    private static Face face(Family family, int style) {
        boolean bold = (style & BOLD) != 0;
        boolean italic = (style & ITALIC) != 0;
        return switch (family) {
            case MONO -> bold ? Face.MONO_BOLD : Face.MONO;
            case SERIF -> bold ? Face.SERIF_BOLD : italic ? Face.SERIF_ITALIC : Face.SERIF;
            case SANS -> bold ? Face.SANS_BOLD : italic ? Face.SANS_ITALIC : Face.SANS;
        };
    }

    /** Serifenlose Schrift: {@link #NORMAL}, {@link #BOLD} oder {@link #ITALIC}. */
    public static Font font(int style, float size, Color color) {
        return font(Family.SANS, style, size, color);
    }

    public static Font font(Family family, int style, float size, Color color) {
        return new Font(baseFont(family, style), size, Font.NORMAL, color);
    }

    public static Font mono(float size, Color color) {
        return font(Family.MONO, NORMAL, size, color);
    }

    /**
     * Text als Phrase; Zeichen, die die Schrift nicht kennt, kommen aus den Ausweichschriften (gleiche
     * Größe und Farbe, fett nachgezeichnet, wenn die Hauptschrift fett ist). Zeichen, die keine Schrift
     * kennt, entfallen.
     */
    public static Phrase phrase(String text, Font font) {
        String value = clean(text);
        Phrase phrase = new Phrase(font.getCalculatedLeading(1.5f));
        phrase.setFont(font);
        if (value.isEmpty()) return phrase;
        BaseFont base = font.getBaseFont();
        if (base == null || covered(value, base)) {
            phrase.add(new Chunk(value, font));
            return phrase;
        }
        FontSelector selector = new FontSelector();
        selector.addFont(font);
        int fallbackStyle = isBold(base) ? Font.BOLD : Font.NORMAL;
        if (base == load(Face.MONO) || base == load(Face.MONO_BOLD)) {
            selector.addFont(new Font(load(isBold(base) ? Face.SANS_BOLD : Face.SANS), font.getSize(), Font.NORMAL, font.getColor()));
        }
        selector.addFont(new Font(load(Face.CJK), font.getSize(), fallbackStyle, font.getColor()));
        selector.addFont(new Font(load(Face.EMOJI), font.getSize(), fallbackStyle, font.getColor()));
        for (Element element : selector.process(value)) phrase.add(element);
        return phrase;
    }

    /** Wie {@link #phrase}, als Absatz. */
    public static Paragraph paragraph(String text, Font font) {
        Paragraph paragraph = new Paragraph(phrase(text, font));
        paragraph.setFont(font);
        return paragraph;
    }

    private static boolean isBold(BaseFont base) {
        return base == load(Face.SANS_BOLD) || base == load(Face.SERIF_BOLD) || base == load(Face.MONO_BOLD);
    }

    private static boolean covered(String text, BaseFont base) {
        return text.codePoints().allMatch(cp -> cp == '\n' || cp == ' ' || base.charExists(cp));
    }

    /**
     * Bereinigt Text für die PDF-Ausgabe: Steuerzeichen (außer Zeilenumbruch) entfallen, Tabulatoren werden
     * zu vier Leerzeichen. Alle anderen Zeichen bleiben erhalten.
     */
    public static String clean(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            if (cp == '\n') sb.append('\n');
            else if (cp == '\t') sb.append("    ");
            else if (cp == '\r' || Character.isISOControl(cp)) return;
            else if (Character.getType(cp) == Character.FORMAT && cp != 0x200D) return;
            else sb.appendCodePoint(cp);
        });
        return sb.toString();
    }
}

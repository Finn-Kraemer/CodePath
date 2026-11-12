package de.codepath.backend.platform.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfReader;
import de.codepath.backend.platform.ApiException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Mehrere PDFs auf einmal ausliefern – für alle Exporte „je Person eines Kurses“ gleich:
 * <ul>
 *   <li>{@code zip}: jede Datei einzeln im ZIP</li>
 *   <li>{@code pdf}: eine Sammel-PDF zum Ausdrucken, jede Datei beginnt auf einer neuen Seite und hat ein
 *   Lesezeichen; mit {@code duplex} wird nach Dateien mit ungerader Seitenzahl eine leere Seite eingefügt,
 *   damit beim beidseitigen Druck jede Datei auf einem neuen Blatt beginnt</li>
 * </ul>
 * Mit {@code inline} öffnet der Browser die PDF direkt (zum Drucken), statt sie herunterzuladen.
 */
public final class PdfBundles {

    public static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private PdfBundles() {
    }

    public enum Format {
        ZIP, PDF;

        public static Format parse(String value) {
            if (value == null || value.isBlank()) return ZIP;
            try {
                return valueOf(value.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("format: zip oder pdf");
            }
        }
    }

    /** Antwort für einen Sammel-Export; baseName ohne Endung, z. B. "Teilnahmebestaetigungen_9a". */
    public static ResponseEntity<byte[]> respond(String baseName, List<PdfFile> files, Format format, boolean duplex, boolean inline) {
        if (format == Format.PDF) {
            return response(baseName + ".pdf", merge(files, duplex), MediaType.APPLICATION_PDF, inline);
        }
        return response(baseName + ".zip", zip(files), ZIP, false);
    }

    /** Antwort für ein einzelnes PDF. */
    public static ResponseEntity<byte[]> respond(PdfFile file, boolean inline) {
        return response(file.filename(), file.content(), MediaType.APPLICATION_PDF, inline);
    }

    public static byte[] zip(List<PdfFile> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Set<String> names = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (PdfFile file : files) {
                String name = file.filename();
                String stem = name.endsWith(".pdf") ? name.substring(0, name.length() - 4) : name;
                for (int i = 2; !names.add(name); i++) name = stem + "_" + i + ".pdf";
                zip.putNextEntry(new ZipEntry(name));
                zip.write(file.content());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw ApiException.badRequest("ZIP konnte nicht erstellt werden.");
        }
        return out.toByteArray();
    }

    /** Hängt alle PDFs aneinander; ein Lesezeichen je Datei. */
    public static byte[] merge(List<PdfFile> files, boolean duplex) {
        if (files.isEmpty()) throw ApiException.badRequest("Es gibt nichts zu drucken.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfCopy copy = new PdfCopy(document, out);
            document.open();
            List<Map<String, Object>> bookmarks = new ArrayList<>();
            int page = 1;
            for (int f = 0; f < files.size(); f++) {
                PdfFile file = files.get(f);
                PdfReader reader = new PdfReader(file.content());
                int pages = reader.getNumberOfPages();
                Map<String, Object> mark = new HashMap<>();
                mark.put("Title", file.title() == null ? file.filename() : file.title());
                mark.put("Action", "GoTo");
                mark.put("Page", page + " Fit");
                bookmarks.add(mark);
                for (int p = 1; p <= pages; p++) copy.addPage(copy.getImportedPage(reader, p));
                page += pages;
                if (duplex && pages % 2 == 1 && f < files.size() - 1) {
                    PdfReader blank = new PdfReader(blankPage(reader.getPageSize(pages)));
                    copy.addPage(copy.getImportedPage(blank, 1));
                    copy.freeReader(blank);
                    blank.close();
                    page++;
                }
                copy.freeReader(reader);
                reader.close();
            }
            copy.setOutlines(bookmarks);
            document.close();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("PDFs konnten nicht zusammengefügt werden.", e);
        }
        return out.toByteArray();
    }

    /** Leere Seite mit Inhalt (ein Leerzeichen), damit auch Text-Extraktoren und ältere Drucker sie verarbeiten. */
    private static byte[] blankPage(Rectangle size) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(new Rectangle(size.getWidth(), size.getHeight()));
        PdfWriter.getInstance(document, out);
        document.open();
        document.add(new Paragraph(" "));
        document.close();
        return out.toByteArray();
    }

    private static ResponseEntity<byte[]> response(String filename, byte[] body, MediaType type, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(type)
                .body(body);
    }
}

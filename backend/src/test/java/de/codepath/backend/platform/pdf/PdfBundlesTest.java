package de.codepath.backend.platform.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.SimpleBookmark;
import de.codepath.backend.platform.ApiException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** ZIP und Sammel-PDF für alle Exporte „je Person“. */
class PdfBundlesTest {

    static PdfFile pdf(String name, int pages) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();
        for (int i = 0; i < pages; i++) {
            if (i > 0) document.newPage();
            document.add(new Paragraph(name + " Seite " + (i + 1)));
        }
        document.close();
        return new PdfFile(name + ".pdf", name, out.toByteArray());
    }

    static int pages(byte[] pdf) throws Exception {
        try (PdfReader reader = new PdfReader(pdf)) {
            return reader.getNumberOfPages();
        }
    }

    @Test
    void mergesAllFilesWithBookmarks() throws Exception {
        byte[] merged = PdfBundles.merge(List.of(pdf("Lena", 1), pdf("Tom", 2), pdf("Mia", 1)), false);
        assertEquals(4, pages(merged));
        try (PdfReader reader = new PdfReader(merged)) {
            List<Map<String, Object>> marks = SimpleBookmark.getBookmarkList(reader);
            assertEquals(List.of("Lena", "Tom", "Mia"), marks.stream().map(m -> m.get("Title")).toList());
            assertEquals("4 Fit", marks.get(2).get("Page"));
        }
    }

    @Test
    void duplexStartsEveryFileOnANewSheet() throws Exception {
        // 1 + Leerseite, 2, 3 (letzte Datei ohne Leerseite)
        byte[] merged = PdfBundles.merge(List.of(pdf("Lena", 1), pdf("Tom", 2), pdf("Mia", 3)), true);
        assertEquals(7, pages(merged));
        try (PdfReader reader = new PdfReader(merged)) {
            List<Map<String, Object>> marks = SimpleBookmark.getBookmarkList(reader);
            assertEquals(List.of("1 Fit", "3 Fit", "5 Fit"), marks.stream().map(m -> m.get("Page")).toList());
        }
    }

    @Test
    void zipKeepsEveryFileEvenWithEqualNames() throws Exception {
        byte[] zip = PdfBundles.zip(List.of(pdf("Lena", 1), pdf("Lena", 1)));
        Set<String> names = new TreeSet<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) names.add(e.getName());
        }
        assertEquals(Set.of("Lena.pdf", "Lena_2.pdf"), names);
    }

    @Test
    void nothingToPrint() {
        assertThrows(ApiException.class, () -> PdfBundles.merge(List.of(), false));
        assertThrows(ApiException.class, () -> PdfBundles.Format.parse("docx"));
        assertEquals(PdfBundles.Format.ZIP, PdfBundles.Format.parse(null));
        assertEquals(PdfBundles.Format.PDF, PdfBundles.Format.parse("pdf"));
    }
}

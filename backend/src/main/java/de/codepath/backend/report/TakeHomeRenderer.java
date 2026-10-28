package de.codepath.backend.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Zeichnet das Mitnehm-Paket als PDF (OpenPDF), im selben Stil wie die Teilnahmebestätigung. */
public class TakeHomeRenderer {

    public record TaskLine(String title, String status, long points, int max) {
    }

    public record ModuleBlock(String title, int achieved, int max, List<TaskLine> tasks) {
    }

    public record CodeBlock(String module, String task, String language, String code) {
    }

    /** module: aus welchem Modul der Link stammt, oder null für allgemeine Links */
    public record Link(String title, String url, String module) {
        public Link(String title, String url) {
            this(title, url, null);
        }
    }

    public record TakeHome(String name, String courseName, LocalDate date, ReportService.Compass compass,
                           List<ModuleBlock> modules, List<CodeBlock> code, List<Link> links) {
    }

    private static final Color NAVY = new Color(15, 23, 42);
    private static final Color GOLD = new Color(180, 83, 9);
    private static final Color BG = new Color(241, 245, 249);
    private static final Color BORDER = new Color(203, 213, 225);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color LINK = new Color(29, 78, 216);

    byte[] render(TakeHome data) {
        Document document = new Document(PageSize.A4, 40, 40, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document document) {
                    PdfContentByte cb = writer.getDirectContent();
                    cb.setColorFill(GOLD);
                    cb.rectangle(0, 832, 595, 10);
                    cb.fill();
                    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                            new Phrase("CodePath · Mitnehm-Paket · Seite " + writer.getPageNumber(), font(FontFactory.HELVETICA, 7, MUTED)),
                            297, 25, 0);
                }
            });
            document.addTitle("Mitnehm-Paket");
            document.open();

            header(document, data);
            compass(document, data.compass());
            results(document, data.modules());
            code(document, data.code());
            links(document, data.links());

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private void header(Document document, TakeHome data) {
        Paragraph title = new Paragraph("MEIN MITNEHM-PAKET", font(FontFactory.HELVETICA_BOLD, 22, NAVY));
        title.setSpacingAfter(4);
        document.add(title);
        String sub = data.courseName() != null ? data.courseName().toUpperCase(Locale.GERMAN) : "BERUFSORIENTIERUNG IT";
        document.add(new Paragraph(clean(sub), font(FontFactory.HELVETICA_BOLD, 11, GOLD)));
        Paragraph who = new Paragraph(clean(data.name()) + " · " + data.date().format(DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale.GERMAN)),
                font(FontFactory.HELVETICA, 11, NAVY));
        who.setSpacingBefore(12);
        document.add(who);
        Paragraph note = new Paragraph("Dein Code, deine Ergebnisse, dein Berufe-Kompass und Ideen zum Weiterlernen. "
                + "Dieses PDF wurde nur für dich erzeugt – auf dem Server wird davon nichts gespeichert. "
                + "Wenn der Kurs endet, wird dein Konto gelöscht; dieses Dokument bleibt dir.", font(FontFactory.HELVETICA, 9, MUTED));
        note.setSpacingBefore(6);
        note.setSpacingAfter(18);
        document.add(note);
    }

    private void compass(Document document, ReportService.Compass compass) {
        section(document, "DEIN BERUFE-KOMPASS");
        if (compass.entries().isEmpty()) {
            document.add(new Paragraph("Noch keine Ergebnisse – löse ein paar Aufgaben, dann zeigt der Kompass eine Richtung.",
                    font(FontFactory.HELVETICA, 10, MUTED)));
            return;
        }
        if (!compass.top().isEmpty()) {
            PdfPTable top = new PdfPTable(1);
            top.setWidthPercentage(100);
            top.setSpacingAfter(12);
            PdfPCell cell = new PdfPCell();
            cell.setPadding(14);
            cell.setBackgroundColor(BG);
            cell.setBorderColor(NAVY);
            cell.setBorderWidth(1.2f);
            cell.addElement(new Paragraph("Dein Kompass zeigt Richtung:", font(FontFactory.HELVETICA, 9, MUTED)));
            for (int i = 0; i < compass.top().size(); i++) {
                ReportService.CompassEntry e = compass.top().get(i);
                Paragraph p = new Paragraph((i + 1) + ". " + clean(e.career()), font(FontFactory.HELVETICA_BOLD, i == 0 ? 14 : 11, i == 0 ? GOLD : NAVY));
                p.setSpacingBefore(4);
                cell.addElement(p);
                cell.addElement(new Paragraph("weil du im Modul „" + clean(e.moduleTitle()) + "“ " + e.percent() + " % der Punkte geholt hast",
                        font(FontFactory.HELVETICA, 9, MUTED)));
            }
            top.addCell(cell);
            document.add(top);
        }
        PdfPTable table = table(new float[]{3, 4, 3}, "Modul", "Berufsfeld", "Anteil der Punkte");
        for (ReportService.CompassEntry e : compass.entries()) {
            table.addCell(cell(clean(e.moduleTitle()), font(FontFactory.HELVETICA, 9, NAVY)));
            table.addCell(cell(clean(e.career()), font(FontFactory.HELVETICA, 9, NAVY)));
            table.addCell(bar(e.percent()));
        }
        document.add(table);
    }

    private void results(Document document, List<ModuleBlock> modules) {
        section(document, "DEINE ERGEBNISSE");
        if (modules.isEmpty()) {
            document.add(new Paragraph("Für dich waren noch keine Module freigeschaltet.", font(FontFactory.HELVETICA, 10, MUTED)));
            return;
        }
        for (ModuleBlock module : modules) {
            Paragraph title = new Paragraph(clean(module.title()) + "  –  " + module.achieved() + " von " + module.max() + " Punkten",
                    font(FontFactory.HELVETICA_BOLD, 10, NAVY));
            title.setSpacingBefore(8);
            title.setSpacingAfter(4);
            document.add(title);
            PdfPTable table = table(new float[]{6, 3, 2}, "Aufgabe", "Stand", "Punkte");
            for (TaskLine task : module.tasks()) {
                table.addCell(cell(clean(task.title()), font(FontFactory.HELVETICA, 9, NAVY)));
                table.addCell(cell(task.status(), font(FontFactory.HELVETICA, 9, task.status().equals("Gelöst") ? new Color(21, 128, 61) : MUTED)));
                table.addCell(cell(task.points() + " / " + task.max(), font(FontFactory.HELVETICA, 9, NAVY)));
            }
            document.add(table);
        }
    }

    private void code(Document document, List<CodeBlock> blocks) {
        if (blocks.isEmpty()) return;
        document.newPage();
        section(document, "DEIN CODE");
        Paragraph intro = new Paragraph("Deine letzte Abgabe je Aufgabe – zum Nachlesen und Weiterbauen.", font(FontFactory.HELVETICA, 9, MUTED));
        intro.setSpacingAfter(6);
        document.add(intro);
        for (CodeBlock block : blocks) {
            Paragraph title = new Paragraph(clean(block.module()) + " › " + clean(block.task()) + "  (" + block.language() + ")",
                    font(FontFactory.HELVETICA_BOLD, 9, NAVY));
            title.setSpacingBefore(10);
            title.setSpacingAfter(3);
            title.setKeepTogether(true);
            document.add(title);
            PdfPTable box = new PdfPTable(1);
            box.setWidthPercentage(100);
            PdfPCell cell = new PdfPCell(new Phrase(clean(block.code().replace("\t", "    ")), font(FontFactory.COURIER, 8, NAVY)));
            cell.setPadding(8);
            cell.setBackgroundColor(new Color(248, 250, 252));
            cell.setBorderColor(BORDER);
            box.addCell(cell);
            document.add(box);
        }
    }

    private void links(Document document, List<Link> links) {
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingBefore(10);
        document.add(spacer);
        section(document, "WEITERLERNEN");
        for (Link link : links) {
            Paragraph p = new Paragraph();
            p.setSpacingBefore(5);
            Anchor anchor = new Anchor(clean(link.title()), font(FontFactory.HELVETICA_BOLD, 10, LINK));
            anchor.setReference(link.url());
            p.add(anchor);
            p.add(new Chunk("\n" + clean(link.url()) + (link.module() != null ? "  ·  passend zu „" + clean(link.module()) + "“" : ""),
                    font(FontFactory.HELVETICA, 8, MUTED)));
            document.add(p);
        }
    }

    private static void section(Document document, String title) {
        Paragraph p = new Paragraph(title, font(FontFactory.HELVETICA_BOLD, 11, NAVY));
        p.setSpacingBefore(14);
        p.setSpacingAfter(8);
        document.add(p);
    }

    private static PdfPTable table(float[] widths, String... headers) {
        PdfPTable table = new PdfPTable(widths.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setHeaderRows(1);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h.toUpperCase(Locale.GERMAN), font(FontFactory.HELVETICA_BOLD, 8, Color.WHITE)));
            cell.setBackgroundColor(NAVY);
            cell.setPadding(6);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }
        return table;
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER);
        return cell;
    }

    /** Balken für den Kompass: gefüllter Anteil plus Prozentzahl. */
    private static PdfPCell bar(int percent) {
        PdfPTable inner = new PdfPTable(new float[]{Math.max(percent, 1), Math.max(100 - percent, 1), 40});
        inner.setWidthPercentage(100);
        PdfPCell filled = new PdfPCell(new Phrase(" ", font(FontFactory.HELVETICA, 6, NAVY)));
        filled.setBackgroundColor(percent > 0 ? GOLD : BG);
        filled.setBorder(Rectangle.NO_BORDER);
        filled.setFixedHeight(8);
        PdfPCell rest = new PdfPCell(new Phrase(" ", font(FontFactory.HELVETICA, 6, NAVY)));
        rest.setBackgroundColor(BG);
        rest.setBorder(Rectangle.NO_BORDER);
        rest.setFixedHeight(8);
        PdfPCell label = new PdfPCell(new Phrase(percent + " %", font(FontFactory.HELVETICA_BOLD, 8, NAVY)));
        label.setBorder(Rectangle.NO_BORDER);
        label.setPaddingLeft(4);
        label.setPaddingTop(0);
        inner.addCell(filled);
        inner.addCell(rest);
        inner.addCell(label);
        PdfPCell cell = new PdfPCell(inner);
        cell.setPadding(6);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private static Font font(String name, float size, Color color) {
        return FontFactory.getFont(name, size, color);
    }

    /** Die Standardschriften kennen nur Windows-1252; alles andere (z. B. Emoji) wird zu "?". */
    static String clean(String text) {
        if (text == null) return "";
        CharsetEncoder encoder = Charset.forName("windows-1252").newEncoder();
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            if (cp == '\n' || (cp >= 32 && encoder.canEncode(ch))) sb.append(ch);
            else if (cp == '\r') return;
            else if (Character.getType(cp) != Character.NON_SPACING_MARK && cp != 0xFE0F) sb.append('?');
        });
        return sb.toString();
    }
}

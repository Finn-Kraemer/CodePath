package de.codepath.backend.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import de.codepath.backend.platform.pdf.PdfFonts;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
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

    /** task null = Notiz zum ganzen Modul; text in Markdown */
    public record JournalNote(String module, String task, String text) {
    }

    public record TakeHome(String name, String courseName, LocalDate date, ReportService.Compass compass,
                           List<ModuleBlock> modules, List<CodeBlock> code, List<Link> links, List<JournalNote> journal) {
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
                            PdfFonts.phrase("CodePath · Mitnehm-Paket · Seite " + writer.getPageNumber(), PdfFonts.font(PdfFonts.NORMAL, 7, MUTED)),
                            297, 25, 0);
                }
            });
            document.addTitle("Mitnehm-Paket");
            document.open();

            header(document, data);
            compass(document, data.compass());
            results(document, data.modules());
            journal(document, data.journal());
            code(document, data.code());
            links(document, data.links());

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private void header(Document document, TakeHome data) {
        Paragraph title = PdfFonts.paragraph("MEIN MITNEHM-PAKET", PdfFonts.font(PdfFonts.BOLD, 22, NAVY));
        title.setSpacingAfter(4);
        document.add(title);
        String sub = data.courseName() != null ? data.courseName().toUpperCase(Locale.GERMAN) : "BERUFSORIENTIERUNG IT";
        document.add(PdfFonts.paragraph(PdfFonts.clean(sub), PdfFonts.font(PdfFonts.BOLD, 11, GOLD)));
        Paragraph who = PdfFonts.paragraph(PdfFonts.clean(data.name()) + " · " + data.date().format(DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale.GERMAN)),
                PdfFonts.font(PdfFonts.NORMAL, 11, NAVY));
        who.setSpacingBefore(12);
        document.add(who);
        Paragraph note = PdfFonts.paragraph("Dein Code, deine Ergebnisse, dein Berufe-Kompass und Ideen zum Weiterlernen. "
                + "Dieses PDF wurde nur für dich erzeugt – auf dem Server wird davon nichts gespeichert. "
                + "Wenn der Kurs endet, wird dein Konto gelöscht; dieses Dokument bleibt dir.", PdfFonts.font(PdfFonts.NORMAL, 9, MUTED));
        note.setSpacingBefore(6);
        note.setSpacingAfter(18);
        document.add(note);
    }

    private void compass(Document document, ReportService.Compass compass) {
        section(document, "DEIN BERUFE-KOMPASS");
        if (compass.entries().isEmpty()) {
            document.add(PdfFonts.paragraph("Noch keine Ergebnisse – löse ein paar Aufgaben, dann zeigt der Kompass eine Richtung.",
                    PdfFonts.font(PdfFonts.NORMAL, 10, MUTED)));
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
            cell.addElement(PdfFonts.paragraph("Dein Kompass zeigt Richtung:", PdfFonts.font(PdfFonts.NORMAL, 9, MUTED)));
            for (int i = 0; i < compass.top().size(); i++) {
                ReportService.CompassEntry e = compass.top().get(i);
                Paragraph p = PdfFonts.paragraph((i + 1) + ". " + PdfFonts.clean(e.career()), PdfFonts.font(PdfFonts.BOLD, i == 0 ? 14 : 11, i == 0 ? GOLD : NAVY));
                p.setSpacingBefore(4);
                cell.addElement(p);
                cell.addElement(PdfFonts.paragraph("weil du im Modul „" + PdfFonts.clean(e.moduleTitle()) + "“ " + e.percent() + " % der Punkte geholt hast",
                        PdfFonts.font(PdfFonts.NORMAL, 9, MUTED)));
            }
            top.addCell(cell);
            document.add(top);
        }
        PdfPTable table = table(new float[]{3, 4, 3}, "Modul", "Berufsfeld", "Anteil der Punkte");
        for (ReportService.CompassEntry e : compass.entries()) {
            table.addCell(cell(PdfFonts.clean(e.moduleTitle()), PdfFonts.font(PdfFonts.NORMAL, 9, NAVY)));
            table.addCell(cell(PdfFonts.clean(e.career()), PdfFonts.font(PdfFonts.NORMAL, 9, NAVY)));
            table.addCell(bar(e.percent()));
        }
        document.add(table);
    }

    private void results(Document document, List<ModuleBlock> modules) {
        section(document, "DEINE ERGEBNISSE");
        if (modules.isEmpty()) {
            document.add(PdfFonts.paragraph("Für dich waren noch keine Module freigeschaltet.", PdfFonts.font(PdfFonts.NORMAL, 10, MUTED)));
            return;
        }
        for (ModuleBlock module : modules) {
            Paragraph title = PdfFonts.paragraph(PdfFonts.clean(module.title()) + "  –  " + module.achieved() + " von " + module.max() + " Punkten",
                    PdfFonts.font(PdfFonts.BOLD, 10, NAVY));
            title.setSpacingBefore(8);
            title.setSpacingAfter(4);
            document.add(title);
            PdfPTable table = table(new float[]{6, 3, 2}, "Aufgabe", "Stand", "Punkte");
            for (TaskLine task : module.tasks()) {
                table.addCell(cell(PdfFonts.clean(task.title()), PdfFonts.font(PdfFonts.NORMAL, 9, NAVY)));
                table.addCell(cell(task.status(), PdfFonts.font(PdfFonts.NORMAL, 9, task.status().equals("Gelöst") ? new Color(21, 128, 61) : MUTED)));
                table.addCell(cell(task.points() + " / " + task.max(), PdfFonts.font(PdfFonts.NORMAL, 9, NAVY)));
            }
            document.add(table);
        }
    }

    /** Eigene Notizen; Markdown wird lesbar dargestellt (Überschriften, Listen, fett, Code). */
    private void journal(Document document, List<JournalNote> notes) {
        if (notes == null || notes.isEmpty()) return;
        section(document, "MEIN LERNTAGEBUCH");
        String module = null;
        for (JournalNote note : notes) {
            if (!note.module().equals(module)) {
                module = note.module();
                Paragraph title = PdfFonts.paragraph(module, PdfFonts.font(PdfFonts.BOLD, 10, NAVY));
                title.setSpacingBefore(8);
                title.setSpacingAfter(2);
                document.add(title);
            }
            if (note.task() != null) {
                Paragraph task = PdfFonts.paragraph("Zur Aufgabe „" + note.task() + "“", PdfFonts.font(PdfFonts.ITALIC, 9, MUTED));
                task.setSpacingBefore(4);
                document.add(task);
            }
            for (Element e : markdown(note.text())) document.add(e);
        }
    }

    /** Sehr einfaches Markdown: # Überschrift, - / 1. Listen, **fett**, *kursiv*, `Code`, Rest als Absatz. */
    static List<Element> markdown(String text) {
        List<Element> out = new java.util.ArrayList<>();
        boolean code = false;
        for (String raw : text.replace("\r", "").split("\n")) {
            String line = raw.stripTrailing();
            if (line.strip().startsWith("```")) {
                code = !code;
                continue;
            }
            if (code) {
                Paragraph p = PdfFonts.paragraph(line.isEmpty() ? " " : line, PdfFonts.mono(8, NAVY));
                p.setIndentationLeft(10);
                out.add(p);
                continue;
            }
            if (line.isBlank()) continue;
            Paragraph p;
            java.util.regex.Matcher heading = java.util.regex.Pattern.compile("^#{1,6}\\s+(.*)$").matcher(line);
            java.util.regex.Matcher bullet = java.util.regex.Pattern.compile("^\\s*[-*+]\\s+(.*)$").matcher(line);
            java.util.regex.Matcher number = java.util.regex.Pattern.compile("^\\s*(\\d+)[.)]\\s+(.*)$").matcher(line);
            if (heading.matches()) {
                p = PdfFonts.paragraph(inline(heading.group(1)), PdfFonts.font(PdfFonts.BOLD, 9.5f, NAVY));
                p.setSpacingBefore(4);
            } else if (bullet.matches()) {
                p = PdfFonts.paragraph("•  " + inline(bullet.group(1)), PdfFonts.font(PdfFonts.NORMAL, 9, NAVY));
                p.setIndentationLeft(10);
            } else if (number.matches()) {
                p = PdfFonts.paragraph(number.group(1) + ".  " + inline(number.group(2)), PdfFonts.font(PdfFonts.NORMAL, 9, NAVY));
                p.setIndentationLeft(10);
            } else {
                p = PdfFonts.paragraph(inline(line.startsWith("> ") ? line.substring(2) : line), PdfFonts.font(PdfFonts.NORMAL, 9, NAVY));
            }
            p.setSpacingAfter(2);
            out.add(p);
        }
        return out;
    }

    /** Auszeichnungen entfernen, Linktext behalten. */
    static String inline(String text) {
        return text.replaceAll("\\*\\*(.+?)\\*\\*", "$1").replaceAll("__(.+?)__", "$1")
                .replaceAll("(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*", "$1").replaceAll("`([^`]+)`", "$1")
                .replaceAll("\\[([^]]+)]\\(([^)]+)\\)", "$1 ($2)");
    }

    private void code(Document document, List<CodeBlock> blocks) {
        if (blocks.isEmpty()) return;
        document.newPage();
        section(document, "DEIN CODE");
        Paragraph intro = PdfFonts.paragraph("Deine letzte Abgabe je Aufgabe – zum Nachlesen und Weiterbauen.", PdfFonts.font(PdfFonts.NORMAL, 9, MUTED));
        intro.setSpacingAfter(6);
        document.add(intro);
        for (CodeBlock block : blocks) {
            Paragraph title = PdfFonts.paragraph(PdfFonts.clean(block.module()) + " › " + PdfFonts.clean(block.task()) + "  (" + block.language() + ")",
                    PdfFonts.font(PdfFonts.BOLD, 9, NAVY));
            title.setSpacingBefore(10);
            title.setSpacingAfter(3);
            title.setKeepTogether(true);
            document.add(title);
            PdfPTable box = new PdfPTable(1);
            box.setWidthPercentage(100);
            PdfPCell cell = new PdfPCell(PdfFonts.phrase(PdfFonts.clean(block.code().replace("\t", "    ")), PdfFonts.font(PdfFonts.Family.MONO, PdfFonts.NORMAL, 8, NAVY)));
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
            Anchor anchor = new Anchor(PdfFonts.phrase(link.title(), PdfFonts.font(PdfFonts.BOLD, 10, LINK)));
            anchor.setReference(link.url());
            p.add(anchor);
            p.add(PdfFonts.phrase("\n" + link.url() + (link.module() != null ? "  ·  passend zu „" + link.module() + "“" : ""),
                    PdfFonts.font(PdfFonts.NORMAL, 8, MUTED)));
            document.add(p);
        }
    }

    private static void section(Document document, String title) {
        Paragraph p = PdfFonts.paragraph(title, PdfFonts.font(PdfFonts.BOLD, 11, NAVY));
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
            PdfPCell cell = new PdfPCell(PdfFonts.phrase(h.toUpperCase(Locale.GERMAN), PdfFonts.font(PdfFonts.BOLD, 8, Color.WHITE)));
            cell.setBackgroundColor(NAVY);
            cell.setPadding(6);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }
        return table;
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(PdfFonts.phrase(text, font));
        cell.setPadding(6);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER);
        return cell;
    }

    /** Balken für den Kompass: gefüllter Anteil plus Prozentzahl. */
    private static PdfPCell bar(int percent) {
        PdfPTable inner = new PdfPTable(new float[]{Math.max(percent, 1), Math.max(100 - percent, 1), 40});
        inner.setWidthPercentage(100);
        PdfPCell filled = new PdfPCell(PdfFonts.phrase(" ", PdfFonts.font(PdfFonts.NORMAL, 6, NAVY)));
        filled.setBackgroundColor(percent > 0 ? GOLD : BG);
        filled.setBorder(Rectangle.NO_BORDER);
        filled.setFixedHeight(8);
        PdfPCell rest = new PdfPCell(PdfFonts.phrase(" ", PdfFonts.font(PdfFonts.NORMAL, 6, NAVY)));
        rest.setBackgroundColor(BG);
        rest.setBorder(Rectangle.NO_BORDER);
        rest.setFixedHeight(8);
        PdfPCell label = new PdfPCell(PdfFonts.phrase(percent + " %", PdfFonts.font(PdfFonts.BOLD, 8, NAVY)));
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


}

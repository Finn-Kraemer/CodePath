package de.codepath.backend.evaluation;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import de.codepath.backend.platform.pdf.PdfFonts;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Bewertungsbögen als PDF (OpenPDF), im Stil von Teilnahmebestätigung und Mitnehm-Paket. */
final class EvaluationPdf {

    /** Eine Zeile eines Bogens; percent = Lage auf der Skala (0–100) für den Balken, sonst null. */
    record Row(boolean heading, String label, String answer, Integer percent, String compare, Integer comparePercent) {
        static Row heading(String label) {
            return new Row(true, label, null, null, null, null);
        }
    }

    /** compareTitle: Überschrift der Vergleichsspalte, null = keine Vergleichsspalte */
    record Section(String title, String note, String compareTitle, List<Row> rows) {
    }

    record Sheet(String roundTitle, String courseName, String person, String intro, LocalDateTime generated,
                 List<Section> sections) {
    }

    record ClassRow(String name, String username, String self, String lesson, String teacher, Integer selfScore,
                    Integer teacherScore) {
    }

    record StatsBlock(String title, String note, List<EvaluationService.CriterionStats> criteria) {
    }

    record Summary(String roundTitle, String courseName, LocalDateTime generated, boolean hasSelf, boolean hasLesson,
                   boolean hasTeacher, List<ClassRow> rows, List<StatsBlock> blocks) {
    }

    private static final Color NAVY = new Color(15, 23, 42);
    private static final Color GOLD = new Color(180, 83, 9);
    private static final Color BG = new Color(241, 245, 249);
    private static final Color BORDER = new Color(203, 213, 225);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale.GERMAN);

    private EvaluationPdf() {
    }

    // ------------------------------------------------------------------ Bogen einer Person

    static byte[] sheet(Sheet data) {
        return document("Bewertungsbogen", document -> {
            header(document, data.roundTitle(), data.courseName(), PdfFonts.clean(data.person()) + " · " + data.generated().format(DATE));
            if (data.intro() != null && !data.intro().isBlank()) {
                Paragraph intro = PdfFonts.paragraph(PdfFonts.clean(data.intro()), PdfFonts.font(PdfFonts.NORMAL, 9, MUTED));
                intro.setSpacingAfter(6);
                document.add(intro);
            }
            if (data.sections().isEmpty()) {
                document.add(PdfFonts.paragraph("Keine Bögen vorhanden.", PdfFonts.font(PdfFonts.NORMAL, 10, MUTED)));
            }
            for (Section section : data.sections()) section(document, section);
        });
    }

    private static void section(Document document, Section section) {
        title(document, section.title());
        if (section.note() != null) {
            Paragraph note = PdfFonts.paragraph(PdfFonts.clean(section.note()), PdfFonts.font(PdfFonts.NORMAL, 9, MUTED));
            note.setSpacingAfter(6);
            document.add(note);
        }
        if (section.rows().isEmpty()) return;
        boolean compare = section.compareTitle() != null;
        PdfPTable table = compare ? table(new float[]{5, 4, 4}, "Kriterium", "Antwort", section.compareTitle())
                : table(new float[]{5, 6}, "Kriterium", "Antwort");
        for (Row row : section.rows()) {
            if (row.heading()) {
                PdfPCell cell = new PdfPCell(PdfFonts.phrase(PdfFonts.clean(row.label()).toUpperCase(Locale.GERMAN), PdfFonts.font(PdfFonts.BOLD, 8, NAVY)));
                cell.setColspan(compare ? 3 : 2);
                cell.setBackgroundColor(BG);
                cell.setPadding(6);
                cell.setBorder(Rectangle.NO_BORDER);
                table.addCell(cell);
                continue;
            }
            table.addCell(cell(PdfFonts.clean(row.label()), PdfFonts.font(PdfFonts.BOLD, 9, NAVY)));
            table.addCell(answer(row.answer(), row.percent(), NAVY));
            if (compare) table.addCell(answer(row.compare(), row.comparePercent(), MUTED));
        }
        document.add(table);
    }

    private static PdfPCell answer(String text, Integer percent, Color color) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(6);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER);
        cell.addElement(PdfFonts.paragraph(PdfFonts.clean(text == null ? "–" : text), PdfFonts.font(PdfFonts.NORMAL, 9, color)));
        if (percent != null) cell.addElement(bar(percent));
        return cell;
    }

    // ------------------------------------------------------------------ Klassenübersicht

    static byte[] summary(Summary data) {
        return document("Klassenauswertung", document -> {
            header(document, data.roundTitle(), data.courseName(), "Klassenauswertung · " + data.generated().format(DATE));
            title(document, "KLASSE");
            List<String> headers = new java.util.ArrayList<>(List.of("Name"));
            List<Float> widths = new java.util.ArrayList<>(List.of(5f));
            if (data.hasSelf()) { headers.add("Selbst"); widths.add(2f); }
            if (data.hasLesson()) { headers.add("Unterricht"); widths.add(2f); }
            if (data.hasTeacher()) { headers.add("Lehrkraft"); widths.add(2f); }
            if (data.hasSelf() && data.hasTeacher()) { headers.add("Selbst / Lehrkraft"); widths.add(3f); }
            float[] w = new float[widths.size()];
            for (int i = 0; i < w.length; i++) w[i] = widths.get(i);
            PdfPTable table = table(w, headers.toArray(String[]::new));
            for (ClassRow row : data.rows()) {
                table.addCell(cell(PdfFonts.clean(row.name()) + " (@" + PdfFonts.clean(row.username()) + ")", PdfFonts.font(PdfFonts.NORMAL, 9, NAVY)));
                if (data.hasSelf()) table.addCell(cell(row.self(), PdfFonts.font(PdfFonts.NORMAL, 9, NAVY)));
                if (data.hasLesson()) table.addCell(cell(row.lesson(), PdfFonts.font(PdfFonts.NORMAL, 9, NAVY)));
                if (data.hasTeacher()) table.addCell(cell(row.teacher(), PdfFonts.font(PdfFonts.NORMAL, 9, NAVY)));
                if (data.hasSelf() && data.hasTeacher()) {
                    table.addCell(cell((row.selfScore() == null ? "–" : row.selfScore() + " %") + " / "
                            + (row.teacherScore() == null ? "–" : row.teacherScore() + " %"), PdfFonts.font(PdfFonts.NORMAL, 9, NAVY)));
                }
            }
            if (data.rows().isEmpty()) {
                PdfPCell empty = cell("Der Kurs hat keine Mitglieder.", PdfFonts.font(PdfFonts.NORMAL, 9, MUTED));
                empty.setColspan(w.length);
                table.addCell(empty);
            }
            document.add(table);

            for (StatsBlock block : data.blocks()) {
                title(document, block.title().toUpperCase(Locale.GERMAN));
                Paragraph note = PdfFonts.paragraph(PdfFonts.clean(block.note()), PdfFonts.font(PdfFonts.NORMAL, 9, MUTED));
                note.setSpacingAfter(6);
                document.add(note);
                for (EvaluationService.CriterionStats c : block.criteria()) stats(document, c);
            }
        });
    }

    private static void stats(Document document, EvaluationService.CriterionStats c) {
        if (c.type() == CriterionType.HEADING) {
            Paragraph p = PdfFonts.paragraph(PdfFonts.clean(c.label()).toUpperCase(Locale.GERMAN), PdfFonts.font(PdfFonts.BOLD, 8, MUTED));
            p.setSpacingBefore(8);
            document.add(p);
            return;
        }
        PdfPTable box = new PdfPTable(new float[]{4, 6, 1.4f});
        box.setWidthPercentage(100);
        box.setSpacingBefore(6);
        box.setKeepTogether(true);
        String head = PdfFonts.clean(c.label()) + "   ·   " + c.answered() + (c.answered() == 1 ? " Antwort" : " Antworten")
                + (c.average() != null ? "   ·   Ø " + String.format(Locale.GERMAN, "%.2f", c.average()) : "");
        PdfPCell title = new PdfPCell(PdfFonts.phrase(head, PdfFonts.font(PdfFonts.BOLD, 9, NAVY)));
        title.setColspan(3);
        title.setPadding(5);
        title.setBorder(Rectangle.BOTTOM);
        title.setBorderColor(NAVY);
        box.addCell(title);
        if (c.distribution() != null) {
            long max = Math.max(1, c.distribution().stream().mapToLong(EvaluationService.Bucket::count).max().orElse(1));
            for (EvaluationService.Bucket b : c.distribution()) {
                String label = b.label() != null && !b.label().equals(b.value())
                        ? (c.type() == CriterionType.SCALE ? b.value() + " · " + b.label() : b.label()) : b.value();
                box.addCell(plain(PdfFonts.clean(label), PdfFonts.font(PdfFonts.NORMAL, 8, MUTED)));
                PdfPCell barCell = new PdfPCell(countBar((int) Math.round(b.count() * 100.0 / max)));
                barCell.setBorder(Rectangle.NO_BORDER);
                barCell.setPadding(3);
                barCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                box.addCell(barCell);
                PdfPCell n = plain(String.valueOf(b.count()), PdfFonts.font(PdfFonts.BOLD, 8, NAVY));
                n.setHorizontalAlignment(Element.ALIGN_RIGHT);
                box.addCell(n);
            }
        }
        if (c.texts() != null) {
            if (c.texts().isEmpty()) {
                PdfPCell none = plain("Keine Antworten.", PdfFonts.font(PdfFonts.ITALIC, 8, MUTED));
                none.setColspan(3);
                box.addCell(none);
            }
            for (EvaluationService.TextAnswer t : c.texts()) {
                PdfPCell text = new PdfPCell();
                text.setColspan(3);
                text.setPadding(5);
                text.setBorder(Rectangle.LEFT);
                text.setBorderWidthLeft(2);
                text.setBorderColor(BORDER);
                text.addElement(PdfFonts.paragraph(PdfFonts.clean(t.text()), PdfFonts.font(PdfFonts.NORMAL, 9, NAVY)));
                if (t.author() != null) text.addElement(PdfFonts.paragraph("– " + PdfFonts.clean(t.author()), PdfFonts.font(PdfFonts.NORMAL, 7, MUTED)));
                box.addCell(text);
            }
        }
        document.add(box);
    }

    // ------------------------------------------------------------------ Bausteine

    private interface Body {
        void write(Document document) throws DocumentException;
    }

    private static byte[] document(String footer, Body body) {
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
                            PdfFonts.phrase("CodePath · " + footer + " · Seite " + writer.getPageNumber(), PdfFonts.font(PdfFonts.NORMAL, 7, MUTED)),
                            297, 25, 0);
                }
            });
            document.addTitle(footer);
            document.open();
            body.write(document);
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static void header(Document document, String title, String course, String line) {
        Paragraph p = PdfFonts.paragraph(PdfFonts.clean(title).toUpperCase(Locale.GERMAN), PdfFonts.font(PdfFonts.BOLD, 20, NAVY));
        p.setSpacingAfter(4);
        document.add(p);
        document.add(PdfFonts.paragraph(PdfFonts.clean(course).toUpperCase(Locale.GERMAN), PdfFonts.font(PdfFonts.BOLD, 10, GOLD)));
        Paragraph who = PdfFonts.paragraph(line, PdfFonts.font(PdfFonts.NORMAL, 11, NAVY));
        who.setSpacingBefore(10);
        who.setSpacingAfter(8);
        document.add(who);
    }

    private static void title(Document document, String title) {
        Paragraph p = PdfFonts.paragraph(PdfFonts.clean(title), PdfFonts.font(PdfFonts.BOLD, 11, NAVY));
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        document.add(p);
    }

    private static PdfPTable table(float[] widths, String... headers) {
        PdfPTable table = new PdfPTable(widths.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setHeaderRows(1);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(PdfFonts.phrase(PdfFonts.clean(h).toUpperCase(Locale.GERMAN), PdfFonts.font(PdfFonts.BOLD, 8, Color.WHITE)));
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

    private static PdfPCell plain(String text, Font font) {
        PdfPCell cell = new PdfPCell(PdfFonts.phrase(text, font));
        cell.setPadding(3);
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    /** Lage auf der Skala als Balken */
    private static PdfPTable bar(int percent) {
        return fill(percent, GOLD, 5);
    }

    private static PdfPTable countBar(int percent) {
        return fill(percent, NAVY, 7);
    }

    private static PdfPTable fill(int percent, Color color, float height) {
        int p = Math.max(0, Math.min(100, percent));
        PdfPTable inner = new PdfPTable(new float[]{Math.max(p, 0.5f), Math.max(100 - p, 0.5f)});
        inner.setWidthPercentage(100);
        inner.setSpacingBefore(3);
        PdfPCell filled = new PdfPCell(PdfFonts.phrase(" ", PdfFonts.font(PdfFonts.NORMAL, 4, NAVY)));
        filled.setBackgroundColor(p > 0 ? color : BG);
        filled.setBorder(Rectangle.NO_BORDER);
        filled.setFixedHeight(height);
        PdfPCell rest = new PdfPCell(PdfFonts.phrase(" ", PdfFonts.font(PdfFonts.NORMAL, 4, NAVY)));
        rest.setBackgroundColor(BG);
        rest.setBorder(Rectangle.NO_BORDER);
        rest.setFixedHeight(height);
        inner.addCell(filled);
        inner.addCell(rest);
        return inner;
    }


}

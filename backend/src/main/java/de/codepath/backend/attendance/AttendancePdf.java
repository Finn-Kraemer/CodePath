package de.codepath.backend.attendance;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import de.codepath.backend.platform.pdf.PdfFonts;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Locale;

/** Anwesenheit einer Person als PDF, im Stil der anderen Exporte. */
final class AttendancePdf {

    private static final Color NAVY = new Color(15, 23, 42);
    private static final Color GOLD = new Color(180, 83, 9);
    private static final Color BORDER = new Color(203, 213, 225);

    private AttendancePdf() {
    }

    static byte[] render(String courseName, AttendanceService.AttendancePersonView person) {
        Document document = new Document(PageSize.A4, 40, 40, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.addTitle("Anwesenheit " + person.displayName());
        document.open();
        Paragraph title = PdfFonts.paragraph("ANWESENHEIT", PdfFonts.font(PdfFonts.BOLD, 20, NAVY));
        title.setSpacingAfter(4);
        document.add(title);
        document.add(PdfFonts.paragraph(courseName == null ? "" : courseName.toUpperCase(Locale.GERMAN), PdfFonts.font(PdfFonts.BOLD, 10, GOLD)));
        Paragraph who = PdfFonts.paragraph(person.displayName() + " (@" + person.username() + ") · Stand "
                + LocalDate.now().format(AttendanceService.DATE), PdfFonts.font(PdfFonts.NORMAL, 11, NAVY));
        who.setSpacingBefore(10);
        document.add(who);
        Paragraph rate = PdfFonts.paragraph(person.rate().text(), PdfFonts.font(PdfFonts.BOLD, 12, NAVY));
        rate.setSpacingBefore(12);
        rate.setSpacingAfter(12);
        document.add(rate);

        PdfPTable table = new PdfPTable(new float[]{2, 5, 2, 4});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String h : new String[]{"Datum", "Termin", "Status", "Notiz"}) {
            PdfPCell cell = new PdfPCell(PdfFonts.phrase(h.toUpperCase(Locale.GERMAN), PdfFonts.font(PdfFonts.BOLD, 8, Color.WHITE)));
            cell.setBackgroundColor(NAVY);
            cell.setPadding(6);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }
        for (AttendanceService.AttendancePersonEntry e : person.entries()) {
            table.addCell(cell(e.date().format(AttendanceService.DATE)));
            table.addCell(cell(e.title() == null ? "" : e.title()));
            table.addCell(cell(AttendanceService.label(e.status())));
            table.addCell(cell(e.note() == null ? "" : e.note()));
        }
        if (person.entries().isEmpty()) {
            PdfPCell empty = cell("Noch keine Termine erfasst.");
            empty.setColspan(4);
            table.addCell(empty);
        }
        document.add(table);
        document.close();
        return out.toByteArray();
    }

    private static PdfPCell cell(String text) {
        PdfPCell cell = new PdfPCell(PdfFonts.phrase(text, PdfFonts.font(PdfFonts.NORMAL, 9, NAVY)));
        cell.setPadding(5);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER);
        return cell;
    }

}

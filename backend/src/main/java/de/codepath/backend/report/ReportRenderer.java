package de.codepath.backend.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import de.codepath.backend.identity.User;
import de.codepath.backend.platform.pdf.PdfFonts;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Zeichnet die Teilnahmebestätigung als PDF (OpenPDF). Kein amtliches Dokument (Annahme A6).
 */
public class ReportRenderer {

    public record ModuleResult(String title, int achieved, int max) {
    }

    byte[] render(User user, String courseName, List<ModuleResult> results) {
        Document document = new Document(PageSize.A4, 40, 40, 60, 60);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            
            // Background Watermark Event
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document document) {
                    PdfContentByte canvas = writer.getDirectContentUnder();
                    Font waterFont = PdfFonts.font(PdfFonts.BOLD, 50, new Color(226, 232, 240, 40));
                    Phrase watermark = PdfFonts.phrase("TEILNAHMEBESTÄTIGUNG", waterFont);
                    ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER, watermark, 297, 421, 45);
                    
                    // Top gold line
                    PdfContentByte topCb = writer.getDirectContent();
                    topCb.setColorFill(new Color(180, 83, 9));
                    topCb.rectangle(0, 832, 595, 10);
                    topCb.fill();
                }
            });

            document.open();

            // Institutional Colors
            Color navy = new Color(15, 23, 42);
            Color gold = new Color(180, 83, 9);
            Color bgGray = new Color(241, 245, 249);
            Color borderGray = new Color(203, 213, 225);

            // Header Section
            PdfPTable headerTable = new PdfPTable(1);
            headerTable.setWidthPercentage(100);
            
            PdfPCell hCell = new PdfPCell();
            hCell.setBorder(Rectangle.NO_BORDER);
            hCell.setPaddingBottom(30);
            
            Paragraph pTitle = PdfFonts.paragraph("TEILNAHMEBESTÄTIGUNG", PdfFonts.font(PdfFonts.BOLD, 22, navy));
            pTitle.setSpacingAfter(5);
            hCell.addElement(pTitle);
            
            Paragraph pSub = PdfFonts.paragraph(courseName != null ? courseName.toUpperCase(java.util.Locale.GERMAN) : "BERUFSORIENTIERUNG IT", PdfFonts.font(PdfFonts.BOLD, 12, gold));
            hCell.addElement(pSub);
            
            headerTable.addCell(hCell);
            document.add(headerTable);

            // Form-like User Info
            PdfPTable userBox = new PdfPTable(2);
            userBox.setWidthPercentage(100);
            userBox.setWidths(new float[]{1, 2});
            userBox.setSpacingAfter(40);

            addFormCell(userBox, "NAME", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername(), navy, bgGray, borderGray);
            addFormCell(userBox, "BENUTZERNAME", "@" + user.getUsername(), navy, bgGray, borderGray);
            addFormCell(userBox, "DATUM", java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd. MMMM yyyy", java.util.Locale.GERMAN)), navy, bgGray, borderGray);
            
            document.add(userBox);

            // Table of results
            Paragraph tableTitle = PdfFonts.paragraph("ERGEBNISSE DER LERNMODULE", PdfFonts.font(PdfFonts.BOLD, 10, navy));
            tableTitle.setSpacingAfter(10);
            document.add(tableTitle);

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4, 1.5f, 1.5f, 1});
            table.setHeaderRows(1);

            String[] colHeaders = {"Lernmodul", "Ergebnis", "Max.", "Note"};
            for (String ch : colHeaders) {
                PdfPCell cell = new PdfPCell(PdfFonts.phrase(ch.toUpperCase(), PdfFonts.font(PdfFonts.BOLD, 9, Color.WHITE)));
                cell.setBackgroundColor(navy);
                cell.setPadding(10);
                cell.setBorder(Rectangle.NO_BORDER);
                table.addCell(cell);
            }

            int totalAchieved = 0;
            int totalMax = 0;
            for (int i = 0; i < results.size(); i++) {
                ModuleResult r = results.get(i);
                if (r.max() <= 0) continue;
                int grade = calculateGrade((double) r.achieved() / r.max());
                Color rowBg = (i % 2 == 0) ? Color.WHITE : new Color(248, 250, 252);
                table.addCell(createCell(r.title(), PdfFonts.font(PdfFonts.NORMAL, 10, Color.BLACK), rowBg, borderGray));
                table.addCell(createCell(r.achieved() + " Pkt.", PdfFonts.font(PdfFonts.NORMAL, 10, Color.BLACK), rowBg, borderGray));
                table.addCell(createCell(r.max() + " Pkt.", PdfFonts.font(PdfFonts.NORMAL, 10, Color.BLACK), rowBg, borderGray));
                table.addCell(createCell(String.valueOf(grade), PdfFonts.font(PdfFonts.BOLD, 10, Color.BLACK), rowBg, borderGray));
                totalAchieved += r.achieved();
                totalMax += r.max();
            }
            document.add(table);

            // Final Evaluation
            if (totalMax > 0) {
                double totalPct = (double) totalAchieved / totalMax;
                int finalGrade = calculateGrade(totalPct);

                PdfPTable footerTable = new PdfPTable(1);
                footerTable.setSpacingBefore(40);
                footerTable.setWidthPercentage(100);

                PdfPCell summaryCell = new PdfPCell();
                summaryCell.setPadding(25);
                summaryCell.setBorderWidth(1.5f);
                summaryCell.setBorderColor(navy);
                summaryCell.setBackgroundColor(bgGray);

                Paragraph resTitle = PdfFonts.paragraph("GESAMTBEWERTUNG", PdfFonts.font(PdfFonts.BOLD, 12, navy));
                resTitle.setSpacingAfter(10);
                summaryCell.addElement(resTitle);

                summaryCell.addElement(PdfFonts.paragraph(String.format("Erreicht wurden %d von %d Punkten.", totalAchieved, totalMax), PdfFonts.font(PdfFonts.NORMAL, 10, Color.BLACK)));
                
                Paragraph pFinalGrade = PdfFonts.paragraph("GESAMTNOTE: " + finalGrade, PdfFonts.font(PdfFonts.BOLD, 18, gold));
                pFinalGrade.setSpacingBefore(10);
                summaryCell.addElement(pFinalGrade);

                footerTable.addCell(summaryCell);
                document.add(footerTable);
            }

            document.close();
        } catch (DocumentException e) {
            throw new RuntimeException(e);
        }

        return out.toByteArray();
    }

    private void addFormCell(PdfPTable table, String label, String value, Color navy, Color bg, Color border) {
        PdfPCell lCell = new PdfPCell(PdfFonts.phrase(label, PdfFonts.font(PdfFonts.BOLD, 7, Color.GRAY)));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setPaddingTop(10);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(PdfFonts.phrase(value, PdfFonts.font(PdfFonts.BOLD, 11, navy)));
        vCell.setBorder(Rectangle.BOTTOM);
        vCell.setBorderColor(border);
        vCell.setPaddingBottom(5);
        table.addCell(vCell);
    }

    private PdfPCell createCell(String text, Font font, Color bg, Color border) {
        PdfPCell cell = new PdfPCell(PdfFonts.phrase(text, font));
        cell.setPadding(10);
        cell.setBackgroundColor(bg);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(border);
        return cell;
    }

    private int calculateGrade(double percentage) {
        if (percentage >= 0.92) return 1;
        if (percentage >= 0.81) return 2;
        if (percentage >= 0.67) return 3;
        if (percentage >= 0.50) return 4;
        if (percentage >= 0.30) return 5;
        return 6;
    }
}

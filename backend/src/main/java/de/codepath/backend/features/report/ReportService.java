package de.codepath.backend.features.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.modules.ModuleRepository;
import de.codepath.backend.features.tasks.*;
import de.codepath.backend.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ModuleRepository moduleRepository;
    private final TaskRepository taskRepository;
    private final UserTaskCompletionRepository completionRepository;

    public byte[] generateStudentReport(User user) {
        Document document = new Document(PageSize.A4, 40, 40, 60, 60);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            
            // Background Watermark Event
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document document) {
                    PdfContentByte canvas = writer.getDirectContentUnder();
                    Font waterFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 50, new Color(226, 232, 240, 40));
                    Phrase watermark = new Phrase("OFFIZIELLER LEISTUNGSNACHWEIS", waterFont);
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
            
            Paragraph pTitle = new Paragraph("ZERTIFIKAT ÜBER DIE TEILNAHME", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, navy));
            pTitle.setSpacingAfter(5);
            hCell.addElement(pTitle);
            
            Paragraph pSub = new Paragraph("BERUFSORIENTIERUNGSTAG IT 2026", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, gold));
            hCell.addElement(pSub);
            
            headerTable.addCell(hCell);
            document.add(headerTable);

            // Form-like User Info
            PdfPTable userBox = new PdfPTable(2);
            userBox.setWidthPercentage(100);
            userBox.setWidths(new float[]{1, 2});
            userBox.setSpacingAfter(40);

            addFormCell(userBox, "NAME DES ABSOLVENTEN", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername(), navy, bgGray, borderGray);
            addFormCell(userBox, "SYSTEM-IDENTIFIKATOR", "@" + user.getUsername(), navy, bgGray, borderGray);
            addFormCell(userBox, "DATUM DER PRÜFUNG", java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd. MMMM yyyy", java.util.Locale.GERMAN)), navy, bgGray, borderGray);
            
            document.add(userBox);

            // Table of results
            Paragraph tableTitle = new Paragraph("ERGEBNISSE DER LERNMODULE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, navy));
            tableTitle.setSpacingAfter(10);
            document.add(tableTitle);

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4, 1.5f, 1.5f, 1});
            table.setHeaderRows(1);

            String[] colHeaders = {"Lernmodul", "Ergebnis", "Max.", "Note"};
            for (String ch : colHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(ch.toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
                cell.setBackgroundColor(navy);
                cell.setPadding(10);
                cell.setBorder(Rectangle.NO_BORDER);
                table.addCell(cell);
            }

            List<Module> modules = moduleRepository.findByIsUnlockedTrue(org.springframework.data.domain.Sort.by("sortOrder"));
            int totalAchieved = 0;
            int totalMax = 0;

            for (int i = 0; i < modules.size(); i++) {
                Module m = modules.get(i);
                List<Task> tasks = taskRepository.findByModuleIdOrderBySortOrderAsc(m.getId());
                int mAchieved = 0;
                int mMax = 0;

                for (Task t : tasks) {
                    mMax += t.getPoints();
                    UserTaskCompletion c = completionRepository.findByUser_IdAndTask_Id(user.getId(), t.getId()).orElse(null);
                    if (c != null && c.isCompleted()) mAchieved += c.getPointsAwarded();
                }

                if (mMax > 0) {
                    double pct = (double) mAchieved / mMax;
                    int grade = calculateGrade(pct);
                    Color rowBg = (i % 2 == 0) ? Color.WHITE : new Color(248, 250, 252);

                    table.addCell(createCell(m.getTitle(), FontFactory.getFont(FontFactory.HELVETICA, 10), rowBg, borderGray));
                    table.addCell(createCell(mAchieved + " Pkt.", FontFactory.getFont(FontFactory.HELVETICA, 10), rowBg, borderGray));
                    table.addCell(createCell(mMax + " Pkt.", FontFactory.getFont(FontFactory.HELVETICA, 10), rowBg, borderGray));
                    table.addCell(createCell(String.valueOf(grade), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10), rowBg, borderGray));

                    totalAchieved += mAchieved;
                    totalMax += mMax;
                }
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

                Paragraph resTitle = new Paragraph("GESAMTBEWERTUNG", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, navy));
                resTitle.setSpacingAfter(10);
                summaryCell.addElement(resTitle);

                summaryCell.addElement(new Paragraph(String.format("Der Teilnehmer hat %d von %d Punkten erreicht.", totalAchieved, totalMax), FontFactory.getFont(FontFactory.HELVETICA, 10)));
                
                Paragraph pFinalGrade = new Paragraph("ABSCHLUSSNOTE: " + finalGrade, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, gold));
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
        PdfPCell lCell = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.GRAY)));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setPaddingTop(10);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, navy)));
        vCell.setBorder(Rectangle.BOTTOM);
        vCell.setBorderColor(border);
        vCell.setPaddingBottom(5);
        table.addCell(vCell);
    }

    private PdfPCell createCell(String text, Font font, Color bg, Color border) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
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

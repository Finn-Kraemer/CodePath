package de.codepath.backend.features.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
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
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Font styles
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new Color(0, 32, 63));
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);

            // Title
            Paragraph title = new Paragraph("LEISTUNGSNACHWEIS", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);
            
            Paragraph subTitle = new Paragraph("CodePath - Kompetenzzentrum IT", FontFactory.getFont(FontFactory.HELVETICA, 12, new Color(193, 161, 97)));
            subTitle.setAlignment(Element.ALIGN_CENTER);
            subTitle.setSpacingAfter(30);
            document.add(subTitle);

            // User Info
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingAfter(30);
            
            addInfoCell(infoTable, "Teilnehmer:", boldFont);
            addInfoCell(infoTable, user.getDisplayName() != null ? user.getDisplayName() : user.getUsername(), normalFont);
            
            addInfoCell(infoTable, "Benutzername:", boldFont);
            addInfoCell(infoTable, "@" + user.getUsername(), normalFont);
            
            addInfoCell(infoTable, "Ausstellungsdatum:", boldFont);
            addInfoCell(infoTable, java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")), normalFont);
            
            document.add(infoTable);

            // Results Table
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4, 2, 2, 1});

            // Table Headers
            String[] headers = {"Lernmodul", "Erreicht", "Max.", "Note"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header.toUpperCase(), headerFont));
                cell.setBackgroundColor(new Color(0, 32, 63)); 
                cell.setPadding(10);
                cell.setBorderColor(Color.WHITE);
                table.addCell(cell);
            }

            // Only fetch modules that are unlocked by admin
            List<Module> modules = moduleRepository.findByIsUnlockedTrue(org.springframework.data.domain.Sort.by("sortOrder"));
            int totalAchieved = 0;
            int totalMaxAvailable = 0;

            for (Module module : modules) {
                List<Task> tasks = taskRepository.findByModuleIdOrderBySortOrderAsc(module.getId());
                int moduleAchieved = 0;
                int moduleMax = 0;

                for (Task task : tasks) {
                    moduleMax += task.getPoints();
                    
                    UserTaskCompletion completion = completionRepository.findByUser_IdAndTask_Id(user.getId(), task.getId())
                            .orElse(null);
                    
                    if (completion != null && completion.isCompleted()) {
                        moduleAchieved += completion.getPointsAwarded();
                    }
                }

                if (moduleMax > 0) {
                    double percentage = (double) moduleAchieved / moduleMax;
                    int grade = calculateGrade(percentage);

                    table.addCell(createCell(module.getTitle(), normalFont));
                    table.addCell(createCell(moduleAchieved + " Pkt.", normalFont));
                    table.addCell(createCell(moduleMax + " Pkt.", normalFont));
                    table.addCell(createCell(String.valueOf(grade), boldFont));

                    totalAchieved += moduleAchieved;
                    totalMaxAvailable += moduleMax;
                }
            }

            document.add(table);

            // Summary
            if (totalMaxAvailable > 0) {
                double totalPercentage = (double) totalAchieved / totalMaxAvailable;
                int finalGrade = calculateGrade(totalPercentage);

                PdfPTable summaryTable = new PdfPTable(1);
                summaryTable.setWidthPercentage(100);
                summaryTable.setSpacingBefore(40);
                
                PdfPCell summaryCell = new PdfPCell();
                summaryCell.setPadding(20);
                summaryCell.setBackgroundColor(new Color(248, 250, 252));
                summaryCell.setBorderColor(new Color(226, 232, 240));
                
                Paragraph p1 = new Paragraph("GESAMTERGEBNIS", boldFont);
                p1.setSpacingAfter(10);
                summaryCell.addElement(p1);
                
                summaryCell.addElement(new Paragraph("Gesamtpunktzahl: " + totalAchieved + " von " + totalMaxAvailable + " Punkten", normalFont));
                
                Paragraph pGrade = new Paragraph("ABSCHLUSSNOTE: " + finalGrade, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(193, 161, 97)));
                pGrade.setSpacingBefore(10);
                summaryCell.addElement(pGrade);
                
                summaryTable.addCell(summaryCell);
                document.add(summaryTable);
                
                Paragraph footerNote = new Paragraph("\n* Hinweis: Nur vom Dozenten freigeschaltete Module wurden bewertet. Aufgaben, die durch Fehlversuche gesperrt wurden, zählen mit 0 Punkten in das Gesamtergebnis.", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, Color.GRAY));
                footerNote.setSpacingBefore(20);
                document.add(footerNote);
            }

            document.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Error generating PDF", e);
        }

        return out.toByteArray();
    }

    private void addInfoCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private PdfPCell createCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(8);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
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

package de.codepath.backend.worksheet;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import de.codepath.backend.platform.pdf.PdfFonts;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zeichnet ein Aufgabenblatt als PDF (OpenPDF) – als Schülerfassung mit Platz zum Schreiben oder als
 * Lösung, in der Antworten, Kreuze und Lücken rot eingetragen sind. Fehlende oder unsinnige Werte im
 * Inhalt werden durch Standardwerte ersetzt, damit auch ein halb fertiges Blatt eine Vorschau ergibt.
 */
public final class WorksheetRenderer {

    public enum Variant { STUDENT, SOLUTION }

    private static final Color INK = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color LINE = new Color(148, 163, 184);
    private static final Color LIGHT = new Color(203, 213, 225);
    private static final Color PAPER = new Color(241, 245, 249);
    private static final Color CODE_BG = new Color(248, 250, 252);
    private static final Color RED = new Color(190, 18, 60);
    private static final Color RED_BG = new Color(255, 241, 242);
    private static final Color DEFAULT_ACCENT = new Color(180, 83, 9);
    private static final float MM = 72f / 25.4f;

    private static final Map<String, Color[]> BOX_STYLES = Map.of(
            "info", new Color[]{new Color(29, 78, 216), new Color(239, 246, 255)},
            "tip", new Color[]{new Color(21, 128, 61), new Color(240, 253, 244)},
            "important", new Color[]{new Color(190, 18, 60), new Color(255, 241, 242)},
            "neutral", new Color[]{new Color(100, 116, 139), PAPER});

    private static final Pattern INLINE = Pattern.compile(
            "\\*\\*(.+?)\\*\\*|\\*(?!\\s)(.+?)(?<!\\s)\\*|`([^`]+)`|\\[\\[(.+?)]]");
    private static final Pattern GAP = Pattern.compile("\\[\\[(.+?)]]");
    private static final Pattern FULL_GAP = Pattern.compile("^\\s*\\[\\[(.*?)]]\\s*$");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*•]\\s+(.*)$");
    private static final Pattern NUMBERED = Pattern.compile("^\\s*\\d+[.)]\\s+(.*)$");

    /** Blöcke ohne eigenen Inhalt: allein ergeben sie keine sinnvolle Seite. */
    private static final java.util.Set<String> LAYOUT_ONLY = java.util.Set.of("pagebreak", "divider", "space");

    private static final int NORMAL = 0;
    private static final int BOLD = 1;
    private static final int ITALIC = 2;

    private final String title;
    private final JsonNode settings;
    private final JsonNode blocks;
    private final boolean solution;
    private final Function<String, byte[]> images;

    private final Color accent;
    private final boolean serif;
    private final float size;
    private final float lineHeight;
    private final boolean showPoints;
    private final boolean numbering;
    private final String numberLabel;

    private PdfWriter writer;
    private Document document;
    private PdfTemplate boxEmpty;
    private PdfTemplate boxChecked;
    private int taskNumber;

    private WorksheetRenderer(String title, JsonNode content, Variant variant, Function<String, byte[]> images) {
        this.title = title;
        this.settings = content.path("settings");
        this.blocks = content.path("blocks");
        this.solution = variant == Variant.SOLUTION;
        this.images = images;
        this.accent = color(text(settings, "accent"));
        this.serif = "serif".equals(text(settings, "font"));
        this.size = number(settings, "fontSize", 11, 9, 14);
        this.lineHeight = number(settings, "lineSpacing", 9, 6, 14) * MM;
        this.showPoints = flag(settings, "showPoints", true);
        this.numbering = flag(settings, "numbering", true);
        String label = text(settings, "numberLabel").strip();
        this.numberLabel = label.isEmpty() ? "Aufgabe" : label;
    }

    /**
     * @param images lädt ein hochgeladenes Bild über seinen Schlüssel, {@code null} wenn es fehlt
     */
    public static byte[] render(String title, JsonNode content, Variant variant, Function<String, byte[]> images) {
        return new WorksheetRenderer(title, content, variant, images).render();
    }

    /** Summe der Punkte aller Aufgaben (für das Punktefeld im Kopf und die Übersicht). */
    static double totalPoints(JsonNode blocks) {
        double total = 0;
        for (JsonNode block : blocks) {
            if (WorksheetContent.isTask(block)) total += Math.max(0, block.path("points").asDouble(0));
        }
        return total;
    }

    private byte[] render() {
        Rectangle page = switch (text(settings, "pageSize")) {
            case "A5" -> PageSize.A5;
            case "LETTER" -> PageSize.LETTER;
            default -> PageSize.A4;
        };
        if ("landscape".equals(text(settings, "orientation"))) page = page.rotate();
        float margin = switch (text(settings, "margins")) {
            case "narrow" -> 30;
            case "wide" -> 64;
            default -> 46;
        };
        document = new Document(page, margin, margin, margin + 4, margin + 16);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new Frame());
            document.addTitle(clean(title) + (solution ? " – Lösung" : ""));
            document.addCreator("CodePath");
            document.open();
            boxEmpty = checkbox(false);
            boxChecked = checkbox(true);

            header();
            // Position im ganzen Blatt (nicht nur unter den sichtbaren Blöcken), damit gemischte
            // Reihenfolgen in Schülerblatt und Lösung gleich sind
            java.util.List<Integer> visible = new ArrayList<>();
            for (int i = 0; i < blocks.size(); i++) {
                String show = text(blocks.get(i), "show");
                if (("student".equals(show) && solution) || ("solution".equals(show) && !solution)) continue;
                visible.add(i);
            }
            // Umbrüche, Trennlinien und Freiraum am Ende erzeugen nur leere Seiten
            while (!visible.isEmpty() && LAYOUT_ONLY.contains(text(blocks.get(visible.getLast()), "type"))) visible.removeLast();
            boolean pageStart = false;
            for (int i : visible) {
                JsonNode block = blocks.get(i);
                String type = text(block, "type");
                if ("pagebreak".equals(type)) {
                    document.newPage();
                    pageStart = true;
                    continue;
                }
                // Trennlinie oder Freiraum direkt nach einem Umbruch: oben auf der Seite ohne Sinn
                if (pageStart && LAYOUT_ONLY.contains(type)) continue;
                pageStart = false;
                block(block, i);
            }
            if (writer.getPageNumber() == 1 && visible.isEmpty()) document.add(new Paragraph(" "));
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- Kopf und Rahmen

    /** Farbiger Balken oben, Fußzeile mit eigenem Text und „Seite x von y“. */
    private class Frame extends PdfPageEventHelper {
        private PdfTemplate total;
        private BaseFont baseFont;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(24, 12);
            baseFont = font(NORMAL, 8, MUTED).getCalculatedBaseFont(false);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Rectangle page = document.getPageSize();
            cb.saveState();
            cb.setColorFill(solution ? RED : accent);
            cb.rectangle(0, page.getHeight() - 6, page.getWidth(), 6);
            cb.fill();
            cb.restoreState();

            float y = document.bottomMargin() / 2;
            Font small = font(NORMAL, 8, MUTED);
            String footer = text(settings, "footer").strip();
            if (solution) footer = footer.isEmpty() ? "Lösung" : footer + "  ·  Lösung";
            if (!footer.isEmpty()) {
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, text(footer.replace('\n', ' '), small),
                        document.left(), y, 0);
            }
            if (flag(settings, "pageNumbers", true)) {
                float x = document.right() - 12;
                ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("Seite " + writer.getPageNumber() + " von", small), x, y, 0);
                cb.addTemplate(total, x + 2.5f, y);
            }
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(baseFont, 8);
            total.setColorFill(MUTED);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }

    private void header() {
        String school = text(settings, "school").strip();
        String subject = text(settings, "subject").strip();
        String kind = text(settings, "kind").strip();
        String logoKey = text(settings, "logo");
        Image logo = logoKey.isEmpty() ? null : image(logoKey);

        PdfPTable top = new PdfPTable(logo != null ? new float[]{1.2f, 6, 3} : new float[]{7, 3});
        top.setWidthPercentage(100);
        if (logo != null) {
            logo.scaleToFit(48, 36);
            PdfPCell cell = new PdfPCell(logo, false);
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            top.addCell(cell);
        }
        PdfPCell left = plain();
        left.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (!school.isEmpty()) left.addElement(PdfFonts.paragraph(school, font(BOLD, size - 2, MUTED)));
        if (!subject.isEmpty()) left.addElement(PdfFonts.paragraph(subject, font(NORMAL, size - 1, MUTED)));
        top.addCell(left);

        PdfPCell right = plain();
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (solution) {
            Paragraph badge = new Paragraph("LÖSUNG", font(BOLD, size, RED));
            badge.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(badge);
        }
        if (!kind.isEmpty()) {
            Paragraph p = PdfFonts.paragraph(kind.toUpperCase(Locale.GERMAN), font(BOLD, size - 1, accent));
            p.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(p);
        }
        top.addCell(right);
        if (logo != null || !school.isEmpty() || !subject.isEmpty() || !kind.isEmpty() || solution) document.add(top);

        Paragraph heading = PdfFonts.paragraph(title, font(BOLD, size + 9, INK));
        heading.setSpacingBefore(6);
        document.add(heading);
        String subtitle = text(settings, "subtitle").strip();
        if (!subtitle.isEmpty()) {
            Paragraph p = PdfFonts.paragraph(subtitle, font(NORMAL, size + 1, MUTED));
            p.setSpacingBefore(2);
            document.add(p);
        }

        fields();

        String instructions = text(settings, "instructions").strip();
        if (!instructions.isEmpty()) {
            PdfPTable box = new PdfPTable(1);
            box.setWidthPercentage(100);
            box.setSpacingBefore(10);
            PdfPCell cell = new PdfPCell();
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setBackgroundColor(PAPER);
            cell.setPadding(8);
            cell.setPaddingTop(4);
            for (Element e : rich(instructions, size - 1, INK, false)) cell.addElement(e);
            box.addCell(cell);
            document.add(box);
        }
        Paragraph gap = new Paragraph(" ", font(NORMAL, 4, INK));
        gap.setSpacingAfter(2);
        document.add(gap);
    }

    /** Zeile mit Name, Klasse, Datum, Punkten und Note – je nach Einstellung. */
    private void fields() {
        record Field(String label, String value, float width) {
        }
        java.util.List<Field> fields = new ArrayList<>();
        if (flag(settings, "nameField", true)) fields.add(new Field("Name", "", 4));
        if (flag(settings, "classField", true)) fields.add(new Field("Klasse", text(settings, "className").strip(), 1.6f));
        if (flag(settings, "dateField", true)) fields.add(new Field("Datum", text(settings, "date").strip(), 2));
        double total = totalPoints(blocks);
        if (flag(settings, "pointsField", true) && total > 0) fields.add(new Field("Punkte", "\u00A0".repeat(5) + "/\u00A0" + points(total), 2.6f));
        if (flag(settings, "gradeField", false)) fields.add(new Field("Note", "", 1.4f));
        if (fields.isEmpty()) return;

        float[] widths = new float[fields.size() * 2 - 1];
        for (int i = 0; i < fields.size(); i++) {
            widths[i * 2] = fields.get(i).width();
            if (i > 0) widths[i * 2 - 1] = 0.25f;
        }
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(14);
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) table.addCell(plain());
            Field field = fields.get(i);
            Phrase phrase = new Phrase();
            phrase.add(text(field.label() + ": ", font(BOLD, size - 2, MUTED)));
            phrase.add(text(field.value(), font(NORMAL, size, INK)));
            PdfPCell cell = new PdfPCell(phrase);
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(LINE);
            cell.setBorderWidth(0.7f);
            cell.setPaddingLeft(0);
            cell.setPaddingBottom(4);
            cell.setVerticalAlignment(Element.ALIGN_BOTTOM);
            cell.setFixedHeight(Math.max(lineHeight, size * 2));
            table.addCell(cell);
        }
        document.add(table);
    }

    // ---------------------------------------------------------------- Blöcke

    private void block(JsonNode block, int index) {
        switch (text(block, "type")) {
            case "heading" -> heading(block);
            case "text" -> {
                for (Element e : rich(text(block, "text"), size, INK, false)) document.add(e);
            }
            case "box" -> box(block);
            case "task" -> task(block);
            case "choice" -> choice(block);
            case "gap" -> gapText(block);
            case "truefalse" -> trueFalse(block);
            case "matching" -> matching(block, index);
            case "order" -> order(block, index);
            case "table" -> table(block);
            case "code" -> code(block);
            case "image" -> image(block);
            case "space" -> space(number(block, "size", 3, 1, 40));
            case "divider" -> {
                Paragraph p = new Paragraph(new Chunk(new LineSeparator(0.6f, 100, LIGHT, Element.ALIGN_CENTER, -2)));
                p.setSpacingBefore(size * 0.6f);
                p.setSpacingAfter(size * 0.6f);
                document.add(p);
            }
            case "pagebreak" -> document.newPage();
            default -> {
                // unbekannte Blöcke lässt die Prüfung nicht durch; alte Daten werden übersprungen
            }
        }
    }

    private void heading(JsonNode block) {
        boolean small = number(block, "level", 1, 1, 2) == 2;
        Paragraph p = PdfFonts.paragraph(text(block, "text"), font(BOLD, small ? size + 1 : size + 4, small ? accent : INK));
        p.setSpacingBefore(size * (small ? 0.8f : 1.2f));
        p.setSpacingAfter(size * 0.3f);
        document.add(p);
    }

    private void box(JsonNode block) {
        String style = text(block, "style");
        Color[] colors = "accent".equals(style) ? new Color[]{accent, tint(accent)} : BOX_STYLES.getOrDefault(style, BOX_STYLES.get("info"));
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(size * 0.6f);
        table.setSpacingAfter(size * 0.6f);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.LEFT);
        cell.setBorderColorLeft(colors[0]);
        cell.setBorderWidthLeft(3);
        cell.setBackgroundColor(colors[1]);
        cell.setPadding(8);
        cell.setPaddingTop(4);
        cell.setPaddingLeft(10);
        String heading = text(block, "title").strip();
        if (!heading.isEmpty()) cell.addElement(PdfFonts.paragraph(heading, font(BOLD, size, colors[0])));
        for (Element e : rich(text(block, "text"), size, INK, false)) cell.addElement(e);
        table.addCell(cell);
        document.add(table);
    }

    /** Freie Aufgabe mit Schreibplatz; optional mit Teilaufgaben a), b), … */
    private void task(JsonNode block) {
        taskHead(block);
        String answer = text(block, "answer");
        int lines = number(block, "size", 4, 0, 40);
        JsonNode parts = block.path("parts");
        if (parts.isArray() && !parts.isEmpty()) {
            int i = 0;
            for (JsonNode part : parts) {
                PdfPTable row = new PdfPTable(new float[]{0.6f, 9.4f});
                row.setWidthPercentage(100);
                row.setSpacingBefore(size * 0.4f);
                PdfPCell letter = plain();
                letter.addElement(new Paragraph(letter(i++) + ")", font(BOLD, size, INK)));
                row.addCell(letter);
                PdfPCell body = plain();
                for (Element e : rich(text(part, "text"), size, INK, false)) body.addElement(e);
                row.addCell(body);
                document.add(row);
                int partLines = part.has("size") ? number(part, "size", lines, 0, 40) : lines;
                answerArea(answer, partLines, text(part, "solution"));
            }
        } else {
            answerArea(answer, lines, text(block, "solution"));
        }
    }

    private void choice(JsonNode block) {
        taskHead(block);
        JsonNode options = block.path("options");
        int columns = Math.min(number(block, "columns", 1, 1, 4), Math.max(1, options.size()));
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setSpacingBefore(size * 0.3f);
        for (JsonNode option : options) {
            boolean checked = solution && flag(option, "correct", false);
            Phrase phrase = new Phrase();
            phrase.setLeading(size * 1.45f);
            phrase.add(box(checked));
            phrase.add(new Chunk("  "));
            phrase.add(inline(text(option, "text"), size, INK, false));
            PdfPCell cell = plain();
            cell.setPhrase(phrase);
            cell.setPaddingTop(3);
            cell.setPaddingBottom(3);
            cell.setPaddingRight(8);
            table.addCell(cell);
        }
        table.completeRow();
        document.add(table);
    }

    private void gapText(JsonNode block) {
        taskHead(block);
        String text = text(block, "text");
        if (flag(block, "wordBank", false)) {
            java.util.List<String> words = new ArrayList<>();
            Matcher m = GAP.matcher(text);
            while (m.find()) words.add(answer(m.group(1)));
            words.addAll(lines(text(block, "distractors")));
            Collections.sort(words, String.CASE_INSENSITIVE_ORDER);
            if (!words.isEmpty()) {
                PdfPTable bank = new PdfPTable(1);
                bank.setWidthPercentage(100);
                bank.setSpacingBefore(size * 0.3f);
                PdfPCell cell = new PdfPCell(text(String.join("   ·   ", words), font(NORMAL, size - 1, INK)));
                cell.setBorderColor(LIGHT);
                cell.setBackgroundColor(PAPER);
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                bank.addCell(cell);
                document.add(bank);
            }
        }
        for (Element e : rich(text, size, INK, true)) {
            if (e instanceof Paragraph p) p.setLeading(size * 2.1f);
            document.add(e);
        }
    }

    private void trueFalse(JsonNode block) {
        taskHead(block);
        PdfPTable table = new PdfPTable(new float[]{8, 1.2f, 1.2f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(size * 0.3f);
        table.setHeaderRows(1);
        table.addCell(headCell("Aussage", Element.ALIGN_LEFT));
        table.addCell(headCell(textOr(block, "trueLabel", "richtig"), Element.ALIGN_CENTER));
        table.addCell(headCell(textOr(block, "falseLabel", "falsch"), Element.ALIGN_CENTER));
        for (JsonNode statement : block.path("statements")) {
            boolean correct = flag(statement, "correct", true);
            PdfPCell text = new PdfPCell(inline(text(statement, "text"), size, INK, false));
            rowStyle(text);
            table.addCell(text);
            table.addCell(boxCell(solution && correct));
            table.addCell(boxCell(solution && !correct));
        }
        document.add(table);
    }

    /** Zuordnen: links nummeriert, rechts gemischt mit Buchstaben; Schüler tragen den Buchstaben ein. */
    private void matching(JsonNode block, int index) {
        taskHead(block);
        java.util.List<String[]> pairs = new ArrayList<>();
        for (JsonNode pair : block.path("pairs")) pairs.add(new String[]{text(pair, "left"), text(pair, "right")});
        java.util.List<Integer> order = shuffled(pairs.size(), seed(block, index));
        PdfPTable table = new PdfPTable(new float[]{5, 1.1f, 0.5f, 5});
        table.setWidthPercentage(100);
        table.setSpacingBefore(size * 0.3f);
        for (int i = 0; i < pairs.size(); i++) {
            PdfPCell left = new PdfPCell(numbered((i + 1) + ".", pairs.get(i)[0]));
            rowStyle(left);
            table.addCell(left);
            String letter = String.valueOf((char) ('A' + order.indexOf(i)));
            table.addCell(answerCell(solution ? letter : ""));
            table.addCell(plain());
            PdfPCell right = new PdfPCell(numbered(String.valueOf((char) ('A' + i)), pairs.get(order.get(i))[1]));
            rowStyle(right);
            table.addCell(right);
        }
        document.add(table);
    }

    /** Reihenfolge: Schritte gemischt, Schüler nummerieren sie. */
    private void order(JsonNode block, int index) {
        taskHead(block);
        java.util.List<String> items = new ArrayList<>();
        for (JsonNode item : block.path("items")) items.add(item.isString() ? item.asString() : text(item, "text"));
        java.util.List<Integer> order = shuffled(items.size(), seed(block, index));
        PdfPTable table = new PdfPTable(new float[]{1.1f, 0.4f, 10});
        table.setWidthPercentage(100);
        table.setSpacingBefore(size * 0.3f);
        for (int i = 0; i < items.size(); i++) {
            int original = order.get(i);
            table.addCell(answerCell(solution ? String.valueOf(original + 1) : ""));
            table.addCell(plain());
            PdfPCell text = new PdfPCell(inline(items.get(original), size, INK, false));
            rowStyle(text);
            table.addCell(text);
        }
        document.add(table);
    }

    /** Tabelle; Zellen der Form [[Antwort]] bleiben in der Schülerfassung leer. */
    private void table(JsonNode block) {
        if (WorksheetContent.isTask(block)) taskHead(block);
        JsonNode rows = block.path("rows");
        int columns = 0;
        for (JsonNode row : rows) columns = Math.max(columns, row.size());
        if (columns == 0) return;
        boolean header = flag(block, "header", true);
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setSpacingBefore(size * 0.4f);
        table.setSpacingAfter(size * 0.4f);
        if (header && rows.size() > 1) table.setHeaderRows(1);
        int r = 0;
        for (JsonNode row : rows) {
            boolean head = header && r++ == 0;
            for (int c = 0; c < columns; c++) {
                String value = row.size() > c ? row.get(c).asString("") : "";
                PdfPCell cell;
                Matcher full = FULL_GAP.matcher(value);
                if (head) {
                    cell = new PdfPCell(inline(value, size - 1, Color.WHITE, false));
                    cell.setBackgroundColor(INK);
                } else if (full.matches()) {
                    cell = new PdfPCell(solution ? text(answer(full.group(1)), font(BOLD, size, RED)) : new Phrase(""));
                    if (solution) cell.setBackgroundColor(RED_BG);
                } else {
                    cell = new PdfPCell(inline(value, size, INK, true));
                }
                cell.setBorderColor(LINE);
                cell.setBorderWidth(0.5f);
                cell.setPadding(5);
                cell.setMinimumHeight(lineHeight);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                table.addCell(cell);
            }
        }
        document.add(table);
        if (WorksheetContent.isTask(block) && !text(block, "solution").isBlank() && solution) solutionBox(text(block, "solution"), 0);
    }

    private void code(JsonNode block) {
        String code = text(block, "code").replace("\t", "    ").replace("\r", "");
        boolean lineNumbers = flag(block, "lineNumbers", false);
        boolean gaps = flag(block, "gaps", false);
        float codeSize = size - 1.5f;
        Phrase phrase = new Phrase();
        phrase.setLeading(codeSize * (gaps ? 1.9f : 1.35f));
        String[] lines = code.split("\n", -1);
        int digits = String.valueOf(lines.length).length();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) phrase.add(Chunk.NEWLINE);
            if (lineNumbers) {
                phrase.add(new Chunk(String.format("%" + digits + "d  ", i + 1).replace(' ', '\u00A0'), mono(codeSize, LINE)));
            }
            String line = clean(lines[i]);
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            line = "\u00A0".repeat(indent) + line.substring(indent);
            if (!gaps) {
                phrase.add(text(line, mono(codeSize, INK)));
                continue;
            }
            Matcher m = GAP.matcher(line);
            int last = 0;
            while (m.find()) {
                phrase.add(text(line.substring(last, m.start()), mono(codeSize, INK)));
                phrase.add(gapChunk(m.group(1), mono(codeSize, INK), mono(codeSize, RED)));
                last = m.end();
            }
            phrase.add(text(line.substring(last), mono(codeSize, INK)));
        }
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(size * 0.4f);
        table.setSpacingAfter(size * 0.4f);
        String caption = text(block, "caption").strip();
        if (!caption.isEmpty()) {
            PdfPCell head = new PdfPCell(text(caption, font(BOLD, size - 2, MUTED)));
            head.setBorderColor(LIGHT);
            head.setBackgroundColor(PAPER);
            head.setPadding(5);
            table.addCell(head);
        }
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorderColor(LIGHT);
        cell.setBackgroundColor(CODE_BG);
        cell.setPadding(8);
        cell.setPaddingTop(5);
        table.addCell(cell);
        document.add(table);
    }

    private void image(JsonNode block) {
        String key = text(block, "key");
        Image img = key.isEmpty() ? null : image(key);
        if (img == null) {
            Paragraph p = new Paragraph(key.isEmpty() ? "[Kein Bild gewählt]" : "[Bild nicht verfügbar]", font(ITALIC, size - 1, MUTED));
            p.setAlignment(Element.ALIGN_CENTER);
            document.add(p);
            return;
        }
        float width = (document.right() - document.left()) * number(block, "width", 60, 10, 100) / 100f;
        float maxHeight = (document.top() - document.bottom()) * 0.75f;
        img.scaleToFit(width, maxHeight);
        img.setAlignment(switch (text(block, "align")) {
            case "left" -> Image.ALIGN_LEFT;
            case "right" -> Image.ALIGN_RIGHT;
            default -> Image.ALIGN_CENTER;
        });
        img.setSpacingBefore(size * 0.4f);
        img.setSpacingAfter(size * 0.3f);
        document.add(img);
        String caption = text(block, "caption").strip();
        if (!caption.isEmpty()) {
            Paragraph p = PdfFonts.paragraph(caption, font(ITALIC, size - 2, MUTED));
            p.setAlignment(switch (text(block, "align")) {
                case "left" -> Element.ALIGN_LEFT;
                case "right" -> Element.ALIGN_RIGHT;
                default -> Element.ALIGN_CENTER;
            });
            p.setSpacingAfter(size * 0.4f);
            document.add(p);
        }
    }

    private void space(int lines) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell cell = plain();
        cell.setFixedHeight(lineHeight * lines);
        table.addCell(cell);
        document.add(table);
    }

    // ---------------------------------------------------------------- Bausteine für Aufgaben

    /** „Aufgabe 3“ mit Arbeitsauftrag links und Punkten rechts, dazu ein optionaler Hinweis. */
    private void taskHead(JsonNode block) {
        double points = Math.max(0, block.path("points").asDouble(0));
        boolean withPoints = showPoints && points > 0;
        PdfPTable table = new PdfPTable(withPoints ? new float[]{8.4f, 1.6f} : new float[]{1});
        table.setWidthPercentage(100);
        table.setSpacingBefore(size * 1.1f);
        PdfPCell left = plain();
        java.util.List<Element> prompt = rich(text(block, "prompt"), size, INK, false);
        if (numbering) {
            taskNumber++;
            Phrase label = text(numberLabel + " " + taskNumber + "   ", font(BOLD, size, accent));
            if (!prompt.isEmpty() && prompt.getFirst() instanceof Paragraph first) {
                first.add(0, label);
            } else {
                prompt.addFirst(new Paragraph(label));
            }
        }
        for (Element e : prompt) left.addElement(e);
        String hint = text(block, "hint").strip();
        if (!hint.isEmpty()) {
            Paragraph p = PdfFonts.paragraph(hint, font(ITALIC, size - 1.5f, MUTED));
            p.setSpacingBefore(1);
            left.addElement(p);
        }
        table.addCell(left);
        if (withPoints) {
            PdfPCell right = plain();
            Paragraph p = new Paragraph(solution ? points(points) + " P." : "____ / " + points(points) + " P.", font(NORMAL, size - 2, MUTED));
            p.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(p);
            table.addCell(right);
        }
        document.add(table);
    }

    /** Schreibplatz unter einer Aufgabe; in der Lösung steht dort die Musterlösung. */
    private void answerArea(String kind, int lines, String solutionText) {
        if (solution && !solutionText.isBlank()) {
            solutionBox(solutionText, "none".equals(kind) ? 0 : lineHeight * lines);
            return;
        }
        if ("none".equals(kind) || lines <= 0) return;
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(size * 0.2f);
        switch (kind) {
            case "box", "grid" -> {
                PdfPCell cell = plain();
                cell.setBorder(Rectangle.BOX);
                cell.setBorderColor(LINE);
                cell.setBorderWidth(0.6f);
                float height = lineHeight * lines;
                if ("grid".equals(kind)) {
                    height = Math.max(5 * MM, Math.round(height / (5 * MM)) * 5 * MM);
                    cell.setCellEvent(new Grid());
                }
                cell.setFixedHeight(height);
                table.addCell(cell);
            }
            default -> {
                for (int i = 0; i < lines; i++) {
                    PdfPCell cell = plain();
                    cell.setBorder(Rectangle.BOTTOM);
                    cell.setBorderColor(LINE);
                    cell.setBorderWidth(0.5f);
                    cell.setFixedHeight(lineHeight);
                    table.addCell(cell);
                }
            }
        }
        document.add(table);
    }

    private void solutionBox(String text, float minHeight) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(size * 0.2f);
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(new Color(253, 164, 175));
        cell.setBorderWidth(0.6f);
        cell.setBackgroundColor(RED_BG);
        cell.setPadding(6);
        cell.setPaddingTop(2);
        if (minHeight > 0) cell.setMinimumHeight(minHeight);
        for (Element e : rich(text, size, RED, false)) cell.addElement(e);
        table.addCell(cell);
        document.add(table);
    }

    /** Kästchenpapier (5 mm) als Hintergrund einer Zelle. */
    private static final class Grid implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle r, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.BACKGROUNDCANVAS];
            float step = 5 * MM;
            cb.saveState();
            cb.setColorStroke(LIGHT);
            cb.setLineWidth(0.3f);
            for (float x = r.getLeft() + step; x < r.getRight() - 0.5f; x += step) {
                cb.moveTo(x, r.getBottom());
                cb.lineTo(x, r.getTop());
            }
            for (float y = r.getTop() - step; y > r.getBottom() + 0.5f; y -= step) {
                cb.moveTo(r.getLeft(), y);
                cb.lineTo(r.getRight(), y);
            }
            cb.stroke();
            cb.restoreState();
        }
    }

    private PdfPCell headCell(String text, int align) {
        PdfPCell cell = new PdfPCell(text(text, font(BOLD, size - 2, MUTED)));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        cell.setHorizontalAlignment(align);
        cell.setPaddingBottom(4);
        return cell;
    }

    private void rowStyle(PdfPCell cell) {
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LIGHT);
        cell.setPadding(5);
        cell.setMinimumHeight(lineHeight * 0.9f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
    }

    private PdfPCell boxCell(boolean checked) {
        PdfPCell cell = new PdfPCell(new Phrase(box(checked)));
        rowStyle(cell);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    /** Kästchen zum Eintragen eines Buchstabens oder einer Zahl. */
    private PdfPCell answerCell(String value) {
        PdfPCell outer = plain();
        outer.setPadding(3);
        outer.setVerticalAlignment(Element.ALIGN_MIDDLE);
        PdfPTable inner = new PdfPTable(1);
        inner.setWidthPercentage(100);
        PdfPCell box = new PdfPCell(text(value, font(BOLD, size, RED)));
        box.setBorderColor(LINE);
        box.setBorderWidth(0.7f);
        box.setFixedHeight(Math.max(size * 1.9f, lineHeight * 0.8f));
        box.setHorizontalAlignment(Element.ALIGN_CENTER);
        box.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (!value.isEmpty()) box.setBackgroundColor(RED_BG);
        inner.addCell(box);
        outer.addElement(inner);
        return outer;
    }

    private Phrase numbered(String label, String text) {
        Phrase phrase = new Phrase();
        phrase.add(text(label + "  ", font(BOLD, size, accent)));
        phrase.add(inline(text, size, INK, false));
        return phrase;
    }

    private Chunk box(boolean checked) {
        try {
            return new Chunk(Image.getInstance(checked ? boxChecked : boxEmpty), 0, -1.5f, false);
        } catch (Exception e) {
            return new Chunk(checked ? "[x]" : "[  ]", font(NORMAL, size, checked ? RED : INK));
        }
    }

    private PdfTemplate checkbox(boolean checked) {
        float s = size * 0.95f;
        PdfTemplate tpl = writer.getDirectContent().createTemplate(s, s);
        tpl.setColorStroke(INK);
        tpl.setLineWidth(0.7f);
        tpl.rectangle(0.5f, 0.5f, s - 1, s - 1);
        tpl.stroke();
        if (checked) {
            tpl.setColorStroke(RED);
            tpl.setLineWidth(1.5f);
            tpl.moveTo(s * 0.18f, s * 0.52f);
            tpl.lineTo(s * 0.42f, s * 0.22f);
            tpl.lineTo(s * 0.86f, s * 0.88f);
            tpl.stroke();
        }
        return tpl;
    }

    private Phrase gapChunk(String raw, Font normal, Font red) {
        String answer = answer(raw);
        if (solution) {
            return styled("\u00A0" + answer + "\u00A0", red,
                    c -> c.setUnderline(RED, 0.6f, 0, -2.5f, 0, PdfContentByte.LINE_CAP_BUTT));
        }
        int width = Math.max(8, Math.round(answer.length() * 1.7f) + 2);
        Chunk chunk = new Chunk("\u00A0".repeat(width), normal);
        chunk.setUnderline(INK, 0.6f, 0, -2.5f, 0, PdfContentByte.LINE_CAP_BUTT);
        return new Phrase(chunk);
    }

    // ---------------------------------------------------------------- Text mit einfacher Formatierung

    /**
     * Absätze, Aufzählungen (- …) und nummerierte Listen (1. …); innerhalb einer Zeile **fett**,
     * *kursiv*, `Code` und – nur bei Lückentexten – [[Lücken]].
     */
    private java.util.List<Element> rich(String text, float fontSize, Color color, boolean gaps) {
        java.util.List<Element> out = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        List list = null;
        boolean listNumbered = false;
        for (String raw : text.replace("\r", "").split("\n", -1)) {
            String line = raw.stripTrailing();
            Matcher bullet = BULLET.matcher(line);
            Matcher number = NUMBERED.matcher(line);
            boolean isBullet = bullet.matches();
            boolean isNumber = !isBullet && number.matches();
            if (isBullet || isNumber) {
                flushParagraph(out, paragraph, fontSize, color, gaps);
                if (list == null || listNumbered != isNumber) {
                    if (list != null) out.add(list);
                    list = new List(isNumber, fontSize * 1.4f);
                    if (!isNumber) list.setListSymbol(new Chunk("•", font(NORMAL, fontSize, color)));
                    else list.setPreSymbol("");
                    list.setIndentationLeft(fontSize * 0.4f);
                    listNumbered = isNumber;
                }
                ListItem item = new ListItem(inline((isBullet ? bullet : number).group(1), fontSize, color, gaps));
                item.setLeading(fontSize * (gaps ? 2.1f : 1.4f));
                list.add(item);
            } else if (line.isBlank()) {
                flushParagraph(out, paragraph, fontSize, color, gaps);
                if (list != null) {
                    out.add(list);
                    list = null;
                }
            } else {
                if (list != null) {
                    out.add(list);
                    list = null;
                }
                if (!paragraph.isEmpty()) paragraph.append('\n');
                paragraph.append(line);
            }
        }
        flushParagraph(out, paragraph, fontSize, color, gaps);
        if (list != null) out.add(list);
        return out;
    }

    private void flushParagraph(java.util.List<Element> out, StringBuilder text, float fontSize, Color color, boolean gaps) {
        if (text.isEmpty()) return;
        Paragraph p = new Paragraph(inline(text.toString(), fontSize, color, gaps));
        p.setLeading(fontSize * 1.4f);
        p.setSpacingAfter(fontSize * 0.35f);
        out.add(p);
        text.setLength(0);
    }

    private Phrase inline(String text, float fontSize, Color color, boolean gaps) {
        Phrase phrase = new Phrase();
        phrase.setLeading(fontSize * 1.4f);
        String value = clean(text);
        Matcher m = INLINE.matcher(value);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) phrase.add(text(value.substring(last, m.start()), font(NORMAL, fontSize, color)));
            if (m.group(1) != null) {
                phrase.add(text(m.group(1), font(BOLD, fontSize, color)));
            } else if (m.group(2) != null) {
                phrase.add(text(m.group(2), font(ITALIC, fontSize, color)));
            } else if (m.group(3) != null) {
                phrase.add(styled(m.group(3), mono(fontSize - 0.5f, color), c -> c.setBackground(PAPER)));
            } else if (gaps) {
                phrase.add(gapChunk(m.group(4), font(NORMAL, fontSize, color), font(BOLD, fontSize, RED)));
            } else {
                phrase.add(text(m.group(), font(NORMAL, fontSize, color)));
            }
            last = m.end();
        }
        if (last < value.length()) phrase.add(text(value.substring(last), font(NORMAL, fontSize, color)));
        return phrase;
    }

    // ---------------------------------------------------------------- Hilfsfunktionen

    private Image image(String key) {
        byte[] data = images.apply(key);
        if (data == null) return null;
        try {
            return Image.getInstance(data);
        } catch (Exception e) {
            // z. B. WebP: über ImageIO versuchen, sonst Platzhalter
            try {
                BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(data));
                return buffered == null ? null : Image.getInstance(buffered, null);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private Font font(int style, float fontSize, Color color) {
        return PdfFonts.font(serif ? PdfFonts.Family.SERIF : PdfFonts.Family.SANS, style, fontSize, color);
    }

    private static Font mono(float fontSize, Color color) {
        return PdfFonts.mono(fontSize, color);
    }

    /** Text mit Ausweichschriften für Zeichen, die die Hauptschrift nicht kennt. */
    private static Phrase text(String value, Font font) {
        return PdfFonts.phrase(value, font);
    }

    /** Wie {@link #text}, jedes Stück mit derselben Auszeichnung (Unterstrich, Hintergrund). */
    private static Phrase styled(String value, Font font, java.util.function.Consumer<Chunk> style) {
        Phrase phrase = PdfFonts.phrase(value, font);
        for (Element e : phrase) if (e instanceof Chunk c) style.accept(c);
        return phrase;
    }

    private static PdfPCell plain() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    /** Gemischte Reihenfolge, fest je Block (gleiches Blatt = gleiches PDF), nie unverändert. */
    static java.util.List<Integer> shuffled(int n, long seed) {
        java.util.List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        if (n < 2) return order;
        Collections.shuffle(order, new Random(seed));
        boolean unchanged = true;
        for (int i = 0; i < n; i++) unchanged &= order.get(i) == i;
        if (unchanged) Collections.rotate(order, 1);
        return order;
    }

    private static long seed(JsonNode block, int index) {
        String id = text(block, "id");
        return id.isEmpty() ? index * 7919L : id.hashCode();
    }

    private static String answer(String raw) {
        int bar = raw.indexOf('|');
        return (bar >= 0 ? raw.substring(0, bar) : raw).strip();
    }

    private static java.util.List<String> lines(String text) {
        return text.lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static String letter(int i) {
        return String.valueOf((char) ('a' + Math.min(i, 25)));
    }

    static String points(double value) {
        if (value == Math.rint(value)) return String.valueOf((long) value);
        return String.format(Locale.GERMAN, "%.1f", value);
    }

    private static Color color(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) return DEFAULT_ACCENT;
        return new Color(Integer.parseInt(hex.substring(1), 16));
    }

    /** Sehr helle Fassung einer Farbe für Hintergründe. */
    private static Color tint(Color c) {
        return new Color(255 - (255 - c.getRed()) / 10, 255 - (255 - c.getGreen()) / 10, 255 - (255 - c.getBlue()) / 10);
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.isContainer() ? "" : value.asString("");
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        String value = text(node, field).strip();
        return value.isEmpty() ? fallback : value;
    }

    static int number(JsonNode node, String field, int fallback, int min, int max) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) return fallback;
        return Math.clamp(value.asInt(), min, max);
    }

    static boolean flag(JsonNode node, String field, boolean fallback) {
        JsonNode value = node.get(field);
        return value == null || !value.isBoolean() ? fallback : value.asBoolean();
    }

    static String clean(String text) {
        return PdfFonts.clean(text);
    }
}

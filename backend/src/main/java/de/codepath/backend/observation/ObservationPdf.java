package de.codepath.backend.observation;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import de.codepath.backend.platform.pdf.PdfFonts;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/** Beobachtungsbogen als PDF (A4 quer): Sitzplan, Tabelle, Legende der Auswahllisten, optional eine Seite je Person. */
final class ObservationPdf {

    private static final Color NAVY = new Color(15, 23, 42);
    private static final Color GOLD = new Color(180, 83, 9);
    private static final Color BORDER = new Color(203, 213, 225);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color TABLE_FILL = new Color(241, 245, 249);
    private static final Color SEAT_FILL = new Color(254, 243, 199);
    private static final Color SEAT_EMPTY = Color.WHITE;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    /** Höhe, die der Sitzplan auf der ersten Seite höchstens einnimmt */
    private static final float PLAN_HEIGHT = 300;

    private ObservationPdf() {
    }

    static byte[] render(ObservationService.BoardView view, List<ObservationService.PersonView> people,
                         Map<String, String> seatLabels, boolean perPerson, LocalDate date) {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.addTitle("Beobachtungsbogen " + view.courseName());
        document.open();
        heading(document, view.courseName(), date);

        Map<String, ObservationService.PersonView> bySeat = new HashMap<>();
        for (ObservationService.PersonView p : people) if (p.seatId() != null) bySeat.put(p.seatId(), p);
        if (!view.layout().items().isEmpty()) {
            Image plan = plan(writer.getDirectContent(), view.layout(), bySeat, document.right() - document.left());
            plan.setSpacingAfter(12);
            document.add(plan);
        }

        document.add(table(view, people, seatLabels));
        legend(document, view.criteria());

        if (perPerson) {
            for (ObservationService.PersonView p : people) {
                document.newPage();
                person(document, view, p, seatLabels, date);
            }
        }
        document.close();
        return out.toByteArray();
    }

    private static void heading(Document document, String courseName, LocalDate date) {
        Paragraph title = PdfFonts.paragraph("BEOBACHTUNGSBOGEN", PdfFonts.font(PdfFonts.BOLD, 18, NAVY));
        title.setSpacingAfter(2);
        document.add(title);
        Paragraph sub = PdfFonts.paragraph((courseName == null ? "" : courseName.toUpperCase(Locale.GERMAN)) + " · Stand " + date.format(DATE),
                PdfFonts.font(PdfFonts.BOLD, 9, GOLD));
        sub.setSpacingAfter(10);
        document.add(sub);
    }

    // ------------------------------------------------------------------ Sitzplan

    /** Zeichnet den Raum in eine Vorlage, zugeschnitten auf die belegte Fläche. */
    static Image plan(PdfContentByte content, RoomLayout layout, Map<String, ObservationService.PersonView> bySeat, float maxWidth) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (RoomLayout.RoomItem item : layout.items()) {
            for (double[] p : outline(item)) {
                minX = Math.min(minX, p[0]);
                minY = Math.min(minY, p[1]);
                maxX = Math.max(maxX, p[0]);
                maxY = Math.max(maxY, p[1]);
            }
            for (RoomLayout.RoomSeat seat : item.seats()) {
                for (double[] p : seatOutline(item, seat)) {
                    minX = Math.min(minX, p[0]);
                    minY = Math.min(minY, p[1]);
                    maxX = Math.max(maxX, p[0]);
                    maxY = Math.max(maxY, p[1]);
                }
            }
        }
        minX -= 10;
        minY -= 10;
        maxX += 10;
        maxY += 10;
        float scale = (float) Math.min(maxWidth / (maxX - minX), PLAN_HEIGHT / (maxY - minY));
        scale = Math.min(scale, 1f);
        float width = (float) (maxX - minX) * scale, height = (float) (maxY - minY) * scale;
        PdfTemplate t = content.createTemplate(width, height);
        Transform tf = new Transform(minX, minY, scale, height);

        for (RoomLayout.RoomItem item : layout.items()) {
            Color fill = switch (item.kind()) {
                case TABLE -> TABLE_FILL;
                case TEACHER -> new Color(226, 232, 240);
                case BOARD -> new Color(30, 41, 59);
            };
            polygon(t, tf, outline(item), fill, NAVY);
            String label = item.label() != null ? item.label() : switch (item.kind()) {
                case TABLE -> "Tisch " + (layout.tablesInOrder().indexOf(item) + 1);
                case TEACHER -> "Lehrkraft";
                case BOARD -> "Tafel";
            };
            if (label != null) {
                double[] c = tf.map(item.x() + item.width() / 2, item.y() + item.height() / 2);
                text(t, label, (float) c[0], (float) c[1], 7, item.kind() == RoomLayout.Kind.BOARD ? Color.WHITE : MUTED, angle(item));
            }
            for (RoomLayout.RoomSeat seat : item.seats()) {
                ObservationService.PersonView person = bySeat.get(seat.id());
                polygon(t, tf, seatOutline(item, seat), person == null ? SEAT_EMPTY : SEAT_FILL, BORDER);
                if (person != null) {
                    RoomLayout.Point center = RoomLayout.center(item, seat);
                    double[] c = tf.map(center.x(), center.y());
                    text(t, shortName(person.name()), (float) c[0], (float) c[1], 7, NAVY, angle(item));
                }
            }
        }
        return Image.getInstance(t);
    }

    /** Raumkoordinaten (y nach unten) → Vorlage (y nach oben) */
    private record Transform(double minX, double minY, float scale, float height) {
        double[] map(double x, double y) {
            return new double[]{(x - minX) * scale, height - (y - minY) * scale};
        }
    }

    private static List<double[]> outline(RoomLayout.RoomItem item) {
        return rotated(item, new double[][]{{0, 0}, {item.width(), 0}, {item.width(), item.height()}, {0, item.height()}});
    }

    private static List<double[]> seatOutline(RoomLayout.RoomItem item, RoomLayout.RoomSeat seat) {
        RoomLayout.Point c = RoomLayout.localCenter(item, seat);
        double w = RoomLayout.SEAT_WIDTH / 2.0, h = RoomLayout.SEAT_HEIGHT / 2.0;
        return rotated(item, new double[][]{{c.x() - w, c.y() - h}, {c.x() + w, c.y() - h}, {c.x() + w, c.y() + h}, {c.x() - w, c.y() + h}});
    }

    private static List<double[]> rotated(RoomLayout.RoomItem item, double[][] local) {
        double cx = item.width() / 2, cy = item.height() / 2;
        double rad = Math.toRadians(item.rotation());
        List<double[]> result = new ArrayList<>();
        for (double[] p : local) {
            double dx = p[0] - cx, dy = p[1] - cy;
            result.add(new double[]{item.x() + cx + dx * Math.cos(rad) - dy * Math.sin(rad), item.y() + cy + dx * Math.sin(rad) + dy * Math.cos(rad)});
        }
        return result;
    }

    private static void polygon(PdfTemplate t, Transform tf, List<double[]> points, Color fill, Color stroke) {
        t.saveState();
        t.setColorFill(fill);
        t.setColorStroke(stroke);
        t.setLineWidth(0.6f);
        for (int i = 0; i < points.size(); i++) {
            double[] p = tf.map(points.get(i)[0], points.get(i)[1]);
            if (i == 0) t.moveTo((float) p[0], (float) p[1]);
            else t.lineTo((float) p[0], (float) p[1]);
        }
        t.closePathFillStroke();
        t.restoreState();
    }

    /**
     * Schriftwinkel wie auf dem Bildschirm: mit dem Tisch gedreht, aber nie auf dem Kopf. Im PDF zeigt y nach oben,
     * deshalb gegen den Uhrzeigersinn.
     */
    static float angle(RoomLayout.RoomItem item) {
        int r = Math.floorMod(item.rotation(), 360);
        int readable = r > 90 && r <= 270 ? r - 180 : r;
        return -readable;
    }

    private static void text(PdfTemplate t, String text, float x, float y, float size, Color color, float angle) {
        // Grundlinie um ein Drittel der Schriftgröße senkrecht zur Schreibrichtung versetzen, damit der Text mittig sitzt
        double rad = Math.toRadians(angle);
        float dx = (float) (Math.sin(rad) * size / 3), dy = (float) (-Math.cos(rad) * size / 3);
        ColumnText.showTextAligned(t, Element.ALIGN_CENTER, PdfFonts.phrase(text, PdfFonts.font(PdfFonts.NORMAL, size, color)),
                x + dx, y + dy, angle);
    }

    static String shortName(String name) {
        if (name == null) return "";
        String n = name.strip();
        return n.length() > 14 ? n.substring(0, 13) + "…" : n;
    }

    // ------------------------------------------------------------------ Tabelle

    private static PdfPTable table(ObservationService.BoardView view, List<ObservationService.PersonView> people,
                                   Map<String, String> seatLabels) {
        int n = view.criteria().size();
        float[] widths = new float[3 + n];
        widths[0] = 3;
        widths[1] = 2;
        for (int i = 0; i < n; i++) widths[2 + i] = 1.4f;
        widths[2 + n] = Math.max(3, 8 - n);
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        header(table, "Name");
        header(table, "Platz");
        for (ObservationService.CriterionView c : view.criteria()) header(table, c.name());
        header(table, "Notizen");
        for (ObservationService.PersonView p : people) {
            table.addCell(cell(p.name() + (p.username() == null ? "" : "\n@" + p.username()), true));
            table.addCell(cell(p.seatId() == null ? "–" : seatLabels.getOrDefault(p.seatId(), "–"), false));
            for (ObservationService.CriterionView c : view.criteria()) {
                String value = p.ratings().get(c.id());
                table.addCell(cell(value == null ? "" : value, false));
            }
            table.addCell(cell(p.note() == null ? "" : p.note(), false));
        }
        if (people.isEmpty()) {
            PdfPCell empty = cell("Noch keine Personen im Bogen.", false);
            empty.setColspan(3 + n);
            table.addCell(empty);
        }
        return table;
    }

    private static void legend(Document document, List<ObservationService.CriterionView> criteria) {
        boolean first = true;
        for (ObservationService.CriterionView c : criteria) {
            String text = describe(c);
            if (text == null) continue;
            if (first) {
                Paragraph title = PdfFonts.paragraph("KRITERIEN", PdfFonts.font(PdfFonts.BOLD, 9, NAVY));
                title.setSpacingBefore(12);
                document.add(title);
                first = false;
            }
            document.add(PdfFonts.paragraph(c.name() + ": " + text, PdfFonts.font(PdfFonts.NORMAL, 8, MUTED)));
        }
    }

    /** „1 = Neugier …, 2 = …“ bzw. der Bereich der Zahl; null, wenn es nichts zu erklären gibt. */
    static String describe(ObservationService.CriterionView c) {
        if (c.kind() == ObservationCriterion.Kind.CHOICE) {
            StringJoiner joiner = new StringJoiner(" · ");
            for (ObservationService.ObservationOption o : c.options()) {
                joiner.add(o.description() == null ? o.value() : o.value() + " = " + o.description());
            }
            return joiner.toString();
        }
        if (c.min() == null && c.max() == null) return null;
        if (c.min() != null && c.max() != null) {
            return "Zahl von " + ObservationService.plain(c.min()) + " bis " + ObservationService.plain(c.max());
        }
        return c.min() != null ? "Zahl ab " + ObservationService.plain(c.min()) : "Zahl bis " + ObservationService.plain(c.max());
    }

    // ------------------------------------------------------------------ Seite je Person

    private static void person(Document document, ObservationService.BoardView view, ObservationService.PersonView p,
                               Map<String, String> seatLabels, LocalDate date) {
        heading(document, view.courseName(), date);
        Paragraph name = PdfFonts.paragraph(p.name() + (p.username() == null ? "" : " (@" + p.username() + ")"),
                PdfFonts.font(PdfFonts.BOLD, 14, NAVY));
        name.setSpacingAfter(4);
        document.add(name);
        document.add(PdfFonts.paragraph("Platz: " + (p.seatId() == null ? "–" : seatLabels.getOrDefault(p.seatId(), "–")),
                PdfFonts.font(PdfFonts.NORMAL, 10, MUTED)));

        PdfPTable table = new PdfPTable(new float[]{3, 2, 6});
        table.setWidthPercentage(100);
        table.setSpacingBefore(12);
        table.setHeaderRows(1);
        header(table, "Kriterium");
        header(table, "Wert");
        header(table, "Bedeutung");
        for (ObservationService.CriterionView c : view.criteria()) {
            String value = p.ratings().get(c.id());
            table.addCell(cell(c.name(), true));
            table.addCell(cell(value == null ? "–" : value, false));
            String meaning = "";
            if (value != null && c.kind() == ObservationCriterion.Kind.CHOICE) {
                meaning = c.options().stream().filter(o -> o.value().equals(value)).map(ObservationService.ObservationOption::description)
                        .filter(Objects::nonNull).findFirst().orElse("");
            } else if (c.kind() == ObservationCriterion.Kind.NUMBER && (c.min() != null || c.max() != null)) {
                meaning = describe(c);
            }
            table.addCell(cell(meaning, false));
        }
        if (view.criteria().isEmpty()) {
            PdfPCell empty = cell("Noch keine Kriterien angelegt.", false);
            empty.setColspan(3);
            table.addCell(empty);
        }
        document.add(table);

        Paragraph notes = PdfFonts.paragraph("NOTIZEN", PdfFonts.font(PdfFonts.BOLD, 9, NAVY));
        notes.setSpacingBefore(14);
        notes.setSpacingAfter(4);
        document.add(notes);
        document.add(PdfFonts.paragraph(p.note() == null ? "–" : p.note(), PdfFonts.font(PdfFonts.NORMAL, 10, NAVY)));
    }

    private static void header(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(PdfFonts.phrase(text.toUpperCase(Locale.GERMAN), PdfFonts.font(PdfFonts.BOLD, 7, Color.WHITE)));
        cell.setBackgroundColor(NAVY);
        cell.setPadding(5);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private static PdfPCell cell(String text, boolean bold) {
        PdfPCell cell = new PdfPCell(PdfFonts.phrase(text, PdfFonts.font(bold ? PdfFonts.BOLD : PdfFonts.NORMAL, 8, NAVY)));
        cell.setPadding(4);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER);
        return cell;
    }
}

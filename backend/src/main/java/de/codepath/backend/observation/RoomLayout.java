package de.codepath.backend.observation;

import de.codepath.backend.platform.ApiException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Der Klassenraum als Fläche von {@link #WIDTH} × {@link #HEIGHT} Einheiten: Tische, Lehrerpult und Tafel
 * mit Position (linke obere Ecke vor der Drehung), Größe, Drehung um die Mitte und Sitzplätzen an den Seiten.
 * Die Geometrie der Plätze steht gleich in {@code frontend/src/lib/observation/room.ts}.
 */
public record RoomLayout(List<RoomItem> items) {

    public static final int WIDTH = 1200;
    public static final int HEIGHT = 800;
    public static final int SEAT_WIDTH = 76;
    public static final int SEAT_HEIGHT = 28;
    /** Abstand zwischen Tischkante und Platz */
    public static final int SEAT_GAP = 4;

    static final int MAX_ITEMS = 150;
    static final int MAX_SEATS_PER_SIDE = 8;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");

    public enum Kind { TABLE, TEACHER, BOARD }

    public enum Side { TOP, BOTTOM, LEFT, RIGHT }

    /**
     * @param rotation Grad im Uhrzeigersinn um die Mitte, 0–359
     * @param seats    Plätze in Reihenfolge; je Seite gleichmäßig verteilt
     */
    public record RoomItem(String id, Kind kind, String label, double x, double y, double width, double height, int rotation,
                       List<RoomSeat> seats) {
    }

    public record RoomSeat(String id, Side side) {
    }

    /** Mittelpunkt eines Platzes auf der Fläche */
    public record Point(double x, double y) {
    }

    public static RoomLayout empty() {
        return new RoomLayout(List.of());
    }

    /** Prüft die Form und gibt eine bereinigte Fassung zurück (Werte begrenzt, Drehung 0–359). */
    public static RoomLayout validate(RoomLayout layout) {
        if (layout == null || layout.items() == null) return empty();
        if (layout.items().size() > MAX_ITEMS) throw ApiException.badRequest("Höchstens " + MAX_ITEMS + " Elemente im Raum.");
        Set<String> itemIds = new HashSet<>();
        Set<String> seatIds = new HashSet<>();
        List<RoomItem> items = new ArrayList<>();
        for (RoomItem item : layout.items()) {
            if (item == null) continue;
            if (item.id() == null || !ID.matcher(item.id()).matches() || !itemIds.add(item.id())) {
                throw ApiException.badRequest("Jedes Element braucht eine eindeutige ID.");
            }
            Kind kind = item.kind() == null ? Kind.TABLE : item.kind();
            String label = item.label() == null || item.label().isBlank() ? null : item.label().strip();
            if (label != null && label.length() > 40) throw ApiException.badRequest("Bezeichnung zu lang (höchstens 40 Zeichen).");
            double width = clamp(item.width(), 20, 800);
            double height = clamp(item.height(), 10, 600);
            List<RoomSeat> seats = new ArrayList<>();
            if (kind == Kind.TABLE && item.seats() != null) {
                Map<Side, Integer> perSide = new EnumMap<>(Side.class);
                for (RoomSeat seat : item.seats()) {
                    if (seat == null) continue;
                    if (seat.id() == null || !ID.matcher(seat.id()).matches() || !seatIds.add(seat.id())) {
                        throw ApiException.badRequest("Jeder Sitzplatz braucht eine eindeutige ID.");
                    }
                    Side side = seat.side() == null ? Side.BOTTOM : seat.side();
                    if (perSide.merge(side, 1, Integer::sum) > MAX_SEATS_PER_SIDE) {
                        throw ApiException.badRequest("Höchstens " + MAX_SEATS_PER_SIDE + " Plätze je Tischseite.");
                    }
                    seats.add(new RoomSeat(seat.id(), side));
                }
            }
            items.add(new RoomItem(item.id(), kind, label, clamp(item.x(), -width / 2, WIDTH - width / 2),
                    clamp(item.y(), -height / 2, HEIGHT - height / 2), width, height, Math.floorMod(item.rotation(), 360),
                    List.copyOf(seats)));
        }
        return new RoomLayout(List.copyOf(items));
    }

    /** Alle Sitzplätze in Sitzplan-Reihenfolge: Tische von vorne links nach hinten rechts, darin die Plätze. */
    public List<SeatRef> seatsInOrder() {
        List<SeatRef> result = new ArrayList<>();
        int number = 0;
        for (RoomItem item : tablesInOrder()) {
            number++;
            String table = item.label() != null ? item.label() : "Tisch " + number;
            int index = 0;
            for (RoomSeat seat : item.seats()) {
                index++;
                result.add(new SeatRef(seat.id(), item.seats().size() == 1 ? table : table + " · " + index, center(item, seat)));
            }
        }
        return result;
    }

    public record SeatRef(String id, String label, Point center) {
    }

    /** Tische sortiert nach Reihe (y, auf 40 Einheiten gerundet) und darin von links nach rechts. */
    List<RoomItem> tablesInOrder() {
        return items.stream().filter(i -> i.kind() == Kind.TABLE)
                .sorted(Comparator.comparingLong((RoomItem i) -> Math.round((i.y() + i.height() / 2) / 40))
                        .thenComparingDouble(i -> i.x() + i.width() / 2))
                .toList();
    }

    public Set<String> seatIds() {
        Set<String> ids = new HashSet<>();
        for (RoomItem item : items) for (RoomSeat seat : item.seats()) ids.add(seat.id());
        return ids;
    }

    /** Mittelpunkt eines Platzes nach Drehung des Tisches um seine Mitte. */
    public static Point center(RoomItem item, RoomSeat seat) {
        Point local = localCenter(item, seat);
        double cx = item.width() / 2, cy = item.height() / 2;
        double rad = Math.toRadians(item.rotation());
        double dx = local.x() - cx, dy = local.y() - cy;
        return new Point(item.x() + cx + dx * Math.cos(rad) - dy * Math.sin(rad),
                item.y() + cy + dx * Math.sin(rad) + dy * Math.cos(rad));
    }

    /** Mittelpunkt eines Platzes relativ zur linken oberen Ecke des ungedrehten Tisches. */
    public static Point localCenter(RoomItem item, RoomSeat seat) {
        List<RoomSeat> same = item.seats().stream().filter(s -> s.side() == seat.side()).toList();
        int n = same.size();
        int i = same.indexOf(seat);
        double along = (i + 0.5) / n;
        return switch (seat.side()) {
            case TOP -> new Point(item.width() * along, -SEAT_GAP - SEAT_HEIGHT / 2.0);
            case BOTTOM -> new Point(item.width() * along, item.height() + SEAT_GAP + SEAT_HEIGHT / 2.0);
            case LEFT -> new Point(-SEAT_GAP - SEAT_WIDTH / 2.0, item.height() * along);
            case RIGHT -> new Point(item.width() + SEAT_GAP + SEAT_WIDTH / 2.0, item.height() * along);
        };
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.round(Math.max(min, Math.min(max, value)));
    }
}

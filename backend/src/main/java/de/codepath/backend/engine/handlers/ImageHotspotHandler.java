package de.codepath.backend.engine.handlers;

import de.codepath.backend.engine.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Stellen in einem Bild markieren. Config: { image: Medien-Schlüssel, alt, targets: [{id, x, y, w, h, label?}],
 * showCount? (Standard true) } – Rechtecke in Prozent der Bildbreite und -höhe.
 * Antwort: { marks: [{x, y}] } in Prozent. Die Zielbereiche gehen nie an den Browser.
 * Punkte: getroffene Ziele minus Markierungen daneben, geteilt durch die Zahl der Ziele.
 */
@Component
public class ImageHotspotHandler implements TaskHandler {

    static final int MAX_TARGETS = 10;
    /** Markierungen über die Zahl der Ziele hinaus – genug zum Korrigieren, zu wenig zum Flächendecken. */
    static final int EXTRA_MARKS = 2;

    @Override
    public TaskType type() {
        return TaskType.IMAGE_HOTSPOT;
    }

    record Box(String id, double x, double y, double w, double h, String label) {
        boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    static List<Box> targets(Map<String, Object> config) {
        List<Box> boxes = new ArrayList<>();
        for (Map<String, Object> t : Cfg.objects(config, "targets")) {
            Double x = Cfg.number(t, "x"), y = Cfg.number(t, "y"), w = Cfg.number(t, "w"), h = Cfg.number(t, "h");
            if (x == null || y == null || w == null || h == null) continue;
            boxes.add(new Box(Cfg.string(t, "id", ""), x, y, w, h, Cfg.string(t, "label", "")));
        }
        return boxes;
    }

    @Override
    public List<String> validate(Map<String, Object> config, int points) {
        List<Box> boxes = targets(config);
        List<Map<String, Object>> raw = Cfg.objects(config, "targets");
        return new Cfg.Problems()
                .require(MediaKeys.isKey(Cfg.string(config, "image")), "Feld 'image' braucht ein hochgeladenes Bild")
                .requireText(config, "alt")
                .require(!raw.isEmpty() && raw.size() <= MAX_TARGETS, "1 bis " + MAX_TARGETS + " Zielbereiche")
                .require(boxes.size() == raw.size(), "Jeder Zielbereich braucht x, y, w und h")
                .requireUniqueIds(raw, "targets")
                .require(boxes.stream().allMatch(b -> b.x() >= 0 && b.y() >= 0 && b.w() >= 1 && b.h() >= 1
                        && b.x() + b.w() <= 100.001 && b.y() + b.h() <= 100.001), "Zielbereiche müssen im Bild liegen (Prozentwerte 0–100)")
                .require(wrongPoint(boxes) != null, "Die Zielbereiche dürfen nicht das ganze Bild bedecken")
                .list();
    }

    @Override
    public Map<String, Object> publicView(TaskSpec spec, TaskContext context) {
        int count = targets(spec.config()).size();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("image", Cfg.string(spec.config(), "image", ""));
        view.put("alt", Cfg.string(spec.config(), "alt", ""));
        view.put("maxMarks", count + EXTRA_MARKS);
        if (!Boolean.FALSE.equals(spec.config().get("showCount"))) view.put("count", count);
        return view;
    }

    @Override
    public Evaluation evaluate(TaskSpec spec, Map<String, Object> answer, TaskContext context) {
        List<Box> boxes = targets(spec.config());
        List<double[]> marks = new ArrayList<>();
        for (Map<String, Object> m : Cfg.objects(answer, "marks")) {
            Double x = Cfg.number(m, "x"), y = Cfg.number(m, "y");
            if (x == null || y == null || x < 0 || x > 100 || y < 0 || y > 100) return Evaluation.invalid("Ungültige Markierung.");
            marks.add(new double[]{x, y});
        }
        if (marks.isEmpty()) return Evaluation.invalid("Bitte setze mindestens eine Markierung.");
        if (marks.size() > boxes.size() + EXTRA_MARKS) return Evaluation.invalid("Höchstens " + (boxes.size() + EXTRA_MARKS) + " Markierungen.");

        Set<String> hit = new HashSet<>();
        int misses = 0;
        for (double[] mark : marks) {
            Optional<Box> box = boxes.stream().filter(b -> b.contains(mark[0], mark[1])).findFirst();
            if (box.isPresent()) hit.add(box.get().id());
            else misses++;
        }
        double score = (double) (hit.size() - misses) / boxes.size();
        String feedback = hit.size() == boxes.size() && misses == 0 ? "Alles gefunden!"
                : hit.size() + " von " + boxes.size() + " gefunden" + (misses > 0 ? ", " + misses + " Markierung(en) daneben." : ".");
        return Evaluation.scored(score, feedback, Map.of());
    }

    @Override
    public Map<String, Object> solutionView(TaskSpec spec, TaskContext context) {
        return Map.of("targets", targets(spec.config()).stream().map(b -> Map.<String, Object>of(
                "id", b.id(), "x", b.x(), "y", b.y(), "w", b.w(), "h", b.h(), "label", b.label())).toList());
    }

    @Override
    public Map<String, Object> sampleAnswer(TaskSpec spec, TaskContext context) {
        return Map.of("marks", targets(spec.config()).stream()
                .map(b -> Map.<String, Object>of("x", b.x() + b.w() / 2, "y", b.y() + b.h() / 2)).toList());
    }

    @Override
    public Map<String, Object> wrongAnswer(TaskSpec spec, TaskContext context) {
        double[] point = wrongPoint(targets(spec.config()));
        return Map.of("marks", List.of(Map.of("x", point == null ? 50 : point[0], "y", point == null ? 50 : point[1])));
    }

    /** Ein Punkt außerhalb aller Ziele, oder null, wenn das Raster keinen findet. */
    static double[] wrongPoint(List<Box> boxes) {
        for (int y = 2; y <= 98; y += 4) {
            for (int x = 2; x <= 98; x += 4) {
                int px = x, py = y;
                if (boxes.stream().noneMatch(b -> b.contains(px, py))) return new double[]{px, py};
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> template() {
        // Ohne Bild ungültig – im Editor zuerst ein Bild hochladen
        return Map.of("image", "", "alt", "", "targets", List.of(), "showCount", true);
    }
}

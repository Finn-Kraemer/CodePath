package de.codepath.backend.lesson;

import de.codepath.backend.platform.transfer.ItemRunner;
import de.codepath.backend.platform.transfer.TransferItem;
import de.codepath.backend.platform.transfer.TransferSection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Stundenabläufe als Vorlagen im zentralen Export: ohne Kurs, Datum und Protokoll. Beim Import werden sie
 * als Vorlagen (ohne Kurs) angelegt und über den Titel abgeglichen. Verweise auf Bewertungsrunden und
 * Aufgabenblätter sind IDs dieser Installation und müssen nach einem Import in eine andere ggf. neu gewählt werden.
 */
@Component
@RequiredArgsConstructor
public class LessonTransfer implements TransferSection {

    public record LessonExport(String title, boolean restoreReleases, List<LessonStep> steps) {
    }

    private final LessonRepository repository;
    private final LessonService service;
    private final ItemRunner runner;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return "lessons";
    }

    @Override
    public String label() {
        return "Stundenabläufe";
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    @Transactional(readOnly = true)
    public JsonNode export(Set<String> only) {
        Map<String, LessonExport> byTitle = new LinkedHashMap<>();
        for (Lesson l : repository.findAllByOrderByPositionAscIdAsc()) {
            if (!only.isEmpty() && !only.contains(l.getTitle())) continue;
            byTitle.putIfAbsent(l.getTitle(), new LessonExport(l.getTitle(), l.isRestoreReleases(), service.steps(l)));
        }
        return objectMapper.valueToTree(new ArrayList<>(byTitle.values()));
    }

    @Override
    public List<TransferItem> importData(JsonNode data, Long actorId) {
        if (!data.isArray()) return List.of(TransferItem.error(label(), "-", "Erwartet wird eine Liste von Stundenabläufen."));
        List<TransferItem> items = new ArrayList<>();
        for (JsonNode node : data) {
            String name = node.path("title").asString("?");
            items.add(runner.run(label(), name, () -> importLesson(objectMapper.treeToValue(node, LessonExport.class))));
        }
        return items;
    }

    private TransferItem.Action importLesson(LessonExport in) {
        String title = in.title() == null || in.title().isBlank() ? "Stunde" : in.title().strip();
        String steps = objectMapper.writeValueAsString(service.validate(in.steps() == null ? List.of() : in.steps()));
        Optional<Lesson> existing = repository.findFirstByTitleAndCourseIdIsNullOrderByIdAsc(title);
        if (existing.isPresent() && Objects.equals(objectMapper.readTree(existing.get().getSteps()), objectMapper.readTree(steps))
                && existing.get().isRestoreReleases() == in.restoreReleases()) {
            return TransferItem.Action.UNCHANGED;
        }
        Lesson lesson = existing.orElseGet(() -> {
            Lesson l = new Lesson();
            l.setPosition(repository.findAllByOrderByPositionAscIdAsc().stream().mapToInt(Lesson::getPosition).max().orElse(-1) + 1);
            return l;
        });
        lesson.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        lesson.setSteps(steps);
        lesson.setRestoreReleases(in.restoreReleases());
        lesson.setUpdatedAt(LocalDateTime.now());
        repository.save(lesson);
        return existing.isPresent() ? TransferItem.Action.UPDATED : TransferItem.Action.CREATED;
    }
}

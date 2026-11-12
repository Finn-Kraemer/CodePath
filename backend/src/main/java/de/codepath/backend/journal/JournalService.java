package de.codepath.backend.journal;

import de.codepath.backend.classroom.AccessPolicy;
import de.codepath.backend.content.ContentService;
import de.codepath.backend.content.Module;
import de.codepath.backend.content.PublishedTask;
import de.codepath.backend.identity.User;
import de.codepath.backend.identity.UserService;
import de.codepath.backend.platform.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Persönliches Lerntagebuch. Jede Person sieht und bearbeitet nur ihre eigenen Notizen; die Lehrkraft sieht
 * nur Einträge, die ausdrücklich geteilt wurden. Kein Audit-Eintrag, damit auch das Protokoll keine Inhalte
 * oder Gewohnheiten verrät.
 */
@Service
@RequiredArgsConstructor
public class JournalService {

    /** Höchstens so viele Zeichen je Person und Modul (alle Notizen des Moduls zusammen). */
    public static final int MAX_CHARS_PER_MODULE = 10_000;
    static final int MAX_PROMPT_CHARS = 2_000;

    private final JournalEntryRepository repository;
    private final JournalPromptRepository promptRepository;
    private final ContentService contentService;
    private final AccessPolicy accessPolicy;
    private final UserService userService;

    /** taskSlug/taskTitle null = Notiz zum ganzen Modul */
    public record JournalEntryView(Long id, String moduleSlug, String moduleTitle, String moduleIcon, String taskSlug,
                                   String taskTitle, String text, boolean shared, LocalDateTime updatedAt) {
    }

    public record ModuleJournal(String moduleSlug, String moduleTitle, List<String> prompts, int maxChars,
                                List<JournalEntryView> entries) {
    }

    public record SharedEntry(String username, String displayName, JournalEntryView entry) {
    }

    @Transactional(readOnly = true)
    public List<JournalEntryView> all(User user) {
        return repository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ModuleJournal module(User user, String moduleSlug) {
        Module module = visibleModule(user, moduleSlug);
        List<JournalEntryView> entries = repository.findByUserIdAndModuleId(user.getId(), module.getId()).stream()
                .map(this::view)
                .sorted(Comparator.comparing((JournalEntryView v) -> v.taskSlug() != null).thenComparing(JournalEntryView::updatedAt))
                .toList();
        return new ModuleJournal(module.getSlug(), module.getTitle(), prompts(module.getId()), MAX_CHARS_PER_MODULE, entries);
    }

    /** Speichert eine Notiz (leerer Text löscht sie). */
    @Transactional
    public JournalEntryView save(User user, String moduleSlug, String taskSlug, String text, Boolean shared) {
        if (user.isTeacher()) throw ApiException.badRequest("Das Lerntagebuch ist für Schülerinnen und Schüler.");
        Module module = visibleModule(user, moduleSlug);
        Long taskId = null;
        if (taskSlug != null && !taskSlug.isBlank()) {
            PublishedTask task = contentService.publishedTask(moduleSlug, taskSlug);
            accessPolicy.requireTaskVisible(user, task);
            taskId = task.taskId();
        }
        List<JournalEntry> existing = repository.findByUserIdAndModuleId(user.getId(), module.getId());
        Long finalTaskId = taskId;
        Optional<JournalEntry> current = existing.stream().filter(e -> Objects.equals(e.getTaskId(), finalTaskId)).findFirst();
        String value = text == null ? "" : text.replace("\r", "");
        if (value.isBlank()) {
            current.ifPresent(repository::delete);
            return null;
        }
        int others = existing.stream().filter(e -> !Objects.equals(e.getTaskId(), finalTaskId)).mapToInt(e -> e.getText().length()).sum();
        if (others + value.length() > MAX_CHARS_PER_MODULE) {
            throw ApiException.badRequest("Das Lerntagebuch zu diesem Modul ist voll (höchstens "
                    + MAX_CHARS_PER_MODULE + " Zeichen). Kürze ältere Notizen.");
        }
        JournalEntry entry = current.orElseGet(JournalEntry::new);
        entry.setUserId(user.getId());
        entry.setModuleId(module.getId());
        entry.setTaskId(taskId);
        entry.setText(value);
        if (shared != null) entry.setShared(shared);
        entry.setUpdatedAt(LocalDateTime.now());
        repository.save(entry);
        return view(entry);
    }

    @Transactional
    public void delete(User user, Long id) {
        JournalEntry entry = repository.findById(id).filter(e -> e.getUserId().equals(user.getId()))
                .orElseThrow(() -> ApiException.notFound("Eintrag nicht gefunden."));
        repository.delete(entry);
    }

    /** Für das Mitnehm-Paket: alle eigenen Notizen nach Modul und Aufgabe sortiert. */
    @Transactional(readOnly = true)
    public List<JournalEntryView> forTakeHome(User user) {
        return repository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream().map(this::view)
                .sorted(Comparator.comparing(JournalEntryView::moduleTitle)
                        .thenComparing(v -> v.taskSlug() != null)
                        .thenComparing(v -> v.taskTitle() == null ? "" : v.taskTitle()))
                .toList();
    }

    // ------------------------------------------------------------------ Lehrkraft

    /** Nur geteilte Einträge der Mitglieder eines Kurses (oder einer Person). */
    @Transactional(readOnly = true)
    public List<SharedEntry> shared(Long courseId, String username) {
        List<Long> ids = username != null ? List.of(userService.byUsername(username).getId())
                : courseId != null ? userService.memberIds(courseId)
                : userService.students().stream().map(User::getId).toList();
        if (ids.isEmpty()) return List.of();
        return repository.findBySharedTrueAndUserIdInOrderByUpdatedAtDesc(ids).stream().map(e -> {
            User author = userService.byId(e.getUserId()).orElseThrow();
            return new SharedEntry(author.getUsername(), author.getVisibleName(), view(e));
        }).toList();
    }

    public List<String> prompts(Long moduleId) {
        return promptRepository.findById(moduleId).map(p -> Arrays.stream(p.getPrompts().split("\n"))
                .map(String::strip).filter(s -> !s.isEmpty()).toList()).orElse(List.of());
    }

    @Transactional
    public List<String> setPrompts(Long moduleId, String prompts) {
        contentService.module(moduleId);
        String value = prompts == null ? "" : prompts.replace("\r", "").strip();
        if (value.length() > MAX_PROMPT_CHARS) throw ApiException.badRequest("Die Leitfragen sind zu lang.");
        if (value.isEmpty()) promptRepository.deleteById(moduleId);
        else promptRepository.save(new JournalPrompt(moduleId, value));
        return prompts(moduleId);
    }

    // ------------------------------------------------------------------ Hilfen

    private Module visibleModule(User user, String slug) {
        Module module = contentService.module(slug);
        accessPolicy.requireVisible(user, module);
        return module;
    }

    private JournalEntryView view(JournalEntry e) {
        Module module = contentService.module(e.getModuleId());
        PublishedTask task = e.getTaskId() == null ? null : contentService.publishedTask(e.getTaskId());
        return new JournalEntryView(e.getId(), module.getSlug(), module.getTitle(), module.getIcon(),
                task == null ? null : task.task().getSlug(), task == null ? null : task.version().getTitle(),
                e.getText(), e.isShared(), e.getUpdatedAt());
    }
}

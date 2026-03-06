package de.codepath.backend.features.modules;

import de.codepath.backend.features.tasks.TaskRepository;
import de.codepath.backend.features.tasks.UserTaskCompletionRepository;
import de.codepath.backend.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleRepository moduleRepository;
    private final TaskRepository taskRepository;
    private final UserTaskCompletionRepository completionRepository;

    public List<ModuleResponse> getAllModulesForUser(User user) {
        return moduleRepository.findByIsUnlockedTrue(Sort.by("sortOrder")).stream()
                .map(m -> {
                    long total = taskRepository.countByModuleId(m.getId());
                    long completed = completionRepository.countByUserIdAndTaskModuleId(user.getId(), m.getId());
                    
                    return ModuleResponse.builder()
                            .id(m.getId())
                            .slug(m.getSlug())
                            .title(m.getTitle())
                            .description(m.getDescription())
                            .iconEmoji(m.getIconEmoji())
                            .isUnlocked(m.getIsUnlocked())
                            .totalTasks(total)
                            .completedTasks(completed)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public Module getModuleBySlug(String slug) {
        return moduleRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Module not found"));
    }
}

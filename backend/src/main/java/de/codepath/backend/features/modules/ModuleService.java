package de.codepath.backend.features.modules;

import de.codepath.backend.features.tasks.TaskRepository;
import de.codepath.backend.features.tasks.UserTaskCompletionRepository;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRole;
import de.codepath.backend.common.GlobalExceptionHandler.AccessDeniedException;
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
        List<Module> modules;
        if (user.getRole() == UserRole.ADMIN) {
            modules = moduleRepository.findAll(Sort.by("sortOrder"));
        } else {
            modules = moduleRepository.findByIsUnlockedTrue(Sort.by("sortOrder"));
        }

        return modules.stream()
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
                            .availableUntil(m.getAvailableUntil())
                            .totalTasks(total)
                            .completedTasks(completed)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public Module getModuleBySlug(String slug, User user) {
        Module module = moduleRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Module not found"));
        
        if (user.getRole() != UserRole.ADMIN && !Boolean.TRUE.equals(module.getIsUnlocked())) {
            throw new AccessDeniedException("Dieses Modul ist aktuell gesperrt.");
        }
        
        return module;
    }
}

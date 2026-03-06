package de.codepath.backend.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.codepath.backend.features.modules.Module;
import de.codepath.backend.features.modules.ModuleRepository;
import de.codepath.backend.features.tasks.Task;
import de.codepath.backend.features.tasks.TaskRepository;
import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRepository;
import de.codepath.backend.users.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private final ModuleRepository moduleRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Value("${admin.seed.username:admin}")
    private String adminUsername;

    @Value("${admin.seed.password:admin123}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting data initialization...");
        initializeAdminUser();
        loadContentFromJson();
        log.info("Data initialization completed.");
    }

    private void initializeAdminUser() {
        if (userRepository.findByUsername(adminUsername).isPresent()) return;
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(UserRole.ADMIN);
        admin.setDisplayName("System Admin");
        userRepository.save(admin);
    }

    private void loadContentFromJson() {
        try {
            Resource resource = resourceLoader.getResource("classpath:content.json");
            if (!resource.exists()) {
                log.warn("content.json not found in classpath.");
                return;
            }

            try (InputStream is = resource.getInputStream()) {
                List<ModuleDto> modules = objectMapper.readValue(is, new TypeReference<>() {});
                for (ModuleDto mDto : modules) {
                    Module module = persistModule(mDto);
                    if (mDto.getTasks() != null) {
                        for (TaskDto tDto : mDto.getTasks()) {
                            persistTask(module, tDto);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load content from JSON", e);
        }
    }

    private Module persistModule(ModuleDto dto) {
        Module m = moduleRepository.findBySlug(dto.getSlug()).orElse(new Module());
        m.setSlug(dto.getSlug());
        m.setTitle(dto.getTitle());
        m.setDescription(dto.getDescription());
        m.setIconEmoji(dto.getIconEmoji());
        m.setSortOrder(dto.getSortOrder());
        m.setIsUnlocked(m.getIsUnlocked() != null ? m.getIsUnlocked() : false);
        return moduleRepository.save(m);
    }

    private void persistTask(Module module, TaskDto dto) {
        Task t = taskRepository.findByModuleIdAndSlug(module.getId(), dto.getSlug()).orElse(new Task());
        t.setModule(module);
        t.setSlug(dto.getSlug());
        t.setTitle(dto.getTitle());
        t.setStory(dto.getStory());
        t.setDescription(dto.getDescription());
        t.setType(dto.getType());
        t.setDifficulty(dto.getDifficulty());
        t.setPoints(dto.getPoints());
        t.setSortOrder(dto.getSortOrder());
        t.setConfig(dto.getConfig());
        taskRepository.save(t);
    }

    // Helper DTOs for JSON mapping
    @lombok.Data
    private static class ModuleDto {
        private String slug;
        private String title;
        private String description;
        private String iconEmoji;
        private Integer sortOrder;
        private List<TaskDto> tasks;
    }

    @lombok.Data
    private static class TaskDto {
        private String slug;
        private String title;
        private String story;
        private String description;
        private de.codepath.backend.features.tasks.TaskType type;
        private de.codepath.backend.features.tasks.Difficulty difficulty;
        private Integer points;
        private Integer sortOrder;
        private Map<String, Object> config;
    }
}

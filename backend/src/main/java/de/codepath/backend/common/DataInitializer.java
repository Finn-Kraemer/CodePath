package de.codepath.backend.common;

import de.codepath.backend.users.User;
import de.codepath.backend.users.UserRepository;
import de.codepath.backend.users.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.username:admin}")
    private String adminUsername;

    @Value("${admin.seed.password:admin123}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting data initialization...");
        initializeAdminUser();
        log.info("Data initialization completed.");
    }

    private void initializeAdminUser() {
        Optional<User> existingAdmin = userRepository.findByUsername(adminUsername);
        if (existingAdmin.isPresent()) {
            User admin = existingAdmin.get();
            if (admin.getRole() != UserRole.ADMIN) {
                log.info("Updating existing user '{}' to ADMIN role", adminUsername);
                admin.setRole(UserRole.ADMIN);
                userRepository.save(admin);
            }
            return;
        }
        
        log.info("Creating initial admin user: {}", adminUsername);
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(UserRole.ADMIN);
        admin.setDisplayName("System Admin");
        userRepository.save(admin);
    }

    private void loadContentFromJson() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
            Resource[] resources = resolver.getResources("classpath:content/*.json");
            
            if (resources.length == 0) {
                log.warn("No content files found in classpath:content/");
                return;
            }

            for (Resource resource : resources) {
                log.info("Loading content from: {}", resource.getFilename());
                try (InputStream is = resource.getInputStream()) {
                    ModuleDto mDto = objectMapper.readValue(is, ModuleDto.class);
                    Module module = persistModule(mDto);
                    if (mDto.getTasks() != null) {
                        for (TaskDto tDto : mDto.getTasks()) {
                            persistTask(module, tDto);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load content from JSON files", e);
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

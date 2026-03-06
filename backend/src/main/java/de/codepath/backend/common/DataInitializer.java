package de.codepath.backend.common;

import de.codepath.backend.common.content.ContentModule;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final List<ContentModule> contentModules;

    @Value("${admin.seed.username:admin}")
    private String adminUsername;

    @Value("${admin.seed.password:admin123}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting orchestrated data initialization...");
        initializeAdminUser();
        
        for (ContentModule contentModule : contentModules) {
            Module def = contentModule.getModuleDefinition();
            Module persistedModule = persistModule(def);
            
            List<Task> tasks = contentModule.getTasks(persistedModule);
            for (Task task : tasks) {
                persistTask(task);
            }
        }
        
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

    private Module persistModule(Module m) {
        Optional<Module> existing = moduleRepository.findBySlug(m.getSlug());
        if (existing.isPresent()) {
            Module e = existing.get();
            e.setTitle(m.getTitle());
            e.setDescription(m.getDescription());
            e.setIconEmoji(m.getIconEmoji());
            e.setSortOrder(m.getSortOrder());
            return moduleRepository.save(e);
        }
        return moduleRepository.save(m);
    }

    private void persistTask(Task t) {
        Optional<Task> existing = taskRepository.findByModuleIdAndSlug(t.getModule().getId(), t.getSlug());
        if (existing.isPresent()) {
            Task e = existing.get();
            e.setTitle(t.getTitle());
            e.setDescription(t.getDescription());
            e.setType(t.getType());
            e.setDifficulty(t.getDifficulty());
            e.setPoints(t.getPoints());
            e.setSortOrder(t.getSortOrder());
            e.setConfig(t.getConfig());
            taskRepository.save(e);
            return;
        }
        taskRepository.save(t);
    }
}

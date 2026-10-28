package de.codepath.backend.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Legt beim ersten Start das Konto der Lehrkraft an. Ohne ADMIN_SEED_PASSWORD wird ein zufälliges
 * Passwort erzeugt und einmalig ins Log geschrieben – es gibt kein Standardpasswort.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.username:admin}")
    private String adminUsername;

    @Value("${admin.seed.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var existing = userRepository.findByUsername(adminUsername);
        if (existing.isPresent()) {
            if (existing.get().getRole() != UserRole.ADMIN) {
                existing.get().setRole(UserRole.ADMIN);
            }
            return;
        }
        String password = adminPassword;
        if (password == null || password.isBlank()) {
            byte[] bytes = new byte[18];
            new SecureRandom().nextBytes(bytes);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            log.warn("ADMIN_SEED_PASSWORD ist nicht gesetzt. Erzeugtes Passwort für '{}': {}  (bitte notieren, wird nicht erneut angezeigt)",
                    adminUsername, password);
        }
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        admin.setDisplayName("Lehrkraft");
        userRepository.save(admin);
        log.info("Konto der Lehrkraft '{}' angelegt", adminUsername);
    }
}

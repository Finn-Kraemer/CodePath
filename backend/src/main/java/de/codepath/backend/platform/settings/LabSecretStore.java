package de.codepath.backend.platform.settings;

import de.codepath.backend.engine.lab.LabSecret;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Hält das Geheimnis für die Labor-Codes in system_settings. Beim ersten Bedarf zufällig erzeugt; danach
 * bleibt es gleich, damit gefundene Codes einen Neustart überstehen. Wird nie über die API ausgegeben.
 */
@Component
@RequiredArgsConstructor
public class LabSecretStore implements LabSecret {

    static final String KEY = "lab_secret";

    private final SystemSettingRepository repository;
    private volatile byte[] cached;

    @Override
    @Transactional
    public byte[] secret() {
        byte[] value = cached;
        if (value != null) return value;
        synchronized (this) {
            if (cached == null) {
                String hex = repository.findById(KEY).map(SystemSetting::getValue).orElseGet(() -> {
                    byte[] random = new byte[32];
                    new SecureRandom().nextBytes(random);
                    String created = HexFormat.of().formatHex(random);
                    repository.saveAndFlush(new SystemSetting(KEY, created));
                    return created;
                });
                cached = HexFormat.of().parseHex(hex);
            }
            return cached;
        }
    }
}

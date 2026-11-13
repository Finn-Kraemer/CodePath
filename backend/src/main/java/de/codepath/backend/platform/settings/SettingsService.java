package de.codepath.backend.platform.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Schalter für die ganze Installation (Feedback-Runde, Zeugnisse). */
@Service
@RequiredArgsConstructor
public class SettingsService {

    public static final String FEEDBACK_ENABLED = "feedback_enabled";
    public static final String CERTIFICATES_ENABLED = "certificates_enabled";
    /** Anwesenheitsquote auf der Teilnahmebestätigung (Datenschutz: standardmäßig aus) */
    public static final String ATTENDANCE_ON_CERTIFICATE = "attendance_on_certificate";
    /** Schülerinnen und Schüler sehen ihre eigene Anwesenheit im Profil */
    public static final String ATTENDANCE_VISIBLE_TO_STUDENTS = "attendance_visible_to_students";

    private final SystemSettingRepository repository;

    public boolean isEnabled(String key) {
        return repository.findById(key).map(s -> "true".equalsIgnoreCase(s.getValue())).orElse(false);
    }

    @Transactional
    public void set(String key, boolean value) {
        repository.save(new SystemSetting(key, String.valueOf(value)));
    }

    @Transactional
    public boolean toggle(String key) {
        boolean newValue = !isEnabled(key);
        repository.save(new SystemSetting(key, String.valueOf(newValue)));
        return newValue;
    }
}

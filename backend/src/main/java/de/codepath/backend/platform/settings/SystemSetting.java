package de.codepath.backend.platform.settings;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "system_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SystemSetting {

    @Id
    @Column(name = "\"key\"")
    private String key;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String value;
}

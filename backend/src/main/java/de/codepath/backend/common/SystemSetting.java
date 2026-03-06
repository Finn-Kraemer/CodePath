package de.codepath.backend.common;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "system_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SystemSetting {

    @Id
    private String key;

    @Column(nullable = false)
    private String value;
}

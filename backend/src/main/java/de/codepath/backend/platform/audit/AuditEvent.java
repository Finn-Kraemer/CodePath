package de.codepath.backend.platform.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/** Eintrag im Audit-Log. Nur anhängen, nie ändern. */
@Entity
@Immutable
@Table(name = "audit_events")
@Getter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private String actor;

    @Column(nullable = false)
    private String action;

    private String target;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> details;

    public AuditEvent(String actor, String action, String target, Map<String, Object> details) {
        this.createdAt = LocalDateTime.now();
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.details = details;
    }
}

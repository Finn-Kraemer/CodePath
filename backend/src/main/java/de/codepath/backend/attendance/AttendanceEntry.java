package de.codepath.backend.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "attendance_entries")
@IdClass(AttendanceEntry.Key.class)
@Getter @Setter @NoArgsConstructor
public class AttendanceEntry {

    @Id
    @Column(name = "session_id")
    private Long sessionId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    private String note;

    public AttendanceEntry(Long sessionId, Long userId, AttendanceStatus status) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.status = status;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Key implements Serializable {
        private Long sessionId;
        private Long userId;
    }
}

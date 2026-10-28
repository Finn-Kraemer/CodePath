package de.codepath.backend.content;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "media")
@Getter @Setter @NoArgsConstructor
public class MediaFile {

    @Id
    @Column(name = "media_key")
    private String key;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "size_bytes", nullable = false)
    private int size;

    private Integer width;

    private Integer height;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;
}

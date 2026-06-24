package com.web.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chunk_embeddings", indexes = {
    @Index(name = "idx_chunk_entity_name", columnList = "entity_name"),
    @Index(name = "idx_chunk_content_type", columnList = "content_type")
})
public class ChunkEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ContentType contentType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "entity_slug")
    private String entitySlug;

    @Column(name = "entity_name")
    private String entityName;

    @Column(name = "chunk_text", columnDefinition = "TEXT", nullable = false)
    private String chunkText;

    @Column(name = "embedding", columnDefinition = "JSON")
    private String embedding; // JSON string: [0.1, 0.2, ...]

    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata; // JSON string cho thêm thông tin

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Gán thời điểm tạo chunk trước khi lưu.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum ContentType {
        plant, article, research, disease, folk_remedy
    }
}

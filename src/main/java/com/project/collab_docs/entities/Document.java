package com.project.collab_docs.entities;

import com.project.collab_docs.enums.Visibility;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "file_name", length = 255)
    private String fileName; // Original file name for uploaded documents

    @Column(name = "content_type", length = 100)
    private String contentType; // MIME type (application/vnd.openxmlformats-officedocument.wordprocessingml.document, application/pdf, etc.)

    @Lob
    @Column(name = "content", columnDefinition = "TEXT")
    private String content; // Tiptap JSON content or HTML content

    @Column(name = "file_size")
    private Long fileSize; // Size in bytes

    @Column(name = "yjs_room_id", unique = true, nullable = false, length = 100)
    private String yjsRoomId; // Unique room ID for Yjs collaboration

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "is_template", nullable = false)
    @Builder.Default
    private Boolean isTemplate = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Lob
    @Column(name = "yjs_snapshot")
    private byte[] yjsSnapshot; // Optional binary snapshot of Yjs doc (Uint8Array)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility; // "private", "shared", "public"

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @PrePersist
    public void setCreationTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void setUpdateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}

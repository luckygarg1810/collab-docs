package com.project.collab_docs.entities;

import com.project.collab_docs.enums.Visibility;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    private String contentType; // Original file MIME type for tracking upload source
                                // (application/vnd.openxmlformats-officedocument.wordprocessingml.document,
                                // application/pdf, etc.)

    @Column(name = "file_size")
    private Long fileSize; // Original upload file size in bytes (for tracking only)

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

    // VARBINARY forces a plain Postgres bytea column instead of Hibernate's
    // default oid (Large Object) mapping for @Lob byte[]. Large Objects are
    // transaction-scoped — reading one back outside the transaction that
    // fetched it throws "Large Objects may not be used in auto-commit mode."
    // bytea is materialized with the row like any other column, no special
    // transaction handling required.
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "yjs_snapshot", columnDefinition = "bytea")
    private byte[] yjsSnapshot; // Optional binary snapshot of Yjs doc (Uint8Array)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility; // "private", "shared", "public"

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    // Set when soft-deleted (moved to Recycle Bin), cleared on restore. Drives
    // the 15-day auto-purge window and the "deleted X ago" display.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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

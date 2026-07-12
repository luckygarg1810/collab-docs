package com.project.collab_docs.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Entity representing a snapshot/version of a document at a specific point in time.
 * Enables version history, rollback, and audit trail functionality.
 *
 * Design decisions:
 * - Stores both Yjs binary snapshot (for perfect restoration) and content snapshot (for previews)
 * - Includes hash for integrity verification
 * - Supports user-provided labels for meaningful version names
 * - Tracks storage size for quota management
 */
@Entity
@Table(name = "document_versions", indexes = {
    @Index(name = "idx_document_id", columnList = "document_id"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_document_version", columnList = "document_id,version_number", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the parent document
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /**
     * Sequential version number (auto-incremented per document)
     * Version 1 is typically the first saved snapshot
     */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /**
     * User who created this version
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    /**
     * When this version was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Optional user-provided label/description for this version
     * Examples: "Final draft", "Before review", "Approved version"
     */
    @Column(name = "version_name", length = 255)
    private String versionName;

    /**
     * Binary Yjs CRDT snapshot at this point in time
     * This allows perfect restoration of the collaborative editing state
     */
    // See Document.yjsSnapshot for why VARBINARY (bytea) instead of @Lob's
    // default oid mapping — oid reads fail outside their fetch transaction.
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "yjs_snapshot", nullable = false, columnDefinition = "bytea")
    private byte[] yjsSnapshot;

    /**
     * Human-readable content snapshot (HTML or JSON)
     * Useful for quick previews without decoding Yjs binary
     */
    @Column(name = "content_snapshot", columnDefinition = "TEXT")
    private String contentSnapshot;

    /**
     * Size of the snapshot in bytes
     * Useful for storage management and analytics
     */
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /**
     * SHA-256 hash of the Yjs snapshot for integrity verification
     * Ensures version hasn't been tampered with
     */
    @Column(name = "snapshot_hash", length = 64)
    private String snapshotHash;

    /**
     * Optional notes/comments about what changed in this version
     */
    @Column(name = "change_notes", columnDefinition = "TEXT")
    private String changeNotes;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (sizeBytes == null && yjsSnapshot != null) {
            sizeBytes = (long) yjsSnapshot.length;
        }
    }

    /**
     * Get version identifier string (e.g., "v1", "v42")
     */
    public String getVersionIdentifier() {
        return "v" + versionNumber;
    }

    /**
     * Get display name for this version
     * Returns custom name if set, otherwise returns "Version X"
     */
    public String getDisplayName() {
        if (versionName != null && !versionName.trim().isEmpty()) {
            return versionName + " (" + getVersionIdentifier() + ")";
        }
        return "Version " + versionNumber;
    }
}


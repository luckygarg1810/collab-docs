package com.project.collab_docs.entities;

import com.project.collab_docs.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing user permissions for a document.
 * Implements role-based access control (RBAC) for document sharing.
 */
@Entity
@Table(name = "document_permissions", uniqueConstraints = @UniqueConstraint(columnNames = { "document_id",
        "user_id" }, name = "uk_document_user_permission"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Document this permission applies to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /**
     * User who has this permission.
     * Nullable to support future anonymous access via share links.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    /**
     * Permission level (OWNER, EDITOR, VIEWER)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /**
     * User who granted this permission.
     * Used for audit trail.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id", nullable = false)
    private User grantedBy;

    /**
     * When the permission was granted
     */
    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    /**
     * Optional expiration time for temporary access.
     * Null means permanent access.
     */
    @Column(name = "expires_at", nullable = true)
    private LocalDateTime expiresAt;

    /**
     * ID of the ShareLink that granted this permission (nullable).
     * When a share link is deleted/revoked, all permissions created via
     * that link are also revoked.
     */
    @Column(name = "granted_via_share_link_id", nullable = true)
    private Long grantedViaShareLinkId;

    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) {
            grantedAt = LocalDateTime.now();
        }
    }

    /**
     * Check if this permission is currently valid (not expired)
     */
    public boolean isValid() {
        return expiresAt == null || LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * Check if this permission grants at least the required role
     */
    public boolean hasPermissionOf(Role required) {
        return isValid() && role.hasPermissionOf(required);
    }
}

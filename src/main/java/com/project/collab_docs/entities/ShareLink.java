package com.project.collab_docs.entities;

import com.project.collab_docs.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a shareable link for document access.
 * Enables document sharing via URL without requiring explicit user invitations.
 *
 * Security features:
 * - Unique random token (32+ chars) prevents guessing
 * - Optional expiration time
 * - Optional usage limits (maxUses)
 * - Can be deactivated instantly
 * - Optional authentication requirement
 *
 * Use cases:
 * - Public sharing with view-only access
 * - Time-limited collaboration links
 * - One-time access links
 * - Authenticated-only sharing for additional security
 */
@Entity
@Table(name = "share_links", indexes = {
    @Index(name = "idx_token", columnList = "token", unique = true),
    @Index(name = "idx_document_id", columnList = "document_id"),
    @Index(name = "idx_created_by", columnList = "created_by_user_id"),
    @Index(name = "idx_active_expires", columnList = "is_active,expires_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Document this link provides access to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /**
     * Unique token used in the share URL.
     * Generated using SecureRandom, minimum 32 characters.
     * URL format: /share/{token}
     */
    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    /**
     * Permission level granted via this link
     * Typically VIEWER or EDITOR (OWNER not allowed via share links)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /**
     * User who created this share link
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    /**
     * When the link was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When the link expires.
     * Null means no expiration (permanent link)
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Maximum number of times this link can be used.
     * Null means unlimited uses.
     */
    @Column(name = "max_uses")
    private Integer maxUses;

    /**
     * Number of times this link has been used
     */
    @Column(name = "current_uses", nullable = false)
    @Builder.Default
    private Integer currentUses = 0;

    /**
     * Whether the link is currently active.
     * Can be set to false to instantly disable sharing without deleting the record.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * If true, user must be authenticated to use this link.
     * If false, anonymous users can access via the link.
     */
    @Column(name = "requires_auth", nullable = false)
    @Builder.Default
    private Boolean requiresAuth = false;

    /**
     * Optional description/label for this share link
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * When the link was last used
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (currentUses == null) {
            currentUses = 0;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (requiresAuth == null) {
            requiresAuth = false;
        }
    }

    /**
     * Check if this share link is currently valid and can be used
     */
    public boolean isValid() {
        if (!isActive) {
            return false;
        }

        // Check expiration
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }

        // Check usage limit
        if (maxUses != null && currentUses >= maxUses) {
            return false;
        }

        return true;
    }

    /**
     * Increment usage counter and update last used timestamp
     */
    public void incrementUsage() {
        this.currentUses++;
        this.lastUsedAt = LocalDateTime.now();
    }

    /**
     * Check if link has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if link has reached usage limit
     */
    public boolean isUsageLimitReached() {
        return maxUses != null && currentUses >= maxUses;
    }

    /**
     * Get remaining uses (-1 for unlimited)
     */
    public int getRemainingUses() {
        if (maxUses == null) {
            return -1; // Unlimited
        }
        return Math.max(0, maxUses - currentUses);
    }

    /**
     * Get the full share URL (without domain)
     */
    public String getShareUrl() {
        return "/share/" + token;
    }
}


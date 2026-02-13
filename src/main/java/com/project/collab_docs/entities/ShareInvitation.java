
package com.project.collab_docs.entities;

import com.project.collab_docs.enums.InvitationStatus;
import com.project.collab_docs.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a direct invitation to collaborate on a document.
 * Sent via email to specific users, requiring acceptance before granting access.
 *
 * Workflow:
 * 1. Owner/Editor invites user by email
 * 2. Email sent with invitation link
 * 3. Recipient accepts/declines via link
 * 4. Upon acceptance, DocumentPermission is created
 *
 * Features:
 * - Email-based invitations
 * - Status tracking (PENDING, ACCEPTED, DECLINED, EXPIRED, REVOKED)
 * - Automatic expiration after configurable period
 * - Can invite non-registered users (they must register first)
 * - Audit trail of who invited whom
 */
@Entity
@Table(name = "share_invitations", indexes = {
    @Index(name = "idx_email_document", columnList = "invited_email,document_id"),
    @Index(name = "idx_document_id", columnList = "document_id"),
    @Index(name = "idx_invited_by", columnList = "invited_by_user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_invited_user", columnList = "invited_user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Document being shared
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /**
     * Email address of the invited user.
     * User may or may not be registered yet.
     */
    @Column(name = "invited_email", nullable = false, length = 255)
    private String invitedEmail;

    /**
     * The registered user (if they have registered).
     * Null if invitation sent to unregistered email.
     * Set when invitation is accepted and user is found/created.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_user_id")
    private User invitedUser;

    /**
     * Role that will be granted upon acceptance
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /**
     * User who sent the invitation
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private User invitedBy;

    /**
     * When the invitation was sent
     */
    @Column(name = "invited_at", nullable = false, updatable = false)
    private LocalDateTime invitedAt;

    /**
     * When the invitation was accepted/declined
     */
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    /**
     * Current status of the invitation
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    /**
     * Unique token for accepting/declining invitation.
     * Used in email link: /invitations/accept/{token}
     */
    @Column(name = "token", unique = true, nullable = false, length = 64)
    private String token;

    /**
     * When the invitation expires (typically 7 days from creation)
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Optional personal message from inviter
     */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @PrePersist
    protected void onCreate() {
        if (invitedAt == null) {
            invitedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = InvitationStatus.PENDING;
        }
    }

    /**
     * Check if invitation is still valid and can be accepted
     */
    public boolean isValid() {
        if (status != InvitationStatus.PENDING) {
            return false;
        }

        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }

        return true;
    }

    /**
     * Mark invitation as accepted
     */
    public void accept(User user) {
        this.status = InvitationStatus.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
        this.invitedUser = user;
    }

    /**
     * Mark invitation as declined
     */
    public void decline() {
        this.status = InvitationStatus.DECLINED;
        this.respondedAt = LocalDateTime.now();
    }

    /**
     * Mark invitation as revoked
     */
    public void revoke() {
        this.status = InvitationStatus.REVOKED;
        this.respondedAt = LocalDateTime.now();
    }

    /**
     * Mark invitation as expired
     */
    public void expire() {
        this.status = InvitationStatus.EXPIRED;
        this.respondedAt = LocalDateTime.now();
    }

    /**
     * Check if invitation has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if invitation is pending
     */
    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    /**
     * Check if invitation was accepted
     */
    public boolean isAccepted() {
        return status == InvitationStatus.ACCEPTED;
    }
}


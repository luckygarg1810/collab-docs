package com.project.collab_docs.service;

import com.project.collab_docs.dto.request.CreateShareLinkRequest;
import com.project.collab_docs.dto.request.ShareInvitationRequest;
import com.project.collab_docs.dto.response.DocumentAccessResponse;
import com.project.collab_docs.dto.response.ShareInvitationResponse;
import com.project.collab_docs.dto.response.ShareLinkResponse;
import com.project.collab_docs.entities.*;
import com.project.collab_docs.enums.InvitationStatus;
import com.project.collab_docs.enums.Role;
import com.project.collab_docs.exception.*;
import com.project.collab_docs.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing document sharing via links and email invitations.
 *
 * Features:
 * - Generate shareable links with customizable permissions and expiration
 * - Send email invitations to specific users
 * - Track link usage and enforce limits
 * - Automatic cleanup of expired links and invitations
 * - Security: validate permissions, prevent OWNER role via sharing
 *
 * Security considerations:
 * - Share links use SecureRandom for cryptographic strength
 * - OWNER role cannot be granted via share links (only direct permission)
 * - All operations verify user has permission to share (EDITOR or OWNER)
 * - Rate limiting should be applied at controller level
 */
@Service
@Slf4j
public class ShareService {

    @Autowired
    private ShareLinkRepository shareLinkRepository;

    @Autowired
    private ShareInvitationRepository invitationRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private EmailService emailService;

    @Value("${app.share.default-expiry-days:7}")
    private int defaultExpiryDays;

    @Value("${app.share.invitation-expiry-days:7}")
    private int invitationExpiryDays;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int TOKEN_LENGTH = 32;

    // ==================== Share Link Management ====================

    /**
     * Create a new shareable link for a document
     *
     * @param documentId Document to share
     * @param request    Share link configuration
     * @param userId     User creating the link (must have EDITOR or OWNER permission)
     * @return Created share link
     * @throws ResourceNotFoundException if document or user not found
     * @throws PermissionDeniedException if user lacks permission to share
     * @throws IllegalArgumentException  if trying to grant OWNER role via link
     */
    @Transactional
    public ShareLinkResponse createShareLink(Long documentId, CreateShareLinkRequest request, Long userId) {
        // Validate document exists
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check permissions - must be EDITOR or OWNER to share
        if (!permissionService.hasPermission(documentId, userId, Role.EDITOR)) {
            throw new PermissionDeniedException("You don't have permission to share this document");
        }

        // Validate role - cannot grant OWNER via share link
        if (request.getRole() == Role.OWNER) {
            throw new IllegalArgumentException("Cannot grant OWNER role via share link");
        }

        // Generate unique token
        String token = generateUniqueToken();

        // Calculate expiration
        LocalDateTime expiresAt = null;
        if (request.getExpiresInDays() != null && request.getExpiresInDays() > 0) {
            expiresAt = LocalDateTime.now().plusDays(request.getExpiresInDays());
        }

        // Create share link
        ShareLink shareLink = ShareLink.builder()
                .document(document)
                .token(token)
                .role(request.getRole())
                .createdBy(user)
                .expiresAt(expiresAt)
                .maxUses(request.getMaxUses())
                .requiresAuth(request.getRequiresAuth() != null ? request.getRequiresAuth() : false)
                .description(request.getDescription())
                .build();

        shareLink = shareLinkRepository.save(shareLink);

        log.info("Created share link for document {} by user {} with role {}",
                documentId, userId, request.getRole());

        return ShareLinkResponse.from(shareLink);
    }

    /**
     * Validate and access a document via share link
     *
     * @param token  Share link token
     * @param userId User accessing the link (null for anonymous)
     * @return Share link information
     * @throws InvalidShareLinkException if link is invalid, expired, or usage limit reached
     */
    @Transactional
    public ShareLinkResponse validateShareLink(String token, Long userId) {
        ShareLink shareLink = shareLinkRepository.findByToken(token)
                .orElseThrow(() -> new InvalidShareLinkException("Share link not found"));

        // Check if link is valid
        if (!shareLink.isValid()) {
            if (shareLink.isExpired()) {
                throw new InvalidShareLinkException("This share link has expired");
            }
            if (shareLink.isUsageLimitReached()) {
                throw new InvalidShareLinkException("This share link has reached its usage limit");
            }
            if (!shareLink.getIsActive()) {
                throw new InvalidShareLinkException("This share link has been deactivated");
            }
            throw new InvalidShareLinkException("This share link is no longer valid");
        }

        // Check authentication requirement
        if (shareLink.getRequiresAuth() && userId == null) {
            throw new InvalidShareLinkException("Authentication required to access this link");
        }

        return ShareLinkResponse.from(shareLink);
    }

    /**
     * Grant access to a user via share link and increment usage counter
     *
     * This method is called AFTER user authentication (login/register).
     * It creates a permanent DocumentPermission record for the user.
     *
     * @param token  Share link token
     * @param userId User to grant access to (must be authenticated)
     * @return Document details for accessing the document
     * @throws InvalidShareLinkException if link is invalid, expired, or usage limit reached
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional
    public DocumentAccessResponse accessViaShareLink(String token, Long userId) {
        ShareLink shareLink = shareLinkRepository.findByToken(token)
                .orElseThrow(() -> new InvalidShareLinkException("Share link not found"));

        if (!shareLink.isValid()) {
            if (shareLink.isExpired()) {
                throw new InvalidShareLinkException("This share link has expired");
            }
            if (shareLink.isUsageLimitReached()) {
                throw new InvalidShareLinkException("This share link has reached its usage limit");
            }
            if (!shareLink.getIsActive()) {
                throw new InvalidShareLinkException("This share link has been deactivated");
            }
            throw new InvalidShareLinkException("This share link is no longer valid");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Document document = shareLink.getDocument();
        boolean alreadyHadAccess = false;

        // Check if user already has permission
        if (permissionRepository.existsByDocumentIdAndUserId(document.getId(), userId)) {
            // User already has access, just increment usage
            alreadyHadAccess = true;
            log.info("User {} accessed document {} via share link (already has permission)",
                    userId, document.getId());
        } else {
            // Grant permission
            DocumentPermission permission = DocumentPermission.builder()
                    .document(document)
                    .user(user)
                    .role(shareLink.getRole())
                    .grantedBy(shareLink.getCreatedBy())
                    .grantedAt(LocalDateTime.now())
                    .build();

            permissionRepository.save(permission);
            log.info("Granted {} permission to user {} for document {} via share link",
                    shareLink.getRole(), userId, document.getId());
        }

        // Increment usage counter
        shareLink.incrementUsage();
        shareLinkRepository.save(shareLink);

        // Return document details
        return DocumentAccessResponse.builder()
                .documentId(document.getId())
                .title(document.getTitle())
                .yjsRoomId(document.getYjsRoomId())
                .role(shareLink.getRole())
                .isAnonymous(false)
                .hasPermissionGranted(!alreadyHadAccess)
                .accessExpiresAt(shareLink.getExpiresAt())
                .message(alreadyHadAccess
                    ? "You already have access to this document"
                    : "Access granted! You can now collaborate on this document")
                .ownerName(document.getOwner().getFirstName() + " " + document.getOwner().getLastName())
                .build();
    }

    /**
     * Get all share links for a document
     */
    @Transactional(readOnly = true)
    public List<ShareLinkResponse> getDocumentShareLinks(Long documentId, Long userId) {
        // Check permissions
        if (!permissionService.hasPermission(documentId, userId, Role.VIEWER)) {
            throw new PermissionDeniedException("You don't have access to this document");
        }

        return shareLinkRepository.findByDocumentId(documentId).stream()
                .map(ShareLinkResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Get active share links for a document
     */
    @Transactional(readOnly = true)
    public List<ShareLinkResponse> getActiveShareLinks(Long documentId, Long userId) {
        // Check permissions
        if (!permissionService.hasPermission(documentId, userId, Role.VIEWER)) {
            throw new PermissionDeniedException("You don't have access to this document");
        }

        return shareLinkRepository.findActiveByDocumentId(documentId).stream()
                .map(ShareLinkResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Revoke (deactivate) a share link
     */
    @Transactional
    public void revokeShareLink(Long linkId, Long userId) {
        ShareLink shareLink = shareLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));

        // Check permissions - must be creator or document owner
        boolean isCreator = shareLink.getCreatedBy().getId().equals(userId);
        boolean isOwner = permissionService.hasPermission(shareLink.getDocument().getId(), userId, Role.OWNER);

        if (!isCreator && !isOwner) {
            throw new PermissionDeniedException("You don't have permission to revoke this share link");
        }

        shareLink.setIsActive(false);
        shareLinkRepository.save(shareLink);

        log.info("Revoked share link {} by user {}", linkId, userId);
    }

    /**
     * Delete a share link permanently
     */
    @Transactional
    public void deleteShareLink(Long linkId, Long userId) {
        ShareLink shareLink = shareLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));

        // Check permissions - must be creator or document owner
        boolean isCreator = shareLink.getCreatedBy().getId().equals(userId);
        boolean isOwner = permissionService.hasPermission(shareLink.getDocument().getId(), userId, Role.OWNER);

        if (!isCreator && !isOwner) {
            throw new PermissionDeniedException("You don't have permission to delete this share link");
        }

        shareLinkRepository.delete(shareLink);
        log.info("Deleted share link {} by user {}", linkId, userId);
    }

    // ==================== Email Invitation Management ====================

    /**
     * Send an invitation to collaborate via email
     *
     * @param documentId Document to share
     * @param request    Invitation details
     * @param userId     User sending the invitation
     * @return Created invitation
     */
    @Transactional
    public ShareInvitationResponse sendInvitation(Long documentId, ShareInvitationRequest request, Long userId) {
        // Validate document exists
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Validate user exists
        User inviter = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check permissions - must be EDITOR or OWNER
        if (!permissionService.hasPermission(documentId, userId, Role.EDITOR)) {
            throw new PermissionDeniedException("You don't have permission to share this document");
        }

        // Validate role - cannot grant OWNER via invitation
        if (request.getRole() == Role.OWNER) {
            throw new IllegalArgumentException("Cannot grant OWNER role via invitation");
        }

        // Check if user is inviting themselves
        if (inviter.getEmail().equalsIgnoreCase(request.getEmail())) {
            throw new IllegalArgumentException("Cannot invite yourself");
        }

        // Check if email already has pending invitation
        if (invitationRepository.existsPendingInvitation(request.getEmail(), documentId)) {
            throw new DuplicateInvitationException("A pending invitation already exists for this email");
        }

        // Check if user already has permission (if registered)
        User invitedUser = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (invitedUser != null && permissionRepository.existsByDocumentIdAndUserId(documentId, invitedUser.getId())) {
            throw new DuplicateInvitationException("User already has access to this document");
        }

        // Generate unique token
        String token = generateUniqueToken();

        // Create invitation
        ShareInvitation invitation = ShareInvitation.builder()
                .document(document)
                .invitedEmail(request.getEmail())
                .invitedUser(invitedUser)
                .role(request.getRole())
                .invitedBy(inviter)
                .token(token)
                .message(request.getMessage())
                .expiresAt(LocalDateTime.now().plusDays(invitationExpiryDays))
                .build();

        invitation = invitationRepository.save(invitation);

        // Send invitation email
        sendInvitationEmail(invitation);

        log.info("Sent invitation to {} for document {} by user {}",
                request.getEmail(), documentId, userId);

        return ShareInvitationResponse.from(invitation);
    }

    /**
     * Accept an invitation
     *
     * @param token  Invitation token
     * @param userId User accepting the invitation
     * @throws InvalidInvitationException if invitation is invalid or expired
     */
    @Transactional
    public ShareInvitationResponse acceptInvitation(String token, Long userId) {
        ShareInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new InvalidInvitationException("Invitation not found"));

        if (!invitation.isValid()) {
            if (invitation.isExpired()) {
                throw new InvalidInvitationException("This invitation has expired");
            }
            throw new InvalidInvitationException("This invitation is no longer valid");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Verify email matches
        if (!user.getEmail().equalsIgnoreCase(invitation.getInvitedEmail())) {
            throw new InvalidInvitationException("This invitation was sent to a different email address");
        }

        // Check if user already has permission
        if (permissionRepository.existsByDocumentIdAndUserId(invitation.getDocument().getId(), userId)) {
            // Mark as accepted but don't create duplicate permission
            invitation.accept(user);
            invitationRepository.save(invitation);
            log.info("User {} accepted invitation but already has permission to document {}",
                    userId, invitation.getDocument().getId());
            return ShareInvitationResponse.from(invitation);
        }

        // Grant permission
        DocumentPermission permission = DocumentPermission.builder()
                .document(invitation.getDocument())
                .user(user)
                .role(invitation.getRole())
                .grantedBy(invitation.getInvitedBy())
                .grantedAt(LocalDateTime.now())
                .build();

        permissionRepository.save(permission);

        // Mark invitation as accepted
        invitation.accept(user);
        invitationRepository.save(invitation);

        log.info("User {} accepted invitation and granted {} permission to document {}",
                userId, invitation.getRole(), invitation.getDocument().getId());

        return ShareInvitationResponse.from(invitation);
    }

    /**
     * Decline an invitation
     */
    @Transactional
    public void declineInvitation(String token, Long userId) {
        ShareInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new InvalidInvitationException("Invitation not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Verify email matches
        if (!user.getEmail().equalsIgnoreCase(invitation.getInvitedEmail())) {
            throw new InvalidInvitationException("This invitation was sent to a different email address");
        }

        invitation.decline();
        invitationRepository.save(invitation);

        log.info("User {} declined invitation to document {}", userId, invitation.getDocument().getId());
    }

    /**
     * Get all invitations for a document
     */
    @Transactional(readOnly = true)
    public List<ShareInvitationResponse> getDocumentInvitations(Long documentId, Long userId) {
        // Check permissions
        if (!permissionService.hasPermission(documentId, userId, Role.VIEWER)) {
            throw new PermissionDeniedException("You don't have access to this document");
        }

        return invitationRepository.findByDocumentId(documentId).stream()
                .map(ShareInvitationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Get pending invitations for a user
     */
    @Transactional(readOnly = true)
    public List<ShareInvitationResponse> getPendingInvitations(String email) {
        return invitationRepository.findPendingByEmail(email).stream()
                .map(ShareInvitationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Revoke an invitation
     */
    @Transactional
    public void revokeInvitation(Long invitationId, Long userId) {
        ShareInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        // Check permissions - must be sender or document owner
        boolean isSender = invitation.getInvitedBy().getId().equals(userId);
        boolean isOwner = permissionService.hasPermission(invitation.getDocument().getId(), userId, Role.OWNER);

        if (!isSender && !isOwner) {
            throw new PermissionDeniedException("You don't have permission to revoke this invitation");
        }

        invitation.revoke();
        invitationRepository.save(invitation);

        log.info("Revoked invitation {} by user {}", invitationId, userId);
    }

    // ==================== Cleanup Jobs ====================

    /**
     * Scheduled job to mark expired share links as inactive
     * Runs every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredShareLinks() {
        List<ShareLink> expiredLinks = shareLinkRepository.findExpiredLinks();
        for (ShareLink link : expiredLinks) {
            link.setIsActive(false);
        }
        if (!expiredLinks.isEmpty()) {
            shareLinkRepository.saveAll(expiredLinks);
            log.info("Deactivated {} expired share links", expiredLinks.size());
        }

        // Also deactivate links that reached usage limit
        List<ShareLink> usageLimitLinks = shareLinkRepository.findUsageLimitReachedLinks();
        for (ShareLink link : usageLimitLinks) {
            link.setIsActive(false);
        }
        if (!usageLimitLinks.isEmpty()) {
            shareLinkRepository.saveAll(usageLimitLinks);
            log.info("Deactivated {} share links that reached usage limit", usageLimitLinks.size());
        }
    }

    /**
     * Scheduled job to mark expired invitations
     * Runs every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredInvitations() {
        int count = invitationRepository.markExpiredInvitations();
        if (count > 0) {
            log.info("Marked {} invitations as expired", count);
        }
    }

    /**
     * Delete old inactive share links and processed invitations
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void deleteOldRecords() {
        // Delete share links inactive for more than 90 days
        LocalDateTime linkCutoff = LocalDateTime.now().minusDays(90);
        shareLinkRepository.deleteOldInactiveLinks(linkCutoff);

        // Delete invitations processed more than 30 days ago
        LocalDateTime invitationCutoff = LocalDateTime.now().minusDays(30);
        invitationRepository.deleteOldInvitations(invitationCutoff);

        log.info("Cleanup completed for old share links and invitations");
    }

    // ==================== Helper Methods ====================

    /**
     * Generate a cryptographically secure random token
     */
    private String generateUniqueToken() {
        String token;
        do {
            byte[] bytes = new byte[TOKEN_LENGTH];
            secureRandom.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (shareLinkRepository.existsByToken(token) || invitationRepository.existsByToken(token));
        return token;
    }

    /**
     * Send invitation email to user
     */
    private void sendInvitationEmail(ShareInvitation invitation) {
        try {
            String acceptUrl = frontendUrl + "/invitations/accept/" + invitation.getToken();
            String declineUrl = frontendUrl + "/invitations/decline/" + invitation.getToken();

            String subject = invitation.getInvitedBy().getFirstName() + " invited you to collaborate on \""
                            + invitation.getDocument().getTitle() + "\"";

            String body = String.format(
                "Hello,\n\n" +
                "%s %s has invited you to collaborate on the document \"%s\" with %s access.\n\n" +
                "%s\n\n" +
                "To accept this invitation, click here:\n%s\n\n" +
                "To decline, click here:\n%s\n\n" +
                "This invitation will expire on %s.\n\n" +
                "Best regards,\nCollab-Docs Team",
                invitation.getInvitedBy().getFirstName(),
                invitation.getInvitedBy().getLastName(),
                invitation.getDocument().getTitle(),
                invitation.getRole().name().toLowerCase(),
                invitation.getMessage() != null ? "Message: " + invitation.getMessage() : "",
                acceptUrl,
                declineUrl,
                invitation.getExpiresAt()
            );

            emailService.sendEmail(invitation.getInvitedEmail(), subject, body);
        } catch (Exception e) {
            log.error("Failed to send invitation email to {}: {}", invitation.getInvitedEmail(), e.getMessage());
            // Don't fail the transaction if email fails
        }
    }
}


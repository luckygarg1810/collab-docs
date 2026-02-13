package com.project.collab_docs.controller;

import com.project.collab_docs.dto.request.CreateShareLinkRequest;
import com.project.collab_docs.dto.request.ShareInvitationRequest;
import com.project.collab_docs.dto.response.ShareInvitationResponse;
import com.project.collab_docs.dto.response.ShareLinkResponse;
import com.project.collab_docs.security.CustomUserDetails;
import com.project.collab_docs.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for document sharing functionality.
 * Handles both share links and email invitations.
 *
 * Features:
 * - Create and manage shareable links
 * - Send and manage email invitations
 * - Validate and access documents via share links
 * - Accept/decline invitations
 *
 * Security:
 * - JWT authentication required for most endpoints
 * - Permission checks enforced at service layer
 * - Public endpoints for link validation and access
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Sharing", description = "Document sharing via links and invitations")
@Slf4j
public class ShareController {

    @Autowired
    private ShareService shareService;

    /**
     * Create a new shareable link for a document
     */
    @PostMapping("/documents/{documentId}/share-links")
    @Operation(
        summary = "Create share link",
        description = "Generate a shareable link for a document with specified permissions and expiration",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Share link created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request (e.g., trying to grant OWNER role)"),
        @ApiResponse(responseCode = "403", description = "User lacks permission to share document"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<ShareLinkResponse> createShareLink(
            @Parameter(description = "Document ID") @PathVariable Long documentId,
            @Valid @RequestBody CreateShareLinkRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("Creating share link for document {} by user {}", documentId, userDetails.getId());
        ShareLinkResponse response = shareService.createShareLink(documentId, request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all share links for a document
     */
    @GetMapping("/documents/{documentId}/share-links")
    @Operation(
        summary = "List share links",
        description = "Get all share links for a document (requires at least VIEWER permission)",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Share links retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "User lacks access to document"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<List<ShareLinkResponse>> getDocumentShareLinks(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<ShareLinkResponse> links = activeOnly
            ? shareService.getActiveShareLinks(documentId, userDetails.getId())
            : shareService.getDocumentShareLinks(documentId, userDetails.getId());

        return ResponseEntity.ok(links);
    }

    /**
     * Validate a share link (public endpoint)
     */
    @GetMapping("/share/{token}/validate")
    @Operation(
        summary = "Validate share link",
        description = "Check if a share link is valid and get basic information (public endpoint)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Share link is valid"),
        @ApiResponse(responseCode = "400", description = "Share link is invalid, expired, or usage limit reached"),
        @ApiResponse(responseCode = "404", description = "Share link not found")
    })
    public ResponseEntity<ShareLinkResponse> validateShareLink(
            @Parameter(description = "Share link token") @PathVariable String token,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        ShareLinkResponse response = shareService.validateShareLink(token, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Access a document via share link (requires authentication if link requires auth)
     */
    @PostMapping("/share/{token}/access")
    @Operation(
        summary = "Access via share link",
        description = "Grant access to document via share link (authentication may be required)",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Access granted successfully"),
        @ApiResponse(responseCode = "400", description = "Share link is invalid or requires authentication"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "404", description = "Share link not found")
    })
    public ResponseEntity<Map<String, Object>> accessViaShareLink(
            @PathVariable String token,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        shareService.accessViaShareLink(token, userDetails.getId());

        return ResponseEntity.ok(Map.of(
            "message", "Access granted successfully",
            "userId", userDetails.getId()
        ));
    }

    /**
     * Revoke (deactivate) a share link
     */
    @PostMapping("/share-links/{linkId}/revoke")
    @Operation(
        summary = "Revoke share link",
        description = "Deactivate a share link (must be creator or document owner)",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Share link revoked successfully"),
        @ApiResponse(responseCode = "403", description = "User lacks permission to revoke link"),
        @ApiResponse(responseCode = "404", description = "Share link not found")
    })
    public ResponseEntity<Map<String, String>> revokeShareLink(
            @PathVariable Long linkId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        shareService.revokeShareLink(linkId, userDetails.getId());
        return ResponseEntity.ok(Map.of("message", "Share link revoked successfully"));
    }

    /**
     * Delete a share link permanently
     */
    @DeleteMapping("/share-links/{linkId}")
    @Operation(
        summary = "Delete share link",
        description = "Permanently delete a share link (must be creator or document owner)",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Share link deleted successfully"),
        @ApiResponse(responseCode = "403", description = "User lacks permission to delete link"),
        @ApiResponse(responseCode = "404", description = "Share link not found")
    })
    public ResponseEntity<Void> deleteShareLink(
            @PathVariable Long linkId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        shareService.deleteShareLink(linkId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    // ==================== Email Invitation Endpoints ====================

    /**
     * Send an email invitation to collaborate on a document
     */
    @PostMapping("/documents/{documentId}/invitations")
    @Operation(
        summary = "Send invitation",
        description = "Invite a user via email to collaborate on a document",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Invitation sent successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or duplicate invitation"),
        @ApiResponse(responseCode = "403", description = "User lacks permission to share document"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<ShareInvitationResponse> sendInvitation(
            @PathVariable Long documentId,
            @Valid @RequestBody ShareInvitationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("Sending invitation to {} for document {} by user {}",
            request.getEmail(), documentId, userDetails.getId());

        ShareInvitationResponse response = shareService.sendInvitation(
            documentId, request, userDetails.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all invitations for a document
     */
    @GetMapping("/documents/{documentId}/invitations")
    @Operation(
        summary = "List document invitations",
        description = "Get all invitations for a document (requires at least VIEWER permission)",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invitations retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "User lacks access to document"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<List<ShareInvitationResponse>> getDocumentInvitations(
            @PathVariable Long documentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<ShareInvitationResponse> invitations =
            shareService.getDocumentInvitations(documentId, userDetails.getId());

        return ResponseEntity.ok(invitations);
    }

    /**
     * Get pending invitations for the authenticated user
     */
    @GetMapping("/invitations/pending")
    @Operation(
        summary = "Get pending invitations",
        description = "Get all pending invitations for the authenticated user's email",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pending invitations retrieved successfully")
    })
    public ResponseEntity<List<ShareInvitationResponse>> getPendingInvitations(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<ShareInvitationResponse> invitations =
            shareService.getPendingInvitations(userDetails.getEmail());

        return ResponseEntity.ok(invitations);
    }

    /**
     * Accept an invitation
     */
    @PostMapping("/invitations/{token}/accept")
    @Operation(
        summary = "Accept invitation",
        description = "Accept an invitation to collaborate on a document",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invitation accepted successfully"),
        @ApiResponse(responseCode = "400", description = "Invitation is invalid or expired"),
        @ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    public ResponseEntity<ShareInvitationResponse> acceptInvitation(
            @PathVariable String token,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("User {} accepting invitation with token {}", userDetails.getId(), token);
        ShareInvitationResponse response = shareService.acceptInvitation(token, userDetails.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Decline an invitation
     */
    @PostMapping("/invitations/{token}/decline")
    @Operation(
        summary = "Decline invitation",
        description = "Decline an invitation to collaborate on a document",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invitation declined successfully"),
        @ApiResponse(responseCode = "400", description = "Invitation is invalid"),
        @ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    public ResponseEntity<Map<String, String>> declineInvitation(
            @PathVariable String token,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        shareService.declineInvitation(token, userDetails.getId());
        return ResponseEntity.ok(Map.of("message", "Invitation declined"));
    }

    /**
     * Revoke an invitation
     */
    @DeleteMapping("/invitations/{invitationId}")
    @Operation(
        summary = "Revoke invitation",
        description = "Revoke a pending invitation (must be sender or document owner)",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Invitation revoked successfully"),
        @ApiResponse(responseCode = "403", description = "User lacks permission to revoke invitation"),
        @ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    public ResponseEntity<Void> revokeInvitation(
            @PathVariable Long invitationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        shareService.revokeInvitation(invitationId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}


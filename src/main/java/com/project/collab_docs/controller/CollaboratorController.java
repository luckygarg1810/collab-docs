package com.project.collab_docs.controller;

import com.project.collab_docs.dto.CollaboratorRequest;
import com.project.collab_docs.dto.CollaboratorResponse;
import com.project.collab_docs.dto.UpdateRoleRequest;
import com.project.collab_docs.response.MessageResponse;
import com.project.collab_docs.security.CustomUserDetails;
import com.project.collab_docs.service.PermissionService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST Controller for managing document collaborators and permissions
 */
@RestController
@RequestMapping("/api/documents/{documentId}/collaborators")
@Slf4j
public class CollaboratorController {

    @Autowired
    private PermissionService permissionService;

    /**
     * Add a collaborator to a document
     * Only document OWNER can add collaborators
     */
    @PostMapping
    public ResponseEntity<MessageResponse> addCollaborator(
            @PathVariable Long documentId,
            @RequestBody @Valid CollaboratorRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        log.info("User {} adding collaborator {} with role {} to document {}",
                currentUser.getUser().getId(), request.getUserId(), request.getRole(), documentId);

        LocalDateTime expiresAt = null;
        if (request.getExpiresInDays() != null && request.getExpiresInDays() > 0) {
            expiresAt = LocalDateTime.now().plusDays(request.getExpiresInDays());
        }

        permissionService.grantPermission(
                documentId,
                request.getUserId(),
                request.getRole(),
                currentUser.getUser().getId(),
                expiresAt);

        return ResponseEntity.ok(new MessageResponse("Collaborator added successfully!"));
    }

    /**
     * List all collaborators for a document
     * Any user with access can view collaborators
     */
    @GetMapping
    public ResponseEntity<List<CollaboratorResponse>> getCollaborators(
            @PathVariable Long documentId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        log.info("User {} fetching collaborators for document {}",
                currentUser.getUser().getId(), documentId);

        // Check if user has at least VIEWER access before showing collaborators
        permissionService.hasPermission(documentId, currentUser.getUser().getId(),
                com.project.collab_docs.enums.Role.VIEWER);

        List<CollaboratorResponse> collaborators = permissionService.getCollaborators(documentId);
        return ResponseEntity.ok(collaborators);
    }

    /**
     * Update a collaborator's role
     * Only document OWNER can update roles
     */
    @PutMapping("/{userId}")
    public ResponseEntity<MessageResponse> updateCollaborator(
            @PathVariable Long documentId,
            @PathVariable Long userId,
            @RequestBody @Valid UpdateRoleRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        log.info("User {} updating collaborator {} role to {} for document {}",
                currentUser.getUser().getId(), userId, request.getRole(), documentId);

        permissionService.updatePermission(
                documentId,
                userId,
                request.getRole(),
                currentUser.getUser().getId());

        return ResponseEntity.ok(new MessageResponse("Collaborator role updated successfully!"));
    }

    /**
     * Remove a collaborator from a document
     * Only document OWNER can remove collaborators
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<MessageResponse> removeCollaborator(
            @PathVariable Long documentId,
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        log.info("User {} removing collaborator {} from document {}",
                currentUser.getUser().getId(), userId, documentId);

        permissionService.revokePermission(
                documentId,
                userId,
                currentUser.getUser().getId());

        return ResponseEntity.ok(new MessageResponse("Collaborator removed successfully!"));
    }
}

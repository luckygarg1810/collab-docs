package com.project.collab_docs.service;

import com.project.collab_docs.dto.response.CollaboratorResponse;
import com.project.collab_docs.entities.Document;
import com.project.collab_docs.entities.DocumentPermission;
import com.project.collab_docs.entities.User;
import com.project.collab_docs.enums.Role;
import com.project.collab_docs.exception.DuplicatePermissionException;
import com.project.collab_docs.exception.PermissionDeniedException;
import com.project.collab_docs.exception.ResourceNotFoundException;
import com.project.collab_docs.repository.DocumentPermissionRepository;
import com.project.collab_docs.repository.DocumentRepository;
import com.project.collab_docs.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing document permissions (RBAC)
 */
@Service
@Slf4j
public class PermissionService {

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Check if user has at least the required permission level for a document.
     * Uses role hierarchy: OWNER > EDITOR > VIEWER
     * 
     * @param documentId   Document to check
     * @param userId       User to check
     * @param requiredRole Minimum required role
     * @return true if user has sufficient permissions
     */
    public boolean hasPermission(Long documentId, Long userId, Role requiredRole) {
        return permissionRepository.findByDocumentIdAndUserId(documentId, userId)
                .map(permission -> permission.hasPermissionOf(requiredRole))
                .orElse(false);
    }

    /**
     * Get user's effective role for a document.
     * 
     * @param documentId Document to check
     * @param userId     User to check
     * @return User's role, or null if no access
     */
    public Role getEffectiveRole(Long documentId, Long userId) {
        return permissionRepository.findByDocumentIdAndUserId(documentId, userId)
                .filter(DocumentPermission::isValid)
                .map(DocumentPermission::getRole)
                .orElse(null);
    }

    /**
     * Check if user is the owner of a document
     */
    public boolean isOwner(Long documentId, Long userId) {
        return permissionRepository.isOwner(documentId, userId);
    }

    /**
     * Grant permission to a user.
     * Only document owners can grant permissions.
     * 
     * @param documentId      Document to grant access to
     * @param userId          User to grant access to
     * @param role            Role to grant
     * @param grantedByUserId User granting the permission (must be OWNER)
     * @throws PermissionDeniedException    if grantedBy is not OWNER
     * @throws DuplicatePermissionException if permission already exists
     */
    @Transactional
    public void grantPermission(Long documentId, Long userId, Role role, Long grantedByUserId) {
        grantPermission(documentId, userId, role, grantedByUserId, null);
    }

    /**
     * Grant permission with optional expiration
     */
    @Transactional
    public void grantPermission(Long documentId, Long userId, Role role, Long grantedByUserId,
            LocalDateTime expiresAt) {
        log.info("Granting {} permission to user {} for document {} by user {}",
                role, userId, documentId, grantedByUserId);

        // Validate document exists
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Validate granting user exists
        User grantedByUser = userRepository.findById(grantedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Granting user not found"));

        // Validate target user exists
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

        // Check if grantedBy has OWNER permission (unless this is the initial owner
        // grant)
        boolean isInitialOwnerGrant = role == Role.OWNER &&
                permissionRepository.countActiveCollaborators(documentId) == 0;

        if (!isInitialOwnerGrant && !isOwner(documentId, grantedByUserId)) {
            throw new PermissionDeniedException("Only document owners can grant permissions");
        }

        // Check for duplicate permission
        if (permissionRepository.existsByDocumentIdAndUserId(documentId, userId)) {
            throw new DuplicatePermissionException(
                    "User already has permission for this document. Use update instead.");
        }

        // Create permission
        DocumentPermission permission = DocumentPermission.builder()
                .document(document)
                .user(targetUser)
                .role(role)
                .grantedBy(grantedByUser)
                .grantedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .build();

        permissionRepository.save(permission);
        log.info("Permission granted successfully");
    }

    /**
     * Revoke permission from a user.
     * Only document owners can revoke permissions.
     * Cannot revoke OWNER role if they are the last owner.
     * 
     * @param documentId      Document to revoke access from
     * @param userId          User to revoke access from
     * @param revokedByUserId User revoking the permission (must be OWNER)
     * @throws PermissionDeniedException if revokedBy is not OWNER or trying to
     *                                   remove last OWNER
     */
    @Transactional
    public void revokePermission(Long documentId, Long userId, Long revokedByUserId) {
        log.info("Revoking permission from user {} for document {} by user {}",
                userId, documentId, revokedByUserId);

        // Check if revoking user is OWNER
        if (!isOwner(documentId, revokedByUserId)) {
            throw new PermissionDeniedException("Only document owners can revoke permissions");
        }

        // Get permission to revoke
        DocumentPermission permission = permissionRepository.findByDocumentIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));

        // Prevent removing last OWNER
        if (permission.getRole() == Role.OWNER) {
            long ownerCount = permissionRepository.findOwners(documentId).size();
            if (ownerCount <= 1) {
                throw new PermissionDeniedException(
                        "Cannot remove the last owner. Transfer ownership first or delete the document.");
            }
        }

        permissionRepository.deleteByDocumentIdAndUserId(documentId, userId);
        log.info("Permission revoked successfully");
    }

    /**
     * Update user's role.
     * Only document owners can update roles.
     * 
     * @param documentId      Document
     * @param userId          User whose role to update
     * @param newRole         New role
     * @param updatedByUserId User updating the permission (must be OWNER)
     */
    @Transactional
    public void updatePermission(Long documentId, Long userId, Role newRole, Long updatedByUserId) {
        log.info("Updating permission for user {} on document {} to {} by user {}",
                userId, documentId, newRole, updatedByUserId);

        // Check if updating user is OWNER
        if (!isOwner(documentId, updatedByUserId)) {
            throw new PermissionDeniedException("Only document owners can update permissions");
        }

        // Get existing permission
        DocumentPermission permission = permissionRepository.findByDocumentIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));

        // Prevent changing last OWNER role
        if (permission.getRole() == Role.OWNER && newRole != Role.OWNER) {
            long ownerCount = permissionRepository.findOwners(documentId).size();
            if (ownerCount <= 1) {
                throw new PermissionDeniedException(
                        "Cannot change role of the last owner. Add another owner first.");
            }
        }

        permission.setRole(newRole);
        permissionRepository.save(permission);
        log.info("Permission updated successfully");
    }

    /**
     * Get all collaborators for a document
     * 
     * @param documentId Document to get collaborators for
     * @return List of collaborators with their roles
     */
    public List<CollaboratorResponse> getCollaborators(Long documentId) {
        return permissionRepository.findActivePermissionsByDocumentId(documentId)
                .stream()
                .map(this::toCollaboratorResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all documents a user can access (owned + shared)
     * 
     * @param userId User ID
     * @return List of documents with any level of access
     */
    public List<Document> getUserAccessibleDocuments(Long userId) {
        return permissionRepository.findActivePermissionsByUserId(userId)
                .stream()
                .map(DocumentPermission::getDocument)
                .collect(Collectors.toList());
    }

    /**
     * Get all documents where user has a specific role
     */
    public List<Document> getUserDocumentsByRole(Long userId, Role role) {
        return permissionRepository.findActivePermissionsByUserId(userId)
                .stream()
                .filter(p -> p.getRole() == role)
                .map(DocumentPermission::getDocument)
                .collect(Collectors.toList());
    }

    /**
     * Get documents which are shared with user
     */
    public List<Document> getSharedDocumentsForUser(Long userId) {
        return permissionRepository.findActivePermissionsByUserId(userId)
                .stream()
                .filter(p -> p.getRole() != Role.OWNER)
                .map(DocumentPermission::getDocument)
                .collect(Collectors.toList());
    }

    /**
     * Convert DocumentPermission to CollaboratorResponse DTO
     */
    private CollaboratorResponse toCollaboratorResponse(DocumentPermission permission) {
        User user = permission.getUser();
        User grantedBy = permission.getGrantedBy();

        return CollaboratorResponse.builder()
                .id(permission.getId())
                .userId(user.getId())
                .userEmail(user.getEmail())
                .userName(user.getFirstName() + " " + user.getLastName())
                .role(permission.getRole())
                .grantedAt(permission.getGrantedAt())
                .grantedByName(grantedBy.getFirstName() + " " + grantedBy.getLastName())
                .expiresAt(permission.getExpiresAt())
                .isExpired(!permission.isValid())
                .build();
    }
}

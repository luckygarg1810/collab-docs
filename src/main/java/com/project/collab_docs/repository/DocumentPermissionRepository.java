package com.project.collab_docs.repository;

import com.project.collab_docs.entities.DocumentPermission;
import com.project.collab_docs.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing document permissions (RBAC).
 */
@Repository
public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, Long> {

    /**
     * Find user's permission for a specific document
     */
    Optional<DocumentPermission> findByDocumentIdAndUserId(Long documentId, Long userId);

    /**
     * Check if user has any permission for document
     */
    boolean existsByDocumentIdAndUserId(Long documentId, Long userId);

    /**
     * Get all active (non-expired) permissions for a document
     */
    @Query("SELECT p FROM DocumentPermission p WHERE p.document.id = :documentId " +
            "AND (p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP)")
    List<DocumentPermission> findActivePermissionsByDocumentId(@Param("documentId") Long documentId);

    /**
     * Get all documents a user has access to
     */
    @Query("SELECT p FROM DocumentPermission p WHERE p.user.id = :userId " +
            "AND p.document.isDeleted = false " +
            "AND (p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP)")
    List<DocumentPermission> findActivePermissionsByUserId(@Param("userId") Long userId);

    /**
     * Get all users with a specific role for a document
     */
    @Query("SELECT p FROM DocumentPermission p WHERE p.document.id = :documentId " +
            "AND p.role = :role " +
            "AND (p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP)")
    List<DocumentPermission> findByDocumentIdAndRole(
            @Param("documentId") Long documentId,
            @Param("role") Role role);

    /**
     * Delete permission (revoke access)
     */
    void deleteByDocumentIdAndUserId(Long documentId, Long userId);

    /**
     * Check if user is the owner of a document
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
            "FROM DocumentPermission p WHERE p.document.id = :documentId " +
            "AND p.user.id = :userId AND p.role = 'OWNER' " +
            "AND (p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP)")
    boolean isOwner(@Param("documentId") Long documentId, @Param("userId") Long userId);

    /**
     * Count all collaborators for a document (excluding expired)
     */
    @Query("SELECT COUNT(p) FROM DocumentPermission p WHERE p.document.id = :documentId " +
            "AND (p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP)")
    long countActiveCollaborators(@Param("documentId") Long documentId);

    /**
     * Find all OWNER permissions for a document
     */
    @Query("SELECT p FROM DocumentPermission p WHERE p.document.id = :documentId " +
            "AND p.role = 'OWNER' " +
            "AND (p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP)")
    List<DocumentPermission> findOwners(@Param("documentId") Long documentId);
}

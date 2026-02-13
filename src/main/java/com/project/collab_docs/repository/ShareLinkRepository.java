package com.project.collab_docs.repository;

import com.project.collab_docs.entities.ShareLink;
import com.project.collab_docs.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing share links.
 */
@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

    /**
     * Find a share link by its unique token
     */
    Optional<ShareLink> findByToken(String token);

    /**
     * Check if a token exists
     */
    boolean existsByToken(String token);

    /**
     * Get all active share links for a document
     */
    @Query("SELECT sl FROM ShareLink sl WHERE sl.document.id = :documentId " +
           "AND sl.isActive = true " +
           "AND (sl.expiresAt IS NULL OR sl.expiresAt > CURRENT_TIMESTAMP)")
    List<ShareLink> findActiveByDocumentId(@Param("documentId") Long documentId);

    /**
     * Get all share links for a document (including inactive/expired)
     */
    List<ShareLink> findByDocumentId(Long documentId);

    /**
     * Get all share links created by a user
     */
    List<ShareLink> findByCreatedById(Long userId);

    /**
     * Count active share links for a document
     */
    @Query("SELECT COUNT(sl) FROM ShareLink sl WHERE sl.document.id = :documentId " +
           "AND sl.isActive = true " +
           "AND (sl.expiresAt IS NULL OR sl.expiresAt > CURRENT_TIMESTAMP)")
    long countActiveByDocumentId(@Param("documentId") Long documentId);

    /**
     * Find expired share links that are still marked as active
     * (for cleanup job)
     */
    @Query("SELECT sl FROM ShareLink sl WHERE sl.isActive = true " +
           "AND sl.expiresAt IS NOT NULL " +
           "AND sl.expiresAt < CURRENT_TIMESTAMP")
    List<ShareLink> findExpiredLinks();

    /**
     * Find links that have reached their usage limit but are still active
     * (for cleanup job)
     */
    @Query("SELECT sl FROM ShareLink sl WHERE sl.isActive = true " +
           "AND sl.maxUses IS NOT NULL " +
           "AND sl.currentUses >= sl.maxUses")
    List<ShareLink> findUsageLimitReachedLinks();

    /**
     * Find valid (active, not expired, not usage-limited) share links for a document
     */
    @Query("SELECT sl FROM ShareLink sl WHERE sl.document.id = :documentId " +
           "AND sl.isActive = true " +
           "AND (sl.expiresAt IS NULL OR sl.expiresAt > CURRENT_TIMESTAMP) " +
           "AND (sl.maxUses IS NULL OR sl.currentUses < sl.maxUses)")
    List<ShareLink> findValidByDocumentId(@Param("documentId") Long documentId);

    /**
     * Find share links by document and role
     */
    @Query("SELECT sl FROM ShareLink sl WHERE sl.document.id = :documentId " +
           "AND sl.role = :role " +
           "AND sl.isActive = true")
    List<ShareLink> findByDocumentIdAndRole(
        @Param("documentId") Long documentId,
        @Param("role") Role role
    );

    /**
     * Deactivate all share links for a document
     */
    @Modifying
    @Query("UPDATE ShareLink sl SET sl.isActive = false WHERE sl.document.id = :documentId")
    void deactivateAllByDocumentId(@Param("documentId") Long documentId);

    /**
     * Delete old inactive share links (cleanup)
     */
    @Modifying
    @Query("DELETE FROM ShareLink sl WHERE sl.isActive = false " +
           "AND sl.createdAt < :cutoffDate")
    void deleteOldInactiveLinks(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find share links that will expire soon (for notification)
     */
    @Query("SELECT sl FROM ShareLink sl WHERE sl.isActive = true " +
           "AND sl.expiresAt IS NOT NULL " +
           "AND sl.expiresAt BETWEEN CURRENT_TIMESTAMP AND :notificationDate")
    List<ShareLink> findExpiringLinks(@Param("notificationDate") LocalDateTime notificationDate);

    /**
     * Get statistics for a user's share links
     */
    @Query("SELECT COUNT(sl), SUM(sl.currentUses) FROM ShareLink sl " +
           "WHERE sl.createdBy.id = :userId")
    Object[] getShareLinkStats(@Param("userId") Long userId);

    /**
     * Find share links by document ID and created by user
     */
    List<ShareLink> findByDocumentIdAndCreatedById(Long documentId, Long userId);

    /**
     * Check if a specific share link belongs to a document
     */
    boolean existsByIdAndDocumentId(Long linkId, Long documentId);
}


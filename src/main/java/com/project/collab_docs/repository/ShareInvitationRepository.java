package com.project.collab_docs.repository;

import com.project.collab_docs.entities.ShareInvitation;
import com.project.collab_docs.enums.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing share invitations.
 */
@Repository
public interface ShareInvitationRepository extends JpaRepository<ShareInvitation, Long> {

    /**
     * Find invitation by unique token
     */
    Optional<ShareInvitation> findByToken(String token);

    /**
     * Check if token exists
     */
    boolean existsByToken(String token);

    /**
     * Find pending invitation for a specific email and document
     */
    @Query("SELECT si FROM ShareInvitation si WHERE si.invitedEmail = :email " +
           "AND si.document.id = :documentId " +
           "AND si.status = 'PENDING' " +
           "AND (si.expiresAt IS NULL OR si.expiresAt > CURRENT_TIMESTAMP)")
    Optional<ShareInvitation> findPendingByEmailAndDocumentId(
        @Param("email") String email,
        @Param("documentId") Long documentId
    );

    /**
     * Get all invitations for a document
     */
    List<ShareInvitation> findByDocumentId(Long documentId);

    /**
     * Get all pending invitations for a document
     */
    @Query("SELECT si FROM ShareInvitation si WHERE si.document.id = :documentId " +
           "AND si.status = 'PENDING'")
    List<ShareInvitation> findPendingByDocumentId(@Param("documentId") Long documentId);

    /**
     * Get all invitations sent to a specific email
     */
    List<ShareInvitation> findByInvitedEmail(String email);

    /**
     * Get all pending invitations for an email
     */
    @Query("SELECT si FROM ShareInvitation si WHERE si.invitedEmail = :email " +
           "AND si.status = 'PENDING' " +
           "AND (si.expiresAt IS NULL OR si.expiresAt > CURRENT_TIMESTAMP)")
    List<ShareInvitation> findPendingByEmail(@Param("email") String email);

    /**
     * Get all invitations sent to a registered user
     */
    List<ShareInvitation> findByInvitedUserId(Long userId);

    /**
     * Get all invitations sent by a user
     */
    List<ShareInvitation> findByInvitedById(Long userId);

    /**
     * Get invitations by status
     */
    List<ShareInvitation> findByStatus(InvitationStatus status);

    /**
     * Find expired invitations that are still marked as PENDING
     * (for cleanup job)
     */
    @Query("SELECT si FROM ShareInvitation si WHERE si.status = 'PENDING' " +
           "AND si.expiresAt IS NOT NULL " +
           "AND si.expiresAt < CURRENT_TIMESTAMP")
    List<ShareInvitation> findExpiredInvitations();

    /**
     * Count pending invitations for a document
     */
    @Query("SELECT COUNT(si) FROM ShareInvitation si WHERE si.document.id = :documentId " +
           "AND si.status = 'PENDING'")
    long countPendingByDocumentId(@Param("documentId") Long documentId);

    /**
     * Count invitations sent by a user
     */
    @Query("SELECT COUNT(si) FROM ShareInvitation si WHERE si.invitedBy.id = :userId")
    long countByInvitedById(@Param("userId") Long userId);

    /**
     * Check if an email already has a pending invitation for a document
     */
    @Query("SELECT CASE WHEN COUNT(si) > 0 THEN true ELSE false END FROM ShareInvitation si " +
           "WHERE si.invitedEmail = :email " +
           "AND si.document.id = :documentId " +
           "AND si.status = 'PENDING'")
    boolean existsPendingInvitation(
        @Param("email") String email,
        @Param("documentId") Long documentId
    );

    /**
     * Update expired invitations status
     */
    @Modifying
    @Query("UPDATE ShareInvitation si SET si.status = 'EXPIRED', si.respondedAt = CURRENT_TIMESTAMP " +
           "WHERE si.status = 'PENDING' " +
           "AND si.expiresAt IS NOT NULL " +
           "AND si.expiresAt < CURRENT_TIMESTAMP")
    int markExpiredInvitations();

    /**
     * Revoke all pending invitations for a document
     */
    @Modifying
    @Query("UPDATE ShareInvitation si SET si.status = 'REVOKED', si.respondedAt = CURRENT_TIMESTAMP " +
           "WHERE si.document.id = :documentId " +
           "AND si.status = 'PENDING'")
    void revokeAllPendingByDocumentId(@Param("documentId") Long documentId);

    /**
     * Delete old processed invitations (cleanup)
     */
    @Modifying
    @Query("DELETE FROM ShareInvitation si WHERE si.status IN ('ACCEPTED', 'DECLINED', 'EXPIRED', 'REVOKED') " +
           "AND si.respondedAt < :cutoffDate")
    void deleteOldInvitations(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find invitations expiring soon (for reminder notifications)
     */
    @Query("SELECT si FROM ShareInvitation si WHERE si.status = 'PENDING' " +
           "AND si.expiresAt IS NOT NULL " +
           "AND si.expiresAt BETWEEN CURRENT_TIMESTAMP AND :notificationDate")
    List<ShareInvitation> findExpiringInvitations(@Param("notificationDate") LocalDateTime notificationDate);

    /**
     * Get invitation statistics for a user
     */
    @Query("SELECT si.status, COUNT(si) FROM ShareInvitation si " +
           "WHERE si.invitedBy.id = :userId " +
           "GROUP BY si.status")
    List<Object[]> getInvitationStatsByUser(@Param("userId") Long userId);

    /**
     * Find invitations for a document by invited email (for checking duplicates)
     */
    List<ShareInvitation> findByDocumentIdAndInvitedEmail(Long documentId, String invitedEmail);

    /**
     * Check if an invitation belongs to a specific document
     */
    boolean existsByIdAndDocumentId(Long invitationId, Long documentId);
}


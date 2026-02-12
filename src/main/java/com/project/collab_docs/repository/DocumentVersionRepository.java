package com.project.collab_docs.repository;

import com.project.collab_docs.entities.Document;
import com.project.collab_docs.entities.DocumentVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for DocumentVersion entity.
 * Provides efficient queries for version history and management.
 */
@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    /**
     * Find all versions for a document, ordered by version number descending (newest first)
     */
    List<DocumentVersion> findByDocumentOrderByVersionNumberDesc(Document document);

    /**
     * Find all versions for a document with pagination
     */
    Page<DocumentVersion> findByDocumentOrderByVersionNumberDesc(Document document, Pageable pageable);

    /**
     * Find versions by document ID
     */
    @Query("SELECT v FROM DocumentVersion v WHERE v.document.id = :documentId ORDER BY v.versionNumber DESC")
    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(@Param("documentId") Long documentId);

    /**
     * Find versions by document ID with pagination
     */
    @Query("SELECT v FROM DocumentVersion v WHERE v.document.id = :documentId ORDER BY v.versionNumber DESC")
    Page<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(@Param("documentId") Long documentId, Pageable pageable);

    /**
     * Get the latest version for a document
     */
    Optional<DocumentVersion> findTopByDocumentOrderByVersionNumberDesc(Document document);

    /**
     * Get the latest version by document ID
     */
    @Query("SELECT v FROM DocumentVersion v WHERE v.document.id = :documentId ORDER BY v.versionNumber DESC LIMIT 1")
    Optional<DocumentVersion> findLatestByDocumentId(@Param("documentId") Long documentId);

    /**
     * Count total versions for a document
     */
    long countByDocument(Document document);

    /**
     * Count versions by document ID
     */
    @Query("SELECT COUNT(v) FROM DocumentVersion v WHERE v.document.id = :documentId")
    long countByDocumentId(@Param("documentId") Long documentId);

    /**
     * Get the next version number for a document
     */
    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) + 1 FROM DocumentVersion v WHERE v.document.id = :documentId")
    Integer getNextVersionNumber(@Param("documentId") Long documentId);

    /**
     * Find versions created by a specific user
     */
    @Query("SELECT v FROM DocumentVersion v WHERE v.document.id = :documentId AND v.createdBy.id = :userId ORDER BY v.versionNumber DESC")
    List<DocumentVersion> findByDocumentIdAndCreatedByUserId(@Param("documentId") Long documentId, @Param("userId") Long userId);

    /**
     * Find versions created within a date range
     */
    @Query("SELECT v FROM DocumentVersion v WHERE v.document.id = :documentId AND v.createdAt BETWEEN :startDate AND :endDate ORDER BY v.versionNumber DESC")
    List<DocumentVersion> findByDocumentIdAndCreatedAtBetween(
        @Param("documentId") Long documentId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Calculate total storage used by versions for a document
     */
    @Query("SELECT COALESCE(SUM(v.sizeBytes), 0) FROM DocumentVersion v WHERE v.document.id = :documentId")
    Long getTotalStorageByDocumentId(@Param("documentId") Long documentId);

    /**
     * Find a specific version by document ID and version number
     */
    @Query("SELECT v FROM DocumentVersion v WHERE v.document.id = :documentId AND v.versionNumber = :versionNumber")
    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(@Param("documentId") Long documentId, @Param("versionNumber") Integer versionNumber);

    /**
     * Delete old versions keeping only the N most recent
     * Used for cleanup/quota management
     */
    @Query("SELECT v.id FROM DocumentVersion v WHERE v.document.id = :documentId ORDER BY v.versionNumber DESC")
    List<Long> findVersionIdsForCleanup(@Param("documentId") Long documentId, Pageable pageable);

    /**
     * Check if a version exists by ID and document ID (for security checks)
     */
    @Query("SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END FROM DocumentVersion v WHERE v.id = :versionId AND v.document.id = :documentId")
    boolean existsByIdAndDocumentId(@Param("versionId") Long versionId, @Param("documentId") Long documentId);
}


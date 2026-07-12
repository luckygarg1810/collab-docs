package com.project.collab_docs.repository;

import com.project.collab_docs.entities.RecentDocumentView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecentDocumentViewRepository extends JpaRepository<RecentDocumentView, Long> {

    /**
     * Atomic upsert: insert a new view, or bump last_opened_at if the user has
     * already opened this document before. Done as a native query rather than
     * find-then-save to avoid a race between two rapid opens both missing the
     * find and violating the unique constraint on insert.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO recent_document_views (user_id, document_id, last_opened_at)
            VALUES (:userId, :documentId, now())
            ON CONFLICT (user_id, document_id) DO UPDATE SET last_opened_at = now()
            """, nativeQuery = true)
    void recordOpen(@Param("userId") Long userId, @Param("documentId") Long documentId);

    List<RecentDocumentView> findTop10ByUserIdOrderByLastOpenedAtDesc(Long userId);

    Optional<RecentDocumentView> findByUserIdAndDocumentId(Long userId, Long documentId);

    /**
     * Delete every recent-view row for a document. Used by the Recycle Bin
     * hard-delete cascade — must run before deleting the document row.
     */
    @Transactional
    void deleteByDocumentId(Long documentId);
}

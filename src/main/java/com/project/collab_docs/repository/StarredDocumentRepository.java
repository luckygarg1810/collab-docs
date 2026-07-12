package com.project.collab_docs.repository;

import com.project.collab_docs.entities.Document;
import com.project.collab_docs.entities.StarredDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface StarredDocumentRepository extends JpaRepository<StarredDocument, Long> {

    boolean existsByDocumentIdAndUserId(Long documentId, Long userId);

    @Transactional
    void deleteByDocumentIdAndUserId(Long documentId, Long userId);

    /**
     * Delete every star for a document. Used by the Recycle Bin hard-delete
     * cascade — must run before deleting the document row.
     */
    @Transactional
    void deleteByDocumentId(Long documentId);

    @Query("SELECT s.document FROM StarredDocument s WHERE s.user.id = :userId " +
            "AND s.document.isDeleted = false")
    List<Document> findStarredDocumentsByUserId(@Param("userId") Long userId);
}

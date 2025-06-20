package com.project.collab_docs.repository;

import com.project.collab_docs.entities.Document;
import com.project.collab_docs.entities.User;
import com.project.collab_docs.enums.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    // Find non-deleted documents by owner
    List<Document> findByOwnerAndIsDeletedFalseOrderByUpdatedAtDesc(User owner);

    // Find by Yjs room ID (non-deleted)
    Optional<Document> findByYjsRoomIdAndIsDeletedFalse(String yjsRoomId);

    // Search documents by title (non-deleted)
    @Query("SELECT d FROM Document d WHERE d.owner = :owner AND d.title LIKE %:title% AND d.isDeleted = false")
    List<Document> findByOwnerAndTitleContaining(@Param("owner") User owner, @Param("title") String title);

    // Check if Yjs room ID exists (including deleted documents)
    boolean existsByYjsRoomId(String yjsRoomId);

    // Find documents by visibility
    List<Document> findByVisibilityAndIsDeletedFalseOrderByUpdatedAtDesc(Visibility visibility);

    // Find shared documents that user can access
    @Query("SELECT d FROM Document d WHERE d.visibility IN ('public', 'shared') AND d.isDeleted = false ORDER BY d.updatedAt DESC")
    List<Document> findSharedDocuments();

    // Find document by ID (non-deleted)
    Optional<Document> findByIdAndIsDeletedFalse(Long id);
}

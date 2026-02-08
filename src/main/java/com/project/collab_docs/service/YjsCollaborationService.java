package com.project.collab_docs.service;

import com.project.collab_docs.entities.Document;
import com.project.collab_docs.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simplified collaboration service for PostgreSQL snapshot persistence
 * All real-time Yjs operations are now handled by the Node.js microservice
 * This service only handles saving/loading snapshots from the database
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class YjsCollaborationService {

    private final DocumentRepository documentRepository;

    /**
     * Save Yjs snapshot to PostgreSQL database
     * Called by Node.js service periodically for durability
     */
    @Transactional
    public void saveYjsSnapshot(String documentId, byte[] snapshot) {
        try {
            if (snapshot == null || snapshot.length == 0) {
                log.warn("Attempted to save empty snapshot for document: {}", documentId);
                return;
            }

            // Find document and save snapshot
            Document document = documentRepository.findByYjsRoomIdAndIsDeletedFalse(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

            document.setYjsSnapshot(snapshot);
            documentRepository.save(document);

            log.info("Saved Yjs snapshot for document {}: {} bytes", documentId, snapshot.length);

        } catch (Exception e) {
            log.error("Error saving Yjs snapshot for document {}: {}", documentId, e.getMessage(), e);
            throw new RuntimeException("Failed to save Yjs snapshot", e);
        }
    }

    /**
     * Get Yjs snapshot from PostgreSQL database
     * Used by Node.js service on startup to restore document state
     */
    public byte[] getYjsSnapshot(String documentId) {
        try {
            return documentRepository.findByYjsRoomIdAndIsDeletedFalse(documentId)
                    .map(Document::getYjsSnapshot)
                    .orElse(null);

        } catch (Exception e) {
            log.error("Error retrieving Yjs snapshot for document {}: {}", documentId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Check if a document exists
     */
    public boolean documentExists(String documentId) {
        try {
            return documentRepository.findByYjsRoomIdAndIsDeletedFalse(documentId).isPresent();
        } catch (Exception e) {
            log.error("Error checking if document exists: {}", e.getMessage(), e);
            return false;
        }
    }
}

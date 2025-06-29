package com.project.collab_docs.service;

import com.project.collab_docs.entities.Document;
import com.project.collab_docs.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class YjsCollaborationService {

    private final DocumentRepository documentRepository;
    private final RedisTemplate<String, byte[]> redisTemplate;

    // In-memory cache for frequently accessed document states
    private final ConcurrentHashMap<String, byte[]> documentStateCache = new ConcurrentHashMap<>();

    // Redis key patterns
    private static final String YJS_UPDATE_STREAM_PREFIX = "yjs:updates:";
    private static final String YJS_STATE_KEY_PREFIX = "yjs:state:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /**
     * Process incoming Yjs update from a client
     * This method handles the core Yjs update flow:
     * 1. Store update in Redis stream for durability
     * 2. Update in-memory state cache
     * 3. Periodically persist snapshots to database
     */
    @Transactional
    public void processYjsUpdate(String documentId, byte[] updateData) {
        try{
            if (updateData == null || updateData.length == 0) {
                log.warn("Received empty Yjs update for document: {}", documentId);
                return;
            }

            // Validate document exists
            if (!documentExists(documentId)) {
                log.warn("Received update for non-existent document: {}", documentId);
                return;
            }

            // Store update in Redis stream for durability and potential replay
            String streamKey = YJS_UPDATE_STREAM_PREFIX + documentId;
            redisTemplate.opsForStream().add(streamKey, java.util.Map.of("update", updateData));

            // Set TTL on the stream (e.g., keep updates for 7 days)
            redisTemplate.expire(streamKey, Duration.ofDays(7));

            // Update the current document state in Redis cache
            String stateKey = YJS_STATE_KEY_PREFIX + documentId;
            byte[] currentState = redisTemplate.opsForValue().get(stateKey);

            // Apply the update to current state (simplified - in real Yjs this would be proper CRDT merge)
            byte[] newState = mergeYjsUpdate(currentState, updateData);
            redisTemplate.opsForValue().set(stateKey, newState, CACHE_TTL);

            // Update in-memory cache for fast access
            documentStateCache.put(documentId, newState);
            log.debug("Processed Yjs update for document {}: {} bytes", documentId, updateData.length);

            // TODO: Implement periodic snapshot saving to database
            // This could be done asynchronously or triggered by update count/time
        } catch (Exception e) {
            log.error("Error processing Yjs update for document {}: {}", documentId, e.getMessage(), e);
            throw new RuntimeException("Failed to process Yjs update", e);
        }
    }

    /**
     * Get the current document state for new clients joining
     * This returns the latest consolidated state that new clients can use to sync
     */
    public byte[] getDocumentState(String documentId) {
        try{
            // Check in-memory cache first
            byte[] cachedState = documentStateCache.get(documentId);
            if (cachedState != null) {
                log.debug("Retrieved document state from memory cache: {} bytes", cachedState.length);
                return cachedState;
            }

            // Check Redis cache
            String stateKey = YJS_STATE_KEY_PREFIX + documentId;
            byte[] redisState = redisTemplate.opsForValue().get(stateKey);
            if (redisState != null) {
                // Update memory cache
                documentStateCache.put(documentId, redisState);
                log.debug("Retrieved document state from Redis: {} bytes", redisState.length);
                return redisState;
            }

            // Fall back to database snapshot
            byte[] dbSnapshot = getDocumentSnapshotFromDatabase(documentId);
            if (dbSnapshot != null && dbSnapshot.length > 0) {
                // Cache the snapshot
                redisTemplate.opsForValue().set(stateKey, dbSnapshot, CACHE_TTL);
                documentStateCache.put(documentId, dbSnapshot);
                log.debug("Retrieved document state from database: {} bytes", dbSnapshot.length);
                return dbSnapshot;
            }

            log.debug("No existing state found for document: {}", documentId);
            return new byte[0]; // Empty state for new documents

        }catch (Exception e) {
            log.error("Error retrieving document state for {}: {}", documentId, e.getMessage(), e);
            return new byte[0]; // Return empty state on error
        }
    }

    /**
     * Save a Yjs snapshot to the database for long-term persistence
     * This is typically called periodically or when significant changes accumulate
     */
    @Transactional
    public void saveYjsSnapshot(String documentId, byte[] snapshot) {
        try{
            if (snapshot == null || snapshot.length == 0) {
                log.warn("Attempted to save empty snapshot for document: {}", documentId);
                return;
            }

            Document document = documentRepository.findByYjsRoomIdAndIsDeletedFalse(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
            document.setYjsSnapshot(snapshot);
            documentRepository.save(document);

            log.info("Saved Yjs snapshot for document {}: {} bytes", documentId, snapshot.length);

            // Update caches with the new snapshot
            String stateKey = YJS_STATE_KEY_PREFIX + documentId;
            redisTemplate.opsForValue().set(stateKey, snapshot, CACHE_TTL);
            documentStateCache.put(documentId, snapshot);
        }catch (Exception e) {
            log.error("Error saving Yjs snapshot for document {}: {}", documentId, e.getMessage(), e);
            throw new RuntimeException("Failed to save Yjs snapshot", e);
        }
    }

    /**
     * Check if a document exists and is accessible
     */
    private boolean documentExists(String documentId) {
        try {
            return documentRepository.findByYjsRoomIdAndIsDeletedFalse(documentId).isPresent();
        } catch (Exception e) {
            log.error("Error checking if document exists: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get Yjs snapshot from database
     */
    private byte[] getDocumentSnapshotFromDatabase(String documentId) {
        try {
            return documentRepository.findByYjsRoomIdAndIsDeletedFalse(documentId)
                    .map(Document::getYjsSnapshot)
                    .orElse(null);
        } catch (Exception e) {
            log.error("Error retrieving snapshot from database for document {}: {}",
                    documentId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Simplified Yjs update merging
     * In a real implementation, this would use proper Yjs CRDT algorithms
     * For now, we'll implement a basic approach that works for the proof of concept
     */
    private byte[] mergeYjsUpdate(byte[] currentState, byte[] update) {
        // Simplified merge: if no current state, the update becomes the state
        if (currentState == null || currentState.length == 0) {
            return update;
        }

        //TODO: For a proper implementation, you would:
        // 1. Parse both states as Yjs documents
        // 2. Apply the update using Yjs merge algorithms
        // 3. Return the resulting state

        // Placeholder: concatenate for now (NOT production ready)
        // This should be replaced with proper Yjs state merging
        byte[] merged = new byte[currentState.length + update.length];
        System.arraycopy(currentState, 0, merged, 0, currentState.length);
        System.arraycopy(update, 0, merged, currentState.length, update.length);

        return merged;
    }

    /**
     * Clear cached state for a document (useful for cleanup or forced refresh)
     */
    public void clearDocumentCache(String documentId) {
        try {
            documentStateCache.remove(documentId);
            String stateKey = YJS_STATE_KEY_PREFIX + documentId;
            redisTemplate.delete(stateKey);
            log.info("Cleared cache for document: {}", documentId);
        } catch (Exception e) {
            log.error("Error clearing cache for document {}: {}", documentId, e.getMessage(), e);
        }
    }

    /**
     * Get active document count (for monitoring)
     */
    public int getActiveCachedDocumentCount() {
        return documentStateCache.size();
    }
}

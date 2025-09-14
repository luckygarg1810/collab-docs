package com.project.collab_docs.service;

import com.project.collab_docs.entities.Document;
import com.project.collab_docs.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class YjsCollaborationService {

    private final DocumentRepository documentRepository;
    private final RedisTemplate<String, byte[]> redisTemplate;
    private final YjsEnginePool yjsEnginePool;

    // In-memory cache for frequently accessed document states
    private final ConcurrentHashMap<String, byte[]> documentStateCache = new ConcurrentHashMap<>();
    // Track pending updates for batch processing
    private final ConcurrentHashMap<String, List<PendingUpdate>> pendingUpdates = new ConcurrentHashMap<>();

    // Metrics and monitoring
    private final AtomicInteger totalUpdatesProcessed = new AtomicInteger(0);
    private final AtomicInteger totalMergeOperations = new AtomicInteger(0);
    private final AtomicInteger failedOperations = new AtomicInteger(0);


    // Configuration constants
    private static final String YJS_UPDATE_STREAM_PREFIX = "yjs:updates:";
    private static final String YJS_STATE_KEY_PREFIX = "yjs:state:";
    private static final String YJS_LOCK_KEY_PREFIX = "yjs:lock:";
    private static final String YJS_SNAPSHOT_KEY_PREFIX = "yjs:snapshot:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration UPDATE_STREAM_TTL = Duration.ofDays(7);
    private static final int MAX_PENDING_UPDATES = 100;
    private static final int BATCH_PROCESS_THRESHOLD = 10;
    private static final long SNAPSHOT_INTERVAL_MS = 5 * 60 * 1000;

    /**
     * Process incoming Yjs update with proper CRDT merging
     * This is the core method that handles all document updates
     */
    @Transactional
    public void processYjsUpdate(String documentId, byte[] updateData) {
        Instant startTime = Instant.now();

        try {
            // Validate inputs
            if (!validateInput(documentId, updateData)) {
                return;
            }

            // Validate update format using Yjs engine
            if (!isValidYjsUpdate(updateData)) {
                log.warn("Invalid Yjs update format for document: {}", documentId);
                failedOperations.incrementAndGet();
                return;
            }

            // Check document exists and is accessible
            if (!documentExists(documentId)) {
                log.warn("Received update for non-existent document: {}", documentId);
                failedOperations.incrementAndGet();
                return;
            }

            // Add to pending updates for batch processing
            addPendingUpdate(documentId, updateData);

            // Process immediately if threshold reached
            if (shouldProcessImmediately(documentId)) {
                processPendingUpdates(documentId);
            }

            totalUpdatesProcessed.incrementAndGet();

            long processingTime = Duration.between(startTime, Instant.now()).toMillis();
            if (processingTime > 100) {
                log.warn("Slow update processing for document {}: {}ms", documentId, processingTime);
            }

        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("Error processing Yjs update for document {}: {}", documentId, e.getMessage(), e);
            throw new RuntimeException("Failed to process Yjs update", e);
        }
    }

    /**
     * Add update to pending queue for batch processing
     */
    private void addPendingUpdate(String documentId, byte[] updateData) {
        pendingUpdates.compute(documentId, (key, updates) -> {
            if (updates == null) {
                updates = Collections.synchronizedList(new ArrayList<>());
            }

            // Prevent unbounded growth
            if (updates.size() >= MAX_PENDING_UPDATES) {
                log.warn("Pending updates limit reached for document {}, processing immediately", documentId);
                return updates; // Don't add more, will trigger immediate processing
            }

            updates.add(new PendingUpdate(updateData, Instant.now()));
            return updates;
        });
    }

    /**
     * Check if updates should be processed immediately
     */
    private boolean shouldProcessImmediately(String documentId) {
        List<PendingUpdate> updates = pendingUpdates.get(documentId);
        if (updates == null) {
            return false;
        }

        // Process if we have enough updates or if the oldest is too old
        if (updates.size() >= BATCH_PROCESS_THRESHOLD) {
            return true;
        }

        if (!updates.isEmpty()) {
            PendingUpdate oldest = updates.get(0);
            return Duration.between(oldest.timestamp, Instant.now()).toMillis() > 1000;
        }

        return false;
    }


    /**
     * Process all pending updates for a document
     */
    private void processPendingUpdates(String documentId) {
        List<PendingUpdate> updates = pendingUpdates.remove(documentId);
        if (updates == null || updates.isEmpty()) {
            return;
        }

        String lockKey = YJS_LOCK_KEY_PREFIX + documentId;
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1".getBytes(), LOCK_TTL);

        if (Boolean.TRUE.equals(lockAcquired)) {
            try {
                mergeAndStoreUpdates(documentId, updates);
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            // Re-add updates if couldn't acquire lock
            updates.forEach(update -> addPendingUpdate(documentId, update.data));
            log.debug("Could not acquire lock for document {}, re-queuing updates", documentId);
        }
    }

    /**
     * Merge multiple updates and store the result
     */
    private void mergeAndStoreUpdates(String documentId, List<PendingUpdate> updates) {
        try {
            // Get current state
            DocumentState currentState = getOrCreateDocumentState(documentId);
            byte[] baseState = currentState.getState();

            // Extract update data
            List<byte[]> updateDataList = updates.stream()
                    .map(u -> u.data)
                    .toList();

            // Merge all updates using Yjs engine
            byte[] mergedState = yjsEnginePool.executeWithEngine(engine ->
                    engine.mergeMultipleUpdates(baseState, updateDataList)
            );

            // Update state in all storage layers
            updateDocumentState(documentId, mergedState);

            // Store updates in Redis stream for audit/replay
            storeUpdatesInStream(documentId, updates);

            totalMergeOperations.incrementAndGet();
            log.debug("Successfully merged {} updates for document {}", updates.size(), documentId);

        } catch (Exception e) {
            log.error("Error merging updates for document {}: {}", documentId, e.getMessage(), e);
            failedOperations.incrementAndGet();
            throw new RuntimeException("Failed to merge updates", e);
        }
    }

    /**
     * Update document state in all storage layers
     */
    private void updateDocumentState(String documentId, byte[] newState) {
        // Update in-memory cache
        DocumentState docState = documentStateCache.compute(documentId, (key, existing) -> {
            if (existing == null) {
                existing = new DocumentState(documentId);
            }
            existing.setState(newState);
            existing.setLastModified(Instant.now());
            existing.incrementVersion();
            return existing;
        });

        // Update Redis cache
        String stateKey = YJS_STATE_KEY_PREFIX + documentId;
        redisTemplate.opsForValue().set(stateKey, newState, CACHE_TTL);

        // Check if snapshot is needed
        if (docState.needsSnapshot(SNAPSHOT_INTERVAL_MS)) {
            scheduleSnapshot(documentId, newState);
        }
    }

    /**
     * Store updates in Redis stream for durability
     */
    private void storeUpdatesInStream(String documentId, List<PendingUpdate> updates) {
        try {
            String streamKey = YJS_UPDATE_STREAM_PREFIX + documentId;

            for (PendingUpdate update : updates) {
                Map<String, Object> fields = new HashMap<>();
                fields.put("update", update.data);
                fields.put("timestamp", update.timestamp.toString());
                fields.put("size", String.valueOf(update.data.length));

                redisTemplate.opsForStream().add(streamKey, fields);
            }

            redisTemplate.expire(streamKey, UPDATE_STREAM_TTL);

        } catch (Exception e) {
            log.error("Failed to store updates in stream for document {}: {}",
                    documentId, e.getMessage());
        }
    }

    /**
     * Get the current document state for synchronization
     */
    public byte[] getDocumentState(String documentId) {
        try {
            // Try memory cache first
            DocumentState cachedState = documentStateCache.get(documentId);
            if (cachedState != null && cachedState.getState() != null) {
                log.debug("Retrieved document state from memory cache: {} bytes",
                        cachedState.getState().length);
                return cachedState.getState();
            }

            // Try Redis cache
            String stateKey = YJS_STATE_KEY_PREFIX + documentId;
            byte[] redisState = redisTemplate.opsForValue().get(stateKey);
            if (redisState != null) {
                // Update memory cache
                documentStateCache.put(documentId, new DocumentState(documentId, redisState));
                log.debug("Retrieved document state from Redis: {} bytes", redisState.length);
                return redisState;
            }

            // Try database snapshot
            byte[] dbSnapshot = getDocumentSnapshotFromDatabase(documentId);
            if (dbSnapshot != null && dbSnapshot.length > 0) {
                // Restore from snapshot
                byte[] restoredState = yjsEnginePool.executeWithEngine(engine ->
                        engine.restoreFromSnapshot(dbSnapshot, null)
                );

                // Cache the restored state
                updateDocumentState(documentId, restoredState);
                log.debug("Restored document state from database: {} bytes", restoredState.length);
                return restoredState;
            }

            log.debug("No existing state found for document: {}", documentId);
            return new byte[0];

        } catch (Exception e) {
            log.error("Error retrieving document state for {}: {}", documentId, e.getMessage(), e);
            return new byte[0];
        }
    }

    /**
     * Get state vector for synchronization
     */
    public byte[] getStateVector(String documentId) {
        try {
            byte[] currentState = getDocumentState(documentId);
            if (currentState == null || currentState.length == 0) {
                return new byte[0];
            }

            return yjsEnginePool.executeWithEngine(engine ->
                    engine.getStateVector(currentState)
            );

        } catch (Exception e) {
            log.error("Error getting state vector for document {}: {}",
                    documentId, e.getMessage(), e);
            return new byte[0];
        }
    }

    /**
     * Compute diff for synchronization
     */
    public byte[] computeDiff(String documentId, byte[] clientStateVector) {
        try {
            byte[] currentState = getDocumentState(documentId);
            if (currentState == null || currentState.length == 0) {
                return new byte[0];
            }

            // This would require converting state vector to state, then computing diff
            // For now, returning the full state as diff
            return currentState;

        } catch (Exception e) {
            log.error("Error computing diff for document {}: {}",
                    documentId, e.getMessage(), e);
            return new byte[0];
        }
    }

    /**
     * Save Yjs snapshot to database
     */
    @Transactional
    public void saveYjsSnapshot(String documentId, byte[] snapshot) {
        try {
            if (snapshot == null || snapshot.length == 0) {
                log.warn("Attempted to save empty snapshot for document: {}", documentId);
                return;
            }

            // Create proper Yjs snapshot
            byte[] yjsSnapshot = yjsEnginePool.executeWithEngine(engine ->
                    engine.getSnapshot(snapshot)
            );

            // Save to database
            Document document = documentRepository.findByYjsRoomIdAndIsDeletedFalse(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

            document.setYjsSnapshot(yjsSnapshot);
            documentRepository.save(document);

            // Also cache in Redis for faster access
            String snapshotKey = YJS_SNAPSHOT_KEY_PREFIX + documentId;
            redisTemplate.opsForValue().set(snapshotKey, yjsSnapshot, Duration.ofDays(30));

            log.info("Saved Yjs snapshot for document {}: {} bytes", documentId, yjsSnapshot.length);

            // Update last snapshot time
            DocumentState state = documentStateCache.get(documentId);
            if (state != null) {
                state.setLastSnapshot(Instant.now());
            }

        } catch (Exception e) {
            log.error("Error saving Yjs snapshot for document {}: {}", documentId, e.getMessage(), e);
            throw new RuntimeException("Failed to save Yjs snapshot", e);
        }
    }

    /**
     * Validate input parameters
     */
    private boolean validateInput(String documentId, byte[] updateData) {
        if (documentId == null || documentId.trim().isEmpty()) {
            log.warn("Invalid document ID: null or empty");
            return false;
        }

        if (updateData == null || updateData.length == 0) {
            log.warn("Empty Yjs update for document: {}", documentId);
            return false;
        }

        // Check size limits (e.g., 10MB max)
        if (updateData.length > 10 * 1024 * 1024) {
            log.warn("Update too large for document {}: {} bytes", documentId, updateData.length);
            return false;
        }

        return true;
    }


    /**
     * Validate if data is a valid Yjs update
     */
    private boolean isValidYjsUpdate(byte[] updateData) {
        try {
            return yjsEnginePool.executeWithEngine(engine ->
                    engine.validateUpdate(updateData)
            );
        } catch (Exception e) {
            log.warn("Update validation failed: {}", e.getMessage());
            return false;
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
     * Get document snapshot from database
     */
    private byte[] getDocumentSnapshotFromDatabase(String documentId) {
        try {
            // Try Redis snapshot cache first
            String snapshotKey = YJS_SNAPSHOT_KEY_PREFIX + documentId;
            byte[] redisSnapshot = redisTemplate.opsForValue().get(snapshotKey);
            if (redisSnapshot != null) {
                return redisSnapshot;
            }

            // Fall back to database
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
     * Get or create document state
     */
    private DocumentState getOrCreateDocumentState(String documentId) {
        return documentStateCache.computeIfAbsent(documentId, key -> {
            byte[] existingState = getDocumentState(key);
            return new DocumentState(key, existingState);
        });
    }

    /**
     * Schedule snapshot for async processing
     */
    private void scheduleSnapshot(String documentId, byte[] state) {
        // This could be added to a queue for async processing
        // For now, we'll rely on the scheduled task
        log.debug("Snapshot scheduled for document: {}", documentId);
    }


    /**
     * Scheduled task to persist snapshots
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void persistDocumentSnapshots() {
        documentStateCache.forEach((documentId, state) -> {
            try {
                if (state.needsSnapshot(SNAPSHOT_INTERVAL_MS) && state.getState() != null) {
                    saveYjsSnapshot(documentId, state.getState());
                }
            } catch (Exception e) {
                log.error("Error persisting snapshot for document {}: {}",
                        documentId, e.getMessage());
            }
        });
    }

    /**
     * Scheduled task to process pending updates
     */
    @Scheduled(fixedDelay = 1000) // Every second
    public void processPendingUpdatesBatch() {
        pendingUpdates.forEach((documentId, updates) -> {
            if (updates != null && !updates.isEmpty()) {
                if (shouldProcessImmediately(documentId)) {
                    processPendingUpdates(documentId);
                }
            }
        });
    }

    /**
     * Clear cache for a document
     */
    public void clearDocumentCache(String documentId) {
        try {
            documentStateCache.remove(documentId);
            pendingUpdates.remove(documentId);

            String stateKey = YJS_STATE_KEY_PREFIX + documentId;
            String snapshotKey = YJS_SNAPSHOT_KEY_PREFIX + documentId;
            String streamKey = YJS_UPDATE_STREAM_PREFIX + documentId;

            redisTemplate.delete(Arrays.asList(stateKey, snapshotKey, streamKey));

            log.info("Cleared cache for document: {}", documentId);
        } catch (Exception e) {
            log.error("Error clearing cache for document {}: {}", documentId, e.getMessage(), e);
        }
    }

    /**
     * Get service metrics
     */
    public ServiceMetrics getMetrics() {
        return ServiceMetrics.builder()
                .activeCachedDocuments(documentStateCache.size())
                .pendingDocuments(pendingUpdates.size())
                .totalUpdatesProcessed(totalUpdatesProcessed.get())
                .totalMergeOperations(totalMergeOperations.get())
                .failedOperations(failedOperations.get())
                .enginePoolStats(yjsEnginePool.getStatistics())
                .build();
    }

    /**
     * Document state holder
     */
    private static class DocumentState {
        private final String documentId;
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private byte[] state;
        private Instant lastModified;
        private Instant lastSnapshot;
        private int version;

        public DocumentState(String documentId) {
            this(documentId, new byte[0]);
        }

        public DocumentState(String documentId, byte[] state) {
            this.documentId = documentId;
            this.state = state;
            this.lastModified = Instant.now();
            this.lastSnapshot = Instant.now();
            this.version = 0;
        }

        public byte[] getState() {
            lock.readLock().lock();
            try {
                return state;
            } finally {
                lock.readLock().unlock();
            }
        }

        public void setState(byte[] state) {
            lock.writeLock().lock();
            try {
                this.state = state;
            } finally {
                lock.writeLock().unlock();
            }
        }

        public void setLastModified(Instant lastModified) {
            this.lastModified = lastModified;
        }

        public void setLastSnapshot(Instant lastSnapshot) {
            this.lastSnapshot = lastSnapshot;
        }

        public void incrementVersion() {
            this.version++;
        }

        public boolean needsSnapshot(long intervalMs) {
            return Duration.between(lastSnapshot, Instant.now()).toMillis() > intervalMs;
        }
    }

    /**
     * Pending update holder
     */
    private static class PendingUpdate {
        private final byte[] data;
        private final Instant timestamp;

        public PendingUpdate(byte[] data, Instant timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }

    /**
     * Service metrics
     */
    @lombok.Builder
    @lombok.Getter
    public static class ServiceMetrics {
        private final int activeCachedDocuments;
        private final int pendingDocuments;
        private final int totalUpdatesProcessed;
        private final int totalMergeOperations;
        private final int failedOperations;
        private final YjsEnginePool.PoolStatistics enginePoolStats;


}}

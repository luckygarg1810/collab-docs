const Y = require('yjs');
const { encoding, decoding } = require('lib0');
const awarenessProtocol = require('y-protocols/awareness');
const logger = require('../config/logger');
const { saveDocumentState, loadDocumentState } = require('./redisAdapter');
const { saveSnapshotToPostgres, loadSnapshotFromPostgres } = require('./postgresAdapter');

// In-memory storage for active Yjs documents
const documents = new Map();

// Awareness instances for presence tracking
const awarenesses = new Map();

// Track pending debounced saves
const pendingSaves = new Map();

// Track auto-save interval timers per document
const autoSaveTimers = new Map();

// Track save locks to prevent concurrent saves
const saveLocks = new Map();

// Track last activity timestamps for cleanup
const lastActivityTimestamps = new Map();

// Auto-save interval (5 minutes)
const AUTO_SAVE_INTERVAL = 5 * 60 * 1000;

// Debounced save interval (30 seconds after last edit)
const DEBOUNCE_SAVE_INTERVAL = 30 * 1000;

// Inactivity timeout for document cleanup (10 minutes)
const INACTIVITY_CLEANUP_TIMEOUT = 10 * 60 * 1000;

// Cleanup check interval (run every 5 minutes)
const CLEANUP_CHECK_INTERVAL = 5 * 60 * 1000;

/**
 * Get or create a Yjs document
 * @param {string} documentId - Document identifier
 * @returns {Promise<Y.Doc>} Yjs document
 */
async function getDocument(documentId) {
    if (documents.has(documentId)) {
        return documents.get(documentId);
    }

    // Create new Y.Doc
    const ydoc = new Y.Doc();

    // Try to load persisted state - Redis first (fast, latest), then PostgreSQL (permanent)
    let persistedState = null;
    let source = null;

    // 1. Try Redis first (fast cache with most recent changes)
    persistedState = await loadDocumentState(documentId);
    if (persistedState) {
        source = 'Redis';
    } else {
        // 2. Fallback to PostgreSQL (permanent storage)
        persistedState = await loadSnapshotFromPostgres(documentId);
        if (persistedState) {
            source = 'PostgreSQL';

            // Warm up Redis cache with PostgreSQL data for faster future access
            try {
                await saveDocumentState(documentId, persistedState);
                logger.debug('Redis cache warmed up from PostgreSQL', { documentId });
            } catch (error) {
                logger.warn('Failed to warm up Redis cache', { documentId, error: error.message });
            }
        }
    }

    if (persistedState) {
        try {
            Y.applyUpdate(ydoc, persistedState);
            logger.info('Document loaded', { documentId, source });
        } catch (error) {
            logger.error('Failed to apply persisted state', {
                documentId,
                source,
                error: error.message
            });
        }
    } else {
        logger.info('New document created', { documentId });
    }

    // Store document
    documents.set(documentId, ydoc);

    // Track initial activity timestamp
    lastActivityTimestamps.set(documentId, Date.now());

    // Set up auto-save
    setupAutoSave(documentId, ydoc);

    return ydoc;
}

/**
 * Setup automatic document save to both Redis and PostgreSQL
 * @param {string} documentId - Document identifier
 * @param {Y.Doc} ydoc - Yjs document
 */
function setupAutoSave(documentId, ydoc) {
    // Clear existing timer if any
    if (autoSaveTimers.has(documentId)) {
        clearInterval(autoSaveTimers.get(documentId));
    }

    const saveInterval = setInterval(async () => {
        // Check if save is already in progress
        if (saveLocks.get(documentId)) {
            logger.debug('Auto-save skipped - save already in progress', { documentId });
            return;
        }

        saveLocks.set(documentId, true);

        try {
            const state = Y.encodeStateAsUpdate(ydoc);

            // Save to both Redis (fast cache) and PostgreSQL (permanent)
            const [redisResult, postgresResult] = await Promise.allSettled([
                saveDocumentState(documentId, state),
                saveSnapshotToPostgres(documentId, state)
            ]);

            const redisSuccess = redisResult.status === 'fulfilled';
            const postgresSuccess = postgresResult.status === 'fulfilled';

            logger.debug('Document auto-saved', {
                documentId,
                redis: redisSuccess ? 'success' : 'failed',
                postgres: postgresSuccess ? 'success' : 'failed'
            });
        } catch (error) {
            logger.error('Auto-save failed', { documentId, error: error.message });
        } finally {
            saveLocks.set(documentId, false);
        }
    }, AUTO_SAVE_INTERVAL);

    // Store the interval timer for explicit cleanup
    autoSaveTimers.set(documentId, saveInterval);
}

/**
 * Get or create awareness instance for a document
 * @param {string} documentId - Document identifier
 * @returns {awarenessProtocol.Awareness} Awareness instance
 */
function getAwareness(documentId) {
    if (awarenesses.has(documentId)) {
        return awarenesses.get(documentId);
    }

    const ydoc = documents.get(documentId);
    if (!ydoc) {
        logger.warn('Trying to get awareness for non-existent document', { documentId });
        return null;
    }

    const awareness = new awarenessProtocol.Awareness(ydoc);
    awarenesses.set(documentId, awareness);

    logger.debug('Awareness instance created', { documentId });
    return awareness;
}

/**
 * Handle incoming Yjs update message
 * Includes debounced save and incremental Redis persistence
 * @param {string} documentId - Document identifier
 * @param {Uint8Array} update - Yjs update binary
 */
async function applyUpdate(documentId, update) {
    try {
        const ydoc = await getDocument(documentId);
        Y.applyUpdate(ydoc, update);

        // Update last activity timestamp
        lastActivityTimestamps.set(documentId, Date.now());

        logger.debug('Update applied to document', {
            documentId,
            updateSize: update.length
        });

        // Fast snapshot save to Redis (fire-and-forget)
        // Note: This saves the FULL document state, not just the incremental update
        // Trade-off: Simplicity and immediate crash protection vs. bandwidth/storage
        // For true incremental updates, would need to maintain an update queue in Redis
        // and periodically compact, which adds significant complexity
        saveDocumentState(documentId, Y.encodeStateAsUpdate(ydoc)).catch(error => {
            logger.warn('Redis snapshot save failed', { documentId, error: error.message });
        });

        // Schedule debounced save to PostgreSQL (30s after last edit)
        // This reduces PostgreSQL writes while ensuring durability
        if (pendingSaves.has(documentId)) {
            clearTimeout(pendingSaves.get(documentId));
        }

        const timeout = setTimeout(async () => {
            try {
                await saveToDisk(documentId);
                pendingSaves.delete(documentId);
                logger.debug('Debounced save completed', { documentId });
            } catch (error) {
                logger.error('Debounced save failed', { documentId, error: error.message });
            }
        }, DEBOUNCE_SAVE_INTERVAL);

        pendingSaves.set(documentId, timeout);

        return true;
    } catch (error) {
        logger.error('Failed to apply update', {
            documentId,
            error: error.message
        });
        return false;
    }
}

/**
 * Save document to PostgreSQL only (with lock to prevent concurrent saves)
 * Redis saves happen incrementally in applyUpdate
 * @param {string} documentId - Document identifier
 * @returns {Promise<boolean>} Success status
 */
async function saveToDisk(documentId) {
    const ydoc = documents.get(documentId);
    if (!ydoc) {
        logger.warn('Cannot save non-existent document', { documentId });
        return false;
    }

    // Check if save is already in progress
    if (saveLocks.get(documentId)) {
        logger.debug('Save already in progress, skipping', { documentId });
        return false;
    }

    saveLocks.set(documentId, true);

    try {
        const state = Y.encodeStateAsUpdate(ydoc);
        await saveSnapshotToPostgres(documentId, state);

        logger.debug('Document saved to PostgreSQL', { documentId });
        return true;
    } catch (error) {
        logger.error('PostgreSQL save failed', { documentId, error: error.message });
        return false;
    } finally {
        saveLocks.set(documentId, false);
    }
}

/**
 * Get the current state vector of a document
 * @param {string} documentId - Document identifier
 * @returns {Promise<Uint8Array>} State vector
 */
async function getStateVector(documentId) {
    const ydoc = await getDocument(documentId);
    return Y.encodeStateVector(ydoc);
}

/**
 * Get state as update for synchronization
 * @param {string} documentId - Document identifier
 * @param {Uint8Array} stateVector - Client's state vector
 * @returns {Promise<Uint8Array>} State update
 */
async function getStateAsUpdate(documentId, stateVector) {
    const ydoc = await getDocument(documentId);
    return Y.encodeStateAsUpdate(ydoc, stateVector);
}

/**
 * Save document state immediately to both Redis and PostgreSQL
 * @param {string} documentId - Document identifier
 * @param {boolean} forceSave - Force save even if lock is active (for shutdown/close)
 */
async function saveDocument(documentId, forceSave = false) {
    const ydoc = documents.get(documentId);
    if (!ydoc) {
        logger.warn('Cannot save non-existent document', { documentId });
        return false;
    }

    // Check save lock (unless forcing)
    if (!forceSave && saveLocks.get(documentId)) {
        logger.debug('Save already in progress, skipping', { documentId });
        return false;
    }

    // Set lock
    saveLocks.set(documentId, true);

    try {
        const state = Y.encodeStateAsUpdate(ydoc);

        // Save to both Redis and PostgreSQL
        const [redisResult, postgresResult] = await Promise.allSettled([
            saveDocumentState(documentId, state),
            saveSnapshotToPostgres(documentId, state)
        ]);

        const redisSuccess = redisResult.status === 'fulfilled' && redisResult.value;
        const postgresSuccess = postgresResult.status === 'fulfilled' && postgresResult.value;

        logger.info('Document saved manually', {
            documentId,
            redis: redisSuccess ? 'success' : 'failed',
            postgres: postgresSuccess ? 'success' : 'failed',
            forced: forceSave
        });

        // Success if at least one save succeeded
        return redisSuccess || postgresSuccess;
    } catch (error) {
        logger.error('Manual save failed', { documentId, error: error.message });
        return false;
    } finally {
        saveLocks.set(documentId, false);
    }
}

/**
 * Update activity timestamp for a document
 * Called on any interaction (read, write, presence)
 * @param {string} documentId - Document identifier
 */
function updateActivityTimestamp(documentId) {
    lastActivityTimestamps.set(documentId, Date.now());
}

/**
 * Close and cleanup a document
 * @param {string} documentId - Document identifier
 */
async function closeDocument(documentId) {
    const ydoc = documents.get(documentId);
    if (ydoc) {
        // Clear pending debounced save timer
        if (pendingSaves.has(documentId)) {
            clearTimeout(pendingSaves.get(documentId));
            pendingSaves.delete(documentId);
        }

        // Clear auto-save timer (explicitly, don't rely on destroy event)
        if (autoSaveTimers.has(documentId)) {
            clearInterval(autoSaveTimers.get(documentId));
            autoSaveTimers.delete(documentId);
        }

        // Clear save lock
        saveLocks.delete(documentId);

        // Clear last activity timestamp
        lastActivityTimestamps.delete(documentId);

        // Save final state before cleanup
        await saveDocument(documentId);

        // Destroy document
        ydoc.destroy();
        documents.delete(documentId);

        // Clean up awareness
        const awareness = awarenesses.get(documentId);
        if (awareness) {
            awareness.destroy();
            awarenesses.delete(documentId);
        }

        logger.info('Document closed and cleaned up', { documentId });
    }
}

/**
 * Save all active documents (useful for graceful shutdown)
 * @returns {Promise<Object>} Save results
 */
async function saveAllDocuments() {
    const documentIds = Array.from(documents.keys());
    logger.info('Saving all active documents', { count: documentIds.length });

    // Clear all pending debounced saves
    pendingSaves.forEach((timeout) => clearTimeout(timeout));
    pendingSaves.clear();

    // Force save all documents (ignore locks for shutdown)
    const results = await Promise.allSettled(
        documentIds.map(id => saveDocument(id, true))
    );

    const saved = results.filter(r => r.status === 'fulfilled' && r.value).length;
    const failed = results.length - saved;

    logger.info('Bulk save completed', {
        total: documentIds.length,
        saved,
        failed
    });

    return { total: documentIds.length, saved, failed };
}

/**
 * Cleanup inactive documents to prevent memory leaks
 * Called periodically to check for documents with no recent activity
 * @returns {Promise<number>} Number of documents cleaned up
 */
async function cleanupInactiveDocuments() {
    const now = Date.now();
    const inactiveDocuments = [];

    // Find documents that haven't been accessed in INACTIVITY_CLEANUP_TIMEOUT
    for (const [documentId, lastActivity] of lastActivityTimestamps.entries()) {
        if (now - lastActivity > INACTIVITY_CLEANUP_TIMEOUT) {
            inactiveDocuments.push(documentId);
        }
    }

    if (inactiveDocuments.length > 0) {
        logger.info('Cleaning up inactive documents', {
            count: inactiveDocuments.length,
            documentIds: inactiveDocuments
        });

        // Close each inactive document
        for (const documentId of inactiveDocuments) {
            try {
                await closeDocument(documentId);
            } catch (error) {
                logger.error('Failed to cleanup inactive document', {
                    documentId,
                    error: error.message
                });
            }
        }
    }

    return inactiveDocuments.length;
}

/**
 * Start periodic cleanup of inactive documents
 * @returns {NodeJS.Timeout} Cleanup interval timer
 */
function startCleanupInterval() {
    const cleanupInterval = setInterval(async () => {
        try {
            const cleanedCount = await cleanupInactiveDocuments();
            if (cleanedCount > 0) {
                logger.info('Cleanup cycle completed', { cleanedDocuments: cleanedCount });
            }
        } catch (error) {
            logger.error('Cleanup cycle failed', { error: error.message });
        }
    }, CLEANUP_CHECK_INTERVAL);

    logger.info('Cleanup interval started', {
        checkIntervalMinutes: CLEANUP_CHECK_INTERVAL / 1000 / 60,
        inactivityTimeoutMinutes: INACTIVITY_CLEANUP_TIMEOUT / 1000 / 60
    });

    return cleanupInterval;
}

/**
 * Stop cleanup interval (for shutdown)
 * @param {NodeJS.Timeout} cleanupInterval - The interval timer to stop
 */
function stopCleanupInterval(cleanupInterval) {
    if (cleanupInterval) {
        clearInterval(cleanupInterval);
        logger.info('Cleanup interval stopped');
    }
}

/**
 * Get statistics about active documents
 * @returns {Object} Statistics
 */
function getStats() {
    return {
        activeDocuments: documents.size,
        activeAwarenesses: awarenesses.size,
        pendingSaves: pendingSaves.size,
        autoSaveTimers: autoSaveTimers.size,
        activeSaveLocks: Array.from(saveLocks.values()).filter(v => v).length,
        trackedActivities: lastActivityTimestamps.size
    };
}

module.exports = {
    getDocument,
    getAwareness,
    applyUpdate,
    getStateVector,
    getStateAsUpdate,
    saveDocument,
    closeDocument,
    saveAllDocuments,
    cleanupInactiveDocuments,
    startCleanupInterval,
    stopCleanupInterval,
    updateActivityTimestamp,
    getStats,
};

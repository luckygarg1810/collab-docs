const Y = require('yjs');
const { encoding, decoding, awarenessProtocol } = require('lib0');
const logger = require('../config/logger');
const { saveDocumentState, loadDocumentState } = require('./redisAdapter');

// In-memory storage for active Yjs documents
const documents = new Map();

// Awareness instances for presence tracking
const awarenesses = new Map();

// Auto-save interval (5 minutes)
const AUTO_SAVE_INTERVAL = 5 * 60 * 1000;

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

    // Try to load persisted state from Redis
    const persistedState = await loadDocumentState(documentId);
    if (persistedState) {
        try {
            Y.applyUpdate(ydoc, persistedState);
            logger.info('Document loaded from Redis', { documentId });
        } catch (error) {
            logger.error('Failed to apply persisted state', {
                documentId,
                error: error.message
            });
        }
    } else {
        logger.info('New document created', { documentId });
    }

    // Store document
    documents.set(documentId, ydoc);

    // Set up auto-save
    setupAutoSave(documentId, ydoc);

    return ydoc;
}

/**
 * Setup automatic document save to Redis
 * @param {string} documentId - Document identifier
 * @param {Y.Doc} ydoc - Yjs document
 */
function setupAutoSave(documentId, ydoc) {
    const saveInterval = setInterval(async () => {
        try {
            const state = Y.encodeStateAsUpdate(ydoc);
            await saveDocumentState(documentId, state);
            logger.debug('Document auto-saved', { documentId });
        } catch (error) {
            logger.error('Auto-save failed', { documentId, error: error.message });
        }
    }, AUTO_SAVE_INTERVAL);

    // Clear interval when document is closed
    ydoc.on('destroy', () => {
        clearInterval(saveInterval);
    });
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
 * @param {string} documentId - Document identifier
 * @param {Uint8Array} update - Yjs update binary
 */
async function applyUpdate(documentId, update) {
    try {
        const ydoc = await getDocument(documentId);
        Y.applyUpdate(ydoc, update);

        logger.debug('Update applied to document', {
            documentId,
            updateSize: update.length
        });

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
 * Save document state immediately
 * @param {string} documentId - Document identifier
 */
async function saveDocument(documentId) {
    const ydoc = documents.get(documentId);
    if (!ydoc) {
        logger.warn('Cannot save non-existent document', { documentId });
        return false;
    }

    try {
        const state = Y.encodeStateAsUpdate(ydoc);
        await saveDocumentState(documentId, state);
        logger.info('Document saved manually', { documentId });
        return true;
    } catch (error) {
        logger.error('Manual save failed', { documentId, error: error.message });
        return false;
    }
}

/**
 * Close and cleanup a document
 * @param {string} documentId - Document identifier
 */
async function closeDocument(documentId) {
    const ydoc = documents.get(documentId);
    if (ydoc) {
        // Save final state
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
 * Get statistics about active documents
 * @returns {Object} Statistics
 */
function getStats() {
    return {
        activeDocuments: documents.size,
        activeAwarenesses: awarenesses.size,
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
    getStats,
};

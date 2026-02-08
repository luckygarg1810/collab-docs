const Redis = require('ioredis');
const logger = require('../config/logger');

const redis = new Redis({
    host: process.env.REDIS_HOST || 'localhost',
    port: process.env.REDIS_PORT || 6379,
    password: process.env.REDIS_PASSWORD,
    db: process.env.REDIS_DATABASE || 0,
    retryStrategy: (times) => {
        const delay = Math.min(times * 50, 2000);
        return delay;
    },
    maxRetriesPerRequest: 3,
});

redis.on('connect', () => {
    logger.info('Redis connected successfully');
});

redis.on('error', (error) => {
    logger.error('Redis connection error', { error: error.message });
});

redis.on('close', () => {
    logger.warn('Redis connection closed');
});

/**
 * Save Yjs document state to Redis
 * @param {string} documentId - Document identifier
 * @param {Uint8Array} state - Yjs document state as binary
 * @returns {Promise<boolean>} Success status
 */
async function saveDocumentState(documentId, state) {
    try {
        const key = `yjs:doc:${documentId}`;
        const buffer = Buffer.from(state);
        await redis.setex(key, 86400, buffer); // 24 hour TTL
        logger.debug('Document state saved to Redis', { documentId, size: buffer.length });
        return true;
    } catch (error) {
        logger.error('Failed to save document state', { documentId, error: error.message });
        return false;
    }
}

/**
 * Load Yjs document state from Redis
 * @param {string} documentId - Document identifier
 * @returns {Promise<Uint8Array|null>} Document state or null if not found
 */
async function loadDocumentState(documentId) {
    try {
        const key = `yjs:doc:${documentId}`;
        const buffer = await redis.getBuffer(key);

        if (!buffer) {
            logger.debug('No cached state found for document', { documentId });
            return null;
        }

        logger.debug('Document state loaded from Redis', { documentId, size: buffer.length });
        return new Uint8Array(buffer);
    } catch (error) {
        logger.error('Failed to load document state', { documentId, error: error.message });
        return null;
    }
}

/**
 * Track active users in a document
 * @param {string} documentId - Document identifier
 * @param {string} userId - User identifier
 * @param {Object} userInfo - User information for presence
 */
async function addActiveUser(documentId, userId, userInfo) {
    try {
        const key = `yjs:active:${documentId}`;
        await redis.hset(key, userId, JSON.stringify({
            ...userInfo,
            joinedAt: Date.now(),
        }));
        await redis.expire(key, 3600); // 1 hour expiry
        logger.debug('User added to active users', { documentId, userId });
    } catch (error) {
        logger.error('Failed to add active user', { documentId, userId, error: error.message });
    }
}

/**
 * Remove user from active users list
 * @param {string} documentId - Document identifier
 * @param {string} userId - User identifier
 */
async function removeActiveUser(documentId, userId) {
    try {
        const key = `yjs:active:${documentId}`;
        await redis.hdel(key, userId);
        logger.debug('User removed from active users', { documentId, userId });
    } catch (error) {
        logger.error('Failed to remove active user', { documentId, userId, error: error.message });
    }
}

/**
 * Get all active users in a document
 * @param {string} documentId - Document identifier
 * @returns {Promise<Array>} Array of active users
 */
async function getActiveUsers(documentId) {
    try {
        const key = `yjs:active:${documentId}`;
        const users = await redis.hgetall(key);
        return Object.entries(users).map(([userId, data]) => ({
            userId,
            ...JSON.parse(data),
        }));
    } catch (error) {
        logger.error('Failed to get active users', { documentId, error: error.message });
        return [];
    }
}

/**
 * Gracefully close Redis connection
 */
async function closeRedis() {
    await redis.quit();
    logger.info('Redis connection closed');
}

module.exports = {
    redis,
    saveDocumentState,
    loadDocumentState,
    addActiveUser,
    removeActiveUser,
    getActiveUsers,
    closeRedis,
};

const axios = require('axios');
const logger = require('../config/logger');

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';
const INTERNAL_SERVICE_KEY = process.env.INTERNAL_SERVICE_KEY;

/**
 * Save Yjs snapshot to PostgreSQL database via Spring Boot backend
 * @param {string} yjsRoomId - Yjs room identifier
 * @param {Uint8Array} snapshot - Yjs document snapshot as binary data
 * @returns {Promise<boolean>} Success status
 */
async function saveSnapshotToPostgres(yjsRoomId, snapshot) {
    try {
        const buffer = Buffer.from(snapshot);

        const response = await axios.post(
            `${BACKEND_URL}/api/documents/yjs-snapshot`,
            buffer,
            {
                params: { yjsRoomId },
                headers: {
                    'Content-Type': 'application/octet-stream',
                    'X-Internal-Service-Key': INTERNAL_SERVICE_KEY,
                },
                timeout: 10000, // 10 second timeout
                maxContentLength: 50 * 1024 * 1024, // 50MB max
            }
        );

        if (response.status === 200) {
            logger.info('Snapshot saved to PostgreSQL', {
                yjsRoomId,
                size: buffer.length,
            });
            return true;
        }

        logger.warn('Unexpected response saving snapshot', {
            yjsRoomId,
            status: response.status,
        });
        return false;

    } catch (error) {
        if (error.code === 'ECONNREFUSED') {
            logger.error('Cannot connect to backend - is it running?', {
                yjsRoomId,
                backendUrl: BACKEND_URL,
            });
        } else if (error.response) {
            logger.error('Backend error saving snapshot', {
                yjsRoomId,
                status: error.response.status,
                message: error.response.data,
            });
        } else {
            logger.error('Failed to save snapshot to PostgreSQL', {
                yjsRoomId,
                error: error.message,
            });
        }
        return false;
    }
}

/**
 * Load Yjs snapshot from PostgreSQL database via Spring Boot backend
 * @param {string} yjsRoomId - Yjs room identifier
 * @returns {Promise<Uint8Array|null>} Snapshot data or null if not found
 */
async function loadSnapshotFromPostgres(yjsRoomId) {
    try {
        const response = await axios.get(
            `${BACKEND_URL}/api/documents/yjs-snapshot/${yjsRoomId}`,
            {
                headers: {
                    'X-Internal-Service-Key': INTERNAL_SERVICE_KEY,
                },
                responseType: 'arraybuffer',
                timeout: 10000, // 10 second timeout
            }
        );

        if (response.status === 200 && response.data) {
            const snapshot = new Uint8Array(response.data);
            logger.info('Snapshot loaded from PostgreSQL', {
                yjsRoomId,
                size: snapshot.length,
            });
            return snapshot;
        }

        if (response.status === 204) {
            logger.debug('No snapshot found in PostgreSQL', { yjsRoomId });
            return null;
        }

        logger.warn('Unexpected response loading snapshot', {
            yjsRoomId,
            status: response.status,
        });
        return null;

    } catch (error) {
        if (error.code === 'ECONNREFUSED') {
            logger.warn('Cannot connect to backend - falling back to Redis only', {
                yjsRoomId,
                backendUrl: BACKEND_URL,
            });
        } else if (error.response && error.response.status === 404) {
            logger.debug('Snapshot not found in PostgreSQL', { yjsRoomId });
        } else if (error.response && error.response.status === 204) {
            logger.debug('No snapshot available in PostgreSQL', { yjsRoomId });
        } else if (error.response) {
            logger.error('Backend error loading snapshot', {
                yjsRoomId,
                status: error.response.status,
            });
        } else {
            logger.error('Failed to load snapshot from PostgreSQL', {
                yjsRoomId,
                error: error.message,
            });
        }
        return null;
    }
}

module.exports = {
    saveSnapshotToPostgres,
    loadSnapshotFromPostgres,
};

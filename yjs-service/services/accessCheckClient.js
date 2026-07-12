const axios = require('axios');
const logger = require('../config/logger');

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';
const INTERNAL_SERVICE_KEY = process.env.INTERNAL_SERVICE_KEY;

/**
 * Ask the backend whether a user still has at least VIEWER access to a
 * document, by yjsRoomId. Used by the per-connection 15-min hardcheck as a
 * safety net for permission changes that never went through a code path
 * that publishes a document-events broadcast.
 *
 * Fails open on error (network blip, backend briefly down) — we don't want
 * a transient failure here kicking every active session. The document-events
 * broadcast remains the fast, reliable path for real deletions/revocations;
 * this is best-effort backup, not the primary enforcement mechanism.
 *
 * @param {string} yjsRoomId
 * @param {string|number} userId
 * @returns {Promise<boolean>}
 */
async function checkAccess(yjsRoomId, userId) {
    try {
        const response = await axios.get(`${BACKEND_URL}/api/documents/access-check`, {
            params: { yjsRoomId, userId },
            headers: { 'X-Internal-Service-Key': INTERNAL_SERVICE_KEY },
            timeout: 10000,
        });
        return response.data?.hasAccess !== false;
    } catch (error) {
        logger.error('Access-check request failed, failing open', {
            yjsRoomId,
            userId,
            error: error.message,
        });
        return true;
    }
}

module.exports = { checkAccess };

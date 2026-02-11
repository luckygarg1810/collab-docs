const jwt = require('jsonwebtoken');
const logger = require('../config/logger');

const JWT_SECRET = process.env.JWT_SECRET || 'your-secret-key-change-this-in-production-min-32-chars';

/**
 * Verify JWT token from WebSocket connection
 * @param {string} token - JWT token to verify
 * @returns {Object|null} Decoded token payload or null if invalid
 */
function verifyToken(token) {
    try {
        console.log('🔑 [DEBUG] Verifying with secret:', JWT_SECRET.substring(0, 10) + '... (length: ' + JWT_SECRET.length + ')');
        const decoded = jwt.verify(token, JWT_SECRET);
        return decoded;
    } catch (error) {
        logger.warn('JWT verification failed', { error: error.message });
        console.log('❌ [DEBUG] Verification error:', error.message);
        return null;
    }
}

/**
 * Extract token from WebSocket upgrade request
 * Supports token in:
 * - Query parameter: ?token=xxx
 * - Authorization header: Bearer xxx
 * @param {Object} request - HTTP upgrade request
 * @returns {string|null} Extracted token or null
 */
function extractToken(request) {
    // Try query parameter first
    const url = new URL(request.url, `http://${request.headers.host}`);
    const tokenFromQuery = url.searchParams.get('token');

    if (tokenFromQuery) {
        return tokenFromQuery;
    }

    // Try Authorization header
    const authHeader = request.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
        return authHeader.substring(7);
    }

    return null;
}

/**
 * Authenticate WebSocket connection
 * @param {Object} request - HTTP upgrade request
 * @returns {Object|null} User info if authenticated, null otherwise
 */
function authenticateConnection(request) {
    const token = extractToken(request);

    console.log('🔍 [DEBUG] Authentication attempt:', {
        hasToken: !!token,
        tokenPreview: token ? token.substring(0, 20) + '...' : null,
        secret: process.env.JWT_SECRET ? 'SET' : 'NOT SET'
    });

    if (!token) {
        logger.warn('No token provided in WebSocket connection');
        return null;
    }

    const decoded = verifyToken(token);

    console.log('🔍 [DEBUG] Token verification:', {
        valid: !!decoded,
        decoded: decoded
    });

    if (!decoded) {
        return null;
    }

    // Extract user info from JWT payload
    // IMPORTANT: Spring Boot JWT structure:
    // - "sub": email (subject)
    // - "userId": numeric user ID
    // - "firstName", "lastName": user names
    const userInfo = {
        userId: decoded.userId || decoded.id || decoded.sub,  // ✅ Check userId FIRST (not sub which is email)
        email: decoded.email || decoded.sub,                   // ✅ Email from explicit claim or subject
        name: decoded.name || `${decoded.firstName || ''} ${decoded.lastName || ''}`.trim(),
    };

    if (!userInfo.userId) {
        logger.warn('Token missing user ID');
        return null;
    }

    logger.info('WebSocket connection authenticated', {
        userId: userInfo.userId,
        email: userInfo.email
    });

    return userInfo;
}

module.exports = {
    verifyToken,
    extractToken,
    authenticateConnection,
};

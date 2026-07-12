const Redis = require('ioredis');
const logger = require('../config/logger');

const CHANNEL = 'document-events';

// Custom WebSocket close code (4000-4999 is the reserved application-specific
// range per RFC 6455) for "you lost access to this document" — distinct from
// a normal network drop, so the frontend knows not to silently auto-reconnect.
const ACCESS_LOST_CLOSE_CODE = 4001;

/**
 * Subscribes to the "document-events" Redis pub/sub channel published by the
 * Spring backend, and closes the relevant active WebSocket session(s) the
 * moment a document is deleted or a user's access is revoked — otherwise an
 * already-open socket would keep working even after the underlying access
 * no longer exists.
 *
 * Needs a dedicated Redis connection: once a client issues SUBSCRIBE it can't
 * run any other command, so this can't share the client used for document
 * state caching in redisAdapter.js.
 *
 * @param {Map<string, Set<WebSocket>>} documentConnections - same Map server.js tracks live sockets in, keyed by yjsRoomId
 */
function startDocumentEventSubscriber(documentConnections) {
    const subscriber = new Redis({
        host: process.env.REDIS_HOST || 'localhost',
        port: process.env.REDIS_PORT || 6379,
        password: process.env.REDIS_PASSWORD,
        db: process.env.REDIS_DATABASE || 0,
        retryStrategy: (times) => Math.min(times * 50, 2000),
    });

    subscriber.on('connect', () => {
        logger.info('Document-events subscriber connected to Redis');
    });

    subscriber.on('error', (error) => {
        logger.error('Document-events subscriber Redis error', { error: error.message });
    });

    subscriber.subscribe(CHANNEL, (error) => {
        if (error) {
            logger.error('Failed to subscribe to document-events channel', { error: error.message });
        } else {
            logger.info('Subscribed to document-events channel');
        }
    });

    subscriber.on('message', (channel, message) => {
        if (channel !== CHANNEL) return;

        let event;
        try {
            event = JSON.parse(message);
        } catch (error) {
            logger.error('Failed to parse document event', { message, error: error.message });
            return;
        }

        handleEvent(event, documentConnections);
    });

    return subscriber;
}

function handleEvent(event, documentConnections) {
    const { event: type, yjsRoomId, userId } = event;
    const connections = documentConnections.get(yjsRoomId);
    if (!connections || connections.size === 0) return;

    if (type === 'DOCUMENT_DELETED') {
        const targets = Array.from(connections);
        closeConnections(targets, 'Document deleted');
        logger.info('Closed all sessions for deleted document', { yjsRoomId, count: targets.length });
    } else if (type === 'PERMISSION_REVOKED') {
        const targets = Array.from(connections)
            .filter((ws) => String(ws.userInfo?.userId) === String(userId));
        if (targets.length > 0) {
            closeConnections(targets, 'Your access to this document was removed');
            logger.info('Closed session(s) for revoked access', { yjsRoomId, userId, count: targets.length });
        }
    } else {
        logger.debug('Ignoring unhandled document event type', { type });
    }
}

function closeConnections(targets, reason) {
    targets.forEach((ws) => {
        try {
            ws.close(ACCESS_LOST_CLOSE_CODE, reason);
        } catch (error) {
            logger.error('Failed to close websocket', { error: error.message });
        }
    });
}

module.exports = { startDocumentEventSubscriber, ACCESS_LOST_CLOSE_CODE };

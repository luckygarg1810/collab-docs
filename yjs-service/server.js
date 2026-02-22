require('dotenv').config();
const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const Y = require('yjs');
const cors = require('cors');
const { encoding, decoding } = require('lib0');
const awarenessProtocol = require('y-protocols/awareness');
const logger = require('./config/logger');
const { authenticateConnection } = require('./utils/auth');
const {
    getDocument,
    getAwareness,
    applyUpdate,
    getStateVector,
    getStateAsUpdate,
    saveDocument,
    closeDocument,
    saveAllDocuments,
    startCleanupInterval,
    stopCleanupInterval,
    updateActivityTimestamp,
    getStats
} = require('./services/yjsHandler');
const {
    addActiveUser,
    removeActiveUser,
    getActiveUsers,
    closeRedis
} = require('./services/redisAdapter');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ noServer: true });

const PORT = process.env.PORT || 3000;

// CORS — allow the frontend origin for all HTTP routes
const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS || 'http://localhost:5173')
    .split(',').map(o => o.trim());

app.use(cors({
    origin: (origin, callback) => {
        // Allow requests with no origin (curl, server-to-server)
        if (!origin || ALLOWED_ORIGINS.includes(origin)) {
            callback(null, true);
        } else {
            callback(new Error(`CORS: origin ${origin} not allowed`));
        }
    },
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization']
}));

// Message types from y-protocols
const MESSAGE_SYNC = 0;
const MESSAGE_AWARENESS = 1;

// Sync message types
const MESSAGE_SYNC_STEP1 = 0; // Client sends state vector
const MESSAGE_SYNC_STEP2 = 1; // Server sends missing updates
const MESSAGE_SYNC_UPDATE = 2; // Incremental update

// Track connections per document
const documentConnections = new Map();

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({
        status: 'UP',
        service: 'yjs-collaboration',
        timestamp: new Date().toISOString(),
        stats: getStats()
    });
});

// Active users endpoint
app.get('/api/documents/:documentId/users', async (req, res) => {
    try {
        const { documentId } = req.params;
        const users = await getActiveUsers(documentId);
        res.json({ users, count: users.length });
    } catch (error) {
        logger.error('Failed to get active users', { error: error.message });
        res.status(500).json({ error: 'Internal server error' });
    }
});

// WebSocket upgrade handler with authentication
server.on('upgrade', (request, socket, head) => {
    // Check WebSocket origin against allowed origins
    const origin = request.headers.origin;
    if (origin && !ALLOWED_ORIGINS.includes(origin)) {
        socket.write('HTTP/1.1 403 Forbidden\r\n\r\n');
        socket.destroy();
        logger.warn('WebSocket connection rejected: Origin not allowed', { origin });
        return;
    }

    // Authenticate the connection
    const userInfo = authenticateConnection(request);

    if (!userInfo) {
        socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
        socket.destroy();
        logger.warn('WebSocket connection rejected: Authentication failed');
        return;
    }

    // Extract document ID from URL path
    const url = new URL(request.url, `http://${request.headers.host}`);
    const pathParts = url.pathname.split('/').filter(p => p);

    // Expected: /ws/yjs/{documentId}
    if (pathParts.length < 3 || pathParts[0] !== 'ws' || pathParts[1] !== 'yjs') {
        socket.write('HTTP/1.1 400 Bad Request\r\n\r\n');
        socket.destroy();
        logger.warn('Invalid WebSocket path', { path: url.pathname });
        return;
    }

    const documentId = pathParts[2];

    // Attach metadata to request for use in connection handler
    request.userInfo = userInfo;
    request.documentId = documentId;

    wss.handleUpgrade(request, socket, head, (ws) => {
        wss.emit('connection', ws, request);
    });
});

// WebSocket connection handler
wss.on('connection', async (ws, request) => {
    const { userInfo, documentId } = request;

    logger.info('WebSocket connection established', {
        documentId,
        userId: userInfo.userId,
        email: userInfo.email
    });

    // Track connection
    if (!documentConnections.has(documentId)) {
        documentConnections.set(documentId, new Set());
    }
    documentConnections.get(documentId).add(ws);


    // Add user to active users in Redis
    await addActiveUser(documentId, userInfo.userId, {
        name: userInfo.name,
        email: userInfo.email,
    });

    // Get or create Yjs document and awareness
    const ydoc = await getDocument(documentId);
    const awareness = getAwareness(documentId);

    // Update activity timestamp on connection (tracks read-only viewers)
    updateActivityTimestamp(documentId);

    // Store metadata on WebSocket
    ws.documentId = documentId;
    ws.userInfo = userInfo;
    ws.isAlive = true;

    // Track client IDs from awareness updates for proper cleanup
    // Client IDs come from the client's Y.Doc, not the server's
    ws.yjsClientIDs = new Set();

    // Track awareness updates to capture client IDs
    const awarenessUpdateHandler = ({ added, updated, removed }) => {
        // Add new client IDs to our tracking set
        added.forEach(id => ws.yjsClientIDs.add(id));
        updated.forEach(id => ws.yjsClientIDs.add(id));
    };

    awareness.on('update', awarenessUpdateHandler);
    ws.awarenessUpdateHandler = awarenessUpdateHandler; // Store for cleanup

    // Send initial sync message (Step 1: send state vector)
    try {
        const stateVector = await getStateVector(documentId);
        const encoder = encoding.createEncoder();
        encoding.writeVarUint(encoder, MESSAGE_SYNC);
        encoding.writeVarUint(encoder, MESSAGE_SYNC_STEP1);
        encoding.writeVarUint8Array(encoder, stateVector);
        ws.send(encoding.toUint8Array(encoder));

        logger.debug('Sent initial sync to client', { documentId, userId: userInfo.userId });
    } catch (error) {
        logger.error('Failed to send initial sync', {
            documentId,
            error: error.message
        });
    }

    // Handle incoming messages
    ws.on('message', async (message) => {
        try {
            const data = new Uint8Array(message);
            const decoder = decoding.createDecoder(data);
            const messageType = decoding.readVarUint(decoder);

            if (messageType === MESSAGE_SYNC) {
                await handleSyncMessage(ws, decoder, documentId, ydoc);
            } else if (messageType === MESSAGE_AWARENESS) {
                handleAwarenessMessage(ws, decoder, documentId, awareness);
            } else {
                logger.warn('Unknown message type received', { messageType, documentId });
            }
        } catch (error) {
            logger.error('Error processing message', {
                documentId,
                userId: userInfo.userId,
                error: error.message
            });
        }
    });

    // Handle pong (heartbeat response)
    ws.on('pong', () => {
        ws.isAlive = true;
    });

    // Handle connection close
    ws.on('close', async () => {
        logger.info('WebSocket connection closed', {
            documentId,
            userId: userInfo.userId
        });

        // Remove from connections
        const connections = documentConnections.get(documentId);
        if (connections) {
            connections.delete(ws);
            if (connections.size === 0) {
                documentConnections.delete(documentId);
                // Save document when last user disconnects
                await saveDocument(documentId);
            }
        }

        // Remove from active users
        await removeActiveUser(documentId, userInfo.userId);

        // Remove from awareness (using tracked client IDs from awareness updates)
        if (awareness && ws.yjsClientIDs && ws.yjsClientIDs.size > 0) {
            const clientIDs = Array.from(ws.yjsClientIDs);
            awarenessProtocol.removeAwarenessStates(
                awareness,
                clientIDs,
                null
            );
            logger.debug('Removed awareness states for clients', {
                documentId,
                clientIDs
            });
        }

        // Remove awareness update handler
        if (awareness && ws.awarenessUpdateHandler) {
            awareness.off('update', ws.awarenessUpdateHandler);
        }
    });

    // Handle errors
    ws.on('error', (error) => {
        logger.error('WebSocket error', {
            documentId,
            userId: userInfo.userId,
            error: error.message
        });
    });
});

/**
 * Handle sync protocol messages
 */
async function handleSyncMessage(ws, decoder, documentId, ydoc) {
    const syncType = decoding.readVarUint(decoder);

    if (syncType === MESSAGE_SYNC_STEP1) {
        // Client sent state vector, respond with missing updates
        const clientStateVector = decoding.readVarUint8Array(decoder);
        const stateUpdate = await getStateAsUpdate(documentId, clientStateVector);

        const encoder = encoding.createEncoder();
        encoding.writeVarUint(encoder, MESSAGE_SYNC);
        encoding.writeVarUint(encoder, MESSAGE_SYNC_STEP2);
        encoding.writeVarUint8Array(encoder, stateUpdate);
        ws.send(encoding.toUint8Array(encoder));

        logger.debug('Sent sync step 2', { documentId });

    } else if (syncType === MESSAGE_SYNC_STEP2) {
        // Client sent missing updates
        const update = decoding.readVarUint8Array(decoder);
        await applyUpdate(documentId, update);

    } else if (syncType === MESSAGE_SYNC_UPDATE) {
        // Incremental update from client
        const update = decoding.readVarUint8Array(decoder);
        await applyUpdate(documentId, update);

        // Broadcast to other clients in the same document
        broadcastUpdate(documentId, update, ws);
    }
}

/**
 * Handle awareness protocol messages
 */
function handleAwarenessMessage(ws, decoder, documentId, awareness) {
    if (!awareness) return;

    // Update activity timestamp on awareness update
    updateActivityTimestamp(documentId);

    const awarenessUpdate = decoding.readVarUint8Array(decoder);
    awarenessProtocol.applyAwarenessUpdate(awareness, awarenessUpdate, ws);

    // Broadcast awareness to other clients
    broadcastAwareness(documentId, awarenessUpdate, ws);
}

/**
 * Broadcast Yjs update to all connected clients except sender
 */
function broadcastUpdate(documentId, update, excludeWs) {
    const connections = documentConnections.get(documentId);
    if (!connections) return;

    const encoder = encoding.createEncoder();
    encoding.writeVarUint(encoder, MESSAGE_SYNC);
    encoding.writeVarUint(encoder, MESSAGE_SYNC_UPDATE);
    encoding.writeVarUint8Array(encoder, update);
    const message = encoding.toUint8Array(encoder);

    let broadcastCount = 0;
    connections.forEach((client) => {
        if (client !== excludeWs && client.readyState === WebSocket.OPEN) {
            client.send(message);
            broadcastCount++;
        }
    });

    logger.debug('Broadcasted update', { documentId, recipients: broadcastCount });
}

/**
 * Broadcast awareness update to all connected clients except sender
 */
function broadcastAwareness(documentId, awarenessUpdate, excludeWs) {
    const connections = documentConnections.get(documentId);
    if (!connections) return;

    const encoder = encoding.createEncoder();
    encoding.writeVarUint(encoder, MESSAGE_AWARENESS);
    encoding.writeVarUint8Array(encoder, awarenessUpdate);
    const message = encoding.toUint8Array(encoder);

    connections.forEach((client) => {
        if (client !== excludeWs && client.readyState === WebSocket.OPEN) {
            client.send(message);
        }
    });
}

// Heartbeat to detect broken connections
const heartbeatInterval = setInterval(() => {
    wss.clients.forEach((ws) => {
        if (ws.isAlive === false) {
            logger.warn('Terminating inactive connection', {
                documentId: ws.documentId,
                userId: ws.userInfo?.userId
            });
            return ws.terminate();
        }

        ws.isAlive = false;
        ws.ping();
    });
}, 30000); // Every 30 seconds

// Start periodic cleanup of inactive documents
const cleanupInterval = startCleanupInterval();

// Graceful shutdown
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);

async function shutdown() {
    logger.info('Shutting down server gracefully...');

    clearInterval(heartbeatInterval);


    // Stop cleanup interval
    stopCleanupInterval(cleanupInterval);

    // Save all active documents before shutdown
    logger.info('Saving all active documents before shutdown...');
    try {
        const result = await saveAllDocuments();
        logger.info('Pre-shutdown save completed', {
            total: result.total,
            saved: result.saved,
            failed: result.failed
        });
    } catch (error) {
        logger.error('Error during pre-shutdown save', { error: error.message });
    }

    // Close all WebSocket connections
    wss.clients.forEach((ws) => {
        ws.close(1000, 'Server shutting down');
    });

    // Close server
    server.close(() => {
        logger.info('HTTP server closed');
    });

    // Close Redis connection
    await closeRedis();

    logger.info('Graceful shutdown complete');
    process.exit(0);
}

// Start server
server.listen(PORT, () => {
    logger.info(`Yjs collaboration service started on port ${PORT}`);
    logger.info(`WebSocket endpoint: ws://localhost:${PORT}/ws/yjs/{documentId}`);
});

module.exports = server;

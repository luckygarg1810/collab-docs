require('dotenv').config();
const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const Y = require('yjs');
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

    // Store metadata on WebSocket
    ws.documentId = documentId;
    ws.userInfo = userInfo;
    ws.isAlive = true;

    // CRITICAL: Track Yjs client ID for awareness cleanup
    // Each Y.Doc has a unique clientID that awareness protocol uses
    ws.yjsClientID = ydoc.clientID;

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

        // Remove from awareness (using the tracked Yjs client ID)
        if (awareness && ws.yjsClientID !== undefined) {
            awarenessProtocol.removeAwarenessStates(
                awareness,
                [ws.yjsClientID], // ✅ Now using the correct Yjs client ID
                null
            );
            logger.debug('Removed awareness state for client', {
                documentId,
                yjsClientID: ws.yjsClientID
            });
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

// Graceful shutdown
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);

async function shutdown() {
    logger.info('Shutting down server...');

    clearInterval(heartbeatInterval);

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

    process.exit(0);
}

// Start server
server.listen(PORT, () => {
    logger.info(`Yjs collaboration service started on port ${PORT}`);
    logger.info(`WebSocket endpoint: ws://localhost:${PORT}/ws/yjs/{documentId}`);
});

module.exports = server;

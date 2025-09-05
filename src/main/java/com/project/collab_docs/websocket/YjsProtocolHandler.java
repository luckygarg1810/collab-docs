package com.project.collab_docs.websocket;

import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Slf4j
public class YjsProtocolHandler {
    // Yjs protocol message types
    public static final byte MSG_SYNC = 0;
    public static final byte MSG_AWARENESS = 1;
    public static final byte MSG_AUTH = 2; // Optional authentication
    public static final byte MSG_QUERY_AWARENESS = 3;

    // Sync message subtypes
    public static final byte SYNC_STEP_1 = 0; // Client requests sync
    public static final byte SYNC_STEP_2 = 1; // Server sends state
    public static final byte SYNC_UPDATE = 2; // Update message

    // Protocol constants
    private static final int MAX_MESSAGE_SIZE = 16 * 1024 * 1024; // 16MB max
    private static final int MIN_MESSAGE_SIZE = 1; // At least message type

    /**
     * Parse incoming Yjs protocol message with validation
     */
    public YjsMessage parseMessage(byte[] data) throws YjsProtocolException {
        if (!isValidMessage(data)) {
            throw new YjsProtocolException("Invalid message format or size");
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            byte messageType = buffer.get();

            switch (messageType) {
                case MSG_SYNC:
                    return parseSyncMessage(buffer);
                case MSG_AWARENESS:
                    return parseAwarenessMessage(buffer);
                case MSG_QUERY_AWARENESS:
                    return new YjsMessage(MSG_QUERY_AWARENESS, null, new byte[0], null);
                case MSG_AUTH:
                    return parseAuthMessage(buffer);
                default:
                    log.warn("Unknown Yjs message type: {}", messageType);
                    throw new YjsProtocolException("Unknown message type: " + messageType);
            }
        } catch (Exception e) {
            log.error("Error parsing Yjs message: {}", e.getMessage());
            throw new YjsProtocolException("Failed to parse message", e);
        }
    }

    /**
     * Validate message format and size
     */
    private boolean isValidMessage(byte[] data) {
        if (data == null) {
            log.warn("Received null message data");
            return false;
        }
        
        if (data.length < MIN_MESSAGE_SIZE) {
            log.warn("Message too small: {} bytes", data.length);
            return false;
        }
        
        if (data.length > MAX_MESSAGE_SIZE) {
            log.warn("Message too large: {} bytes", data.length);
            return false;
        }
        
        return true;
    }

    /**
     * Parse sync-type messages with enhanced validation
     */
    private YjsMessage parseSyncMessage(ByteBuffer buffer) throws YjsProtocolException {
        if (!buffer.hasRemaining()) {
            throw new YjsProtocolException("Invalid sync message: missing sync type");
        }

        byte syncType = buffer.get();
        
        // Validate sync type
        if (syncType < SYNC_STEP_1 || syncType > SYNC_UPDATE) {
            throw new YjsProtocolException("Invalid sync type: " + syncType);
        }
        
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);

        log.debug("Parsed sync message - type: {}, payload size: {} bytes", syncType, payload.length);
        return new YjsMessage(MSG_SYNC, syncType, payload, null);
    }

    /**
     * Parse awareness messages (user presence) with validation
     */
    private YjsMessage parseAwarenessMessage(ByteBuffer buffer) throws YjsProtocolException {
        if (!buffer.hasRemaining()) {
            log.debug("Empty awareness message received");
            return new YjsMessage(MSG_AWARENESS, null, new byte[0], null);
        }
        
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        
        log.debug("Parsed awareness message - payload size: {} bytes", payload.length);
        return new YjsMessage(MSG_AWARENESS, null, payload, null);
    }

    /**
     * Parse authentication messages
     */
    private YjsMessage parseAuthMessage(ByteBuffer buffer) throws YjsProtocolException {
        if (!buffer.hasRemaining()) {
            throw new YjsProtocolException("Invalid auth message: missing auth data");
        }
        
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        
        log.debug("Parsed auth message - payload size: {} bytes", payload.length);
        return new YjsMessage(MSG_AUTH, null, payload, null);
    }

    /**
     * Create Sync Step 1 message (client/server requests sync)
     */
    public byte[] createSyncStep1Message(byte[] stateVector) {
        if (stateVector == null) stateVector = new byte[0];
        
        ByteBuffer buffer = ByteBuffer.allocate(2 + stateVector.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(MSG_SYNC);
        buffer.put(SYNC_STEP_1);
        buffer.put(stateVector);
        
        log.debug("Created sync step 1 message - state vector size: {} bytes", stateVector.length);
        return buffer.array();
    }

    /**
     * Create Sync Step 2 message (server sends current state)
     */
    public byte[] createSyncStep2Message(byte[] stateVector, byte[] update) {
        if (stateVector == null) stateVector = new byte[0];
        if (update == null) update = new byte[0];
        
        ByteBuffer buffer = ByteBuffer.allocate(2 + stateVector.length + update.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(MSG_SYNC);
        buffer.put(SYNC_STEP_2);
        buffer.put(stateVector);
        buffer.put(update);
        
        log.debug("Created sync step 2 message - state vector: {} bytes, update: {} bytes", 
                stateVector.length, update.length);
        return buffer.array();
    }

    /**
     * Create update message
     */
    public byte[] createUpdateMessage(byte[] update) {
        if (update == null || update.length == 0) {
            log.warn("Attempting to create update message with empty update");
            return new byte[0];
        }
        
        ByteBuffer buffer = ByteBuffer.allocate(2 + update.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(MSG_SYNC);
        buffer.put(SYNC_UPDATE);
        buffer.put(update);
        
        log.debug("Created update message - update size: {} bytes", update.length);
        return buffer.array();
    }

    /**
     * Create awareness message
     */
    public byte[] createAwarenessMessage(byte[] awarenessUpdate) {
        if (awarenessUpdate == null) awarenessUpdate = new byte[0];
        
        ByteBuffer buffer = ByteBuffer.allocate(1 + awarenessUpdate.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(MSG_AWARENESS);
        buffer.put(awarenessUpdate);
        
        log.debug("Created awareness message - update size: {} bytes", awarenessUpdate.length);
        return buffer.array();
    }

    /**
     * Create query awareness message
     */
    public byte[] createQueryAwarenessMessage() {
        ByteBuffer buffer = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(MSG_QUERY_AWARENESS);
        
        log.debug("Created query awareness message");
        return buffer.array();
    }

    /**
     * Create authentication message
     */
    public byte[] createAuthMessage(byte[] authData) {
        if (authData == null) authData = new byte[0];
        
        ByteBuffer buffer = ByteBuffer.allocate(1 + authData.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(MSG_AUTH);
        buffer.put(authData);
        
        log.debug("Created auth message - auth data size: {} bytes", authData.length);
        return buffer.array();
    }

    /**
     * Check if message is a sync request that needs a response
     */
    public boolean requiresSyncResponse(YjsMessage message) {
        return message != null && 
               message.isSyncMessage() && 
               (message.isSyncStep1() || message.isSyncUpdate());
    }

    /**
     * Check if message is an awareness query that needs a response
     */
    public boolean requiresAwarenessResponse(YjsMessage message) {
        return message != null && message.isQueryAwarenessMessage();
    }
}

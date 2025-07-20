package com.project.collab_docs.websocket;

import com.project.collab_docs.service.YjsCollaborationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@RequiredArgsConstructor
@Slf4j
public class YjsWebSocketHandler extends BinaryWebSocketHandler{
    private final YjsCollaborationService yjsCollaborationService;

    // Track active sessions per document room
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> documentSessions = new ConcurrentHashMap<>();
    // Track document room for each session
    private final ConcurrentHashMap<String, String> sessionToRoom = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception{
        try{
            String documentId = extractDocumentId(session);
            if (documentId == null) {
                log.warn("No document ID found in WebSocket connection, closing session: {}", session.getId());
                session.close(CloseStatus.BAD_DATA.withReason("Document ID required"));
                return;
            }

            // Add session to document room
            documentSessions.computeIfAbsent(documentId, k -> new CopyOnWriteArraySet<>()).add(session);
            sessionToRoom.put(session.getId(), documentId);

            log.info("WebSocket connection established for document: {} with session: {}", documentId, session.getId());
            log.info("Active sessions for document {}: {}", documentId, documentSessions.get(documentId).size());

        }catch (Exception e) {
            log.error("Error establishing WebSocket connection for session {}: {}", session.getId(), e.getMessage(), e);
            try {
                session.close(CloseStatus.SERVER_ERROR.withReason("Connection setup failed"));
            } catch (IOException ioException) {
                log.error("Error closing session after setup failure: {}", ioException.getMessage());
            }
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        try{
            String documentId = sessionToRoom.get(session.getId());
            if (documentId == null) {
                log.warn("No document room found for session: {}", session.getId());
                return;
            }

            byte[] updateData = message.getPayload().array();
            log.debug("Received Yjs update from session {}: {} bytes", session.getId(), updateData.length);
            // Process the Yjs update
            yjsCollaborationService.processYjsUpdate(documentId, updateData);

            // Broadcast update to all other sessions in the same document room
            CopyOnWriteArraySet<WebSocketSession> sessions = documentSessions.get(documentId);
            if (sessions != null) {
                int broadcastCount = 0;
                for (WebSocketSession otherSession : sessions) {
                    if (!otherSession.getId().equals(session.getId()) && otherSession.isOpen()) {
                        try {
                            otherSession.sendMessage(new BinaryMessage(updateData));
                            broadcastCount++;
                        } catch (IOException e) {
                            log.error("Error broadcasting to session {}: {}", otherSession.getId(), e.getMessage());
                            // Remove problematic session
                            sessions.remove(otherSession);
                            sessionToRoom.remove(otherSession.getId());
                        }
                    }
                }
                log.debug("Broadcasted update to {} sessions for document: {}", broadcastCount, documentId);
            }
        }catch (Exception e) {
            log.error("Error handling binary message from session {}: {}", session.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage(), exception);
        cleanupSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        log.info("WebSocket connection closed for session {}: {}", session.getId(), closeStatus);
        cleanupSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false; // Yjs updates should be complete binary messages
    }

    /**
     * Extract document ID from WebSocket URI path
     * Expected pattern: /ws/yjs/{documentId}
     */
    private String extractDocumentId(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri != null) {
                String path = uri.getPath();
                String[] pathSegments = path.split("/");

                // Expected: ["", "ws", "yjs", "{documentId}"]
                if (pathSegments.length >= 4) {
                    String documentId = pathSegments[3];
                    log.debug("Extracted document ID: {} from path: {}", documentId, path);
                    return documentId;
                }
            }
            log.warn("Could not extract document ID from WebSocket URI: {}", uri);
            return null;
        } catch (Exception e) {
            log.error("Error extracting document ID from WebSocket session: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Clean up session tracking when connection is closed or error occurs
     */
    private void cleanupSession(WebSocketSession session) {
        try {
            String documentId = sessionToRoom.remove(session.getId());
            if (documentId != null) {
                CopyOnWriteArraySet<WebSocketSession> sessions = documentSessions.get(documentId);
                if (sessions != null) {
                    sessions.remove(session);

                    // Remove empty document rooms
                    if (sessions.isEmpty()) {
                        documentSessions.remove(documentId);
                        log.info("Removed empty document room: {}", documentId);
                    } else {
                        log.info("Removed session from document {}: {} active sessions remaining",
                                documentId, sessions.size());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error cleaning up session {}: {}", session.getId(), e.getMessage(), e);
        }
    }

    /**
     * Get active session count for a document (useful for monitoring)
     */
    public int getActiveSessionCount(String documentId) {
        CopyOnWriteArraySet<WebSocketSession> sessions = documentSessions.get(documentId);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * Get total active sessions across all documents
     */
    public int getTotalActiveSessionCount() {
        return sessionToRoom.size();
    }
}

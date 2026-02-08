package com.project.collab_docs.websocket;

import com.project.collab_docs.entities.Document;
import com.project.collab_docs.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * DEPRECATED: This Spring Boot WebSocket handler is kept for backward
 * compatibility only.
 * All new clients should use the Node.js Yjs microservice at
 * ws://yjs-service:3000/ws/yjs/{documentId}
 * 
 * The Node.js service provides:
 * - Proper Yjs CRDT operations with y-protocols
 * - Redis persistence and state management
 * - Awareness protocol for presence tracking
 * - JWT authentication and permission checks
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class YjsWebSocketHandler extends BinaryWebSocketHandler {

    private final DocumentRepository documentRepository;

    // Track active sessions per document room
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> documentSessions = new ConcurrentHashMap<>();
    // Track document room for each session
    private final ConcurrentHashMap<String, String> sessionToRoom = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            // Extract user information from session attributes (set by
            // JwtHandshakeInterceptor)
            Long userId = (Long) session.getAttributes().get("userId");
            String username = (String) session.getAttributes().get("username");

            if (userId == null || username == null) {
                log.warn("No user information found in session, rejecting connection: {}", session.getId());
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Authentication required"));
                return;
            }

            String documentId = extractDocumentId(session);
            if (documentId == null) {
                log.warn("No document ID found in WebSocket connection, closing session: {}", session.getId());
                session.close(CloseStatus.BAD_DATA.withReason("Document ID required"));
                return;
            }

            // Verify user has permission to access this document
            if (!canUserAccessDocument(userId, documentId)) {
                log.warn("User {} ({}) does not have permission to access document {}",
                        username, userId, documentId);
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Access denied"));
                return;
            }

            // Add session to document room
            documentSessions.computeIfAbsent(documentId, k -> new CopyOnWriteArraySet<>()).add(session);
            sessionToRoom.put(session.getId(), documentId);

            // Store userId in session for later use
            session.getAttributes().put("documentId", documentId);

            log.info("WebSocket connection established for user {} (ID: {}) to document: {} (session: {})",
                    username, userId, documentId, session.getId());
            log.info("Active sessions for document {}: {}", documentId, documentSessions.get(documentId).size());

        } catch (Exception e) {
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
        try {
            String documentId = sessionToRoom.get(session.getId());
            if (documentId == null) {
                log.warn("No document room found for session: {}", session.getId());
                return;
            }

            byte[] updateData = message.getPayload().array();
            log.debug("Received Yjs update from session {}: {} bytes", session.getId(), updateData.length);

            // DEPRECATED: This endpoint no longer processes Yjs updates
            // All Yjs operations should be handled by the Node.js microservice
            log.warn(
                    "Received update on DEPRECATED Spring Boot WebSocket. Clients should migrate to ws://localhost:3000/ws/yjs/{}",
                    documentId);

            // Broadcast update to all other sessions in the same document room (for
            // backward compatibility only)
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
        } catch (Exception e) {
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
     * Check if user has permission to access a document
     * User can access document if:
     * 1. They are the owner
     * 2. Document is shared with them (future: check collaborators table)
     * 3. Document is public (future enhancement)
     */
    private boolean canUserAccessDocument(Long userId, String yjsRoomId) {
        try {
            Optional<Document> documentOpt = documentRepository.findByYjsRoomIdAndIsDeletedFalse(yjsRoomId);

            if (documentOpt.isEmpty()) {
                log.warn("Document not found for yjsRoomId: {}", yjsRoomId);
                return false;
            }

            Document document = documentOpt.get();

            // Check if user is the owner
            if (document.getOwner().getId().equals(userId)) {
                log.debug("User {} is owner of document {}", userId, yjsRoomId);
                return true;
            }

            // TODO: Phase 3 - Check if document is shared with user (collaborators table)
            // TODO: Phase 3 - Check document visibility (PUBLIC documents can be accessed
            // by anyone)

            log.warn("User {} does not have permission to access document {} (owner: {})",
                    userId, yjsRoomId, document.getOwner().getId());
            return false;

        } catch (Exception e) {
            log.error("Error checking document permissions: {}", e.getMessage(), e);
            return false;
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

package com.project.collab_docs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes document domain events (deletion, permission changes) on the
 * "document-events" Redis pub/sub channel. yjs-service subscribes to react
 * in real time (closing active WebSocket sessions that just lost access).
 *
 * Events are named after what happened (DOCUMENT_DELETED, PERMISSION_REVOKED),
 * not the mechanism that should react to them — keeps this reusable by future
 * subscribers (e.g. a notification service) without touching the publisher.
 *
 * Plain Redis pub/sub has no persistence: a message is lost if nobody is
 * subscribed at publish time. That's the correct behavior here (no active
 * session means nothing to close), but a future durable-notification use case
 * (notifying offline users) would need a separate, persisted mechanism —
 * this channel alone won't be sufficient for that.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentEventPublisher {

    private static final String CHANNEL = "document-events";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void publishDocumentDeleted(Long documentId, String yjsRoomId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "DOCUMENT_DELETED");
        payload.put("documentId", documentId);
        payload.put("yjsRoomId", yjsRoomId);
        payload.put("timestamp", Instant.now().toString());
        publish(payload);
    }

    public void publishPermissionRevoked(Long documentId, String yjsRoomId, Long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "PERMISSION_REVOKED");
        payload.put("documentId", documentId);
        payload.put("yjsRoomId", yjsRoomId);
        payload.put("userId", userId);
        payload.put("timestamp", Instant.now().toString());
        publish(payload);
    }

    private void publish(Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.convertAndSend(CHANNEL, json);
            log.info("Published document event: {}", json);
        } catch (Exception e) {
            // Non-fatal: the underlying DB change already happened. A missed
            // broadcast is caught later by the 15-min per-session hardcheck.
            log.error("Failed to publish document event {}: {}", payload.get("event"), e.getMessage(), e);
        }
    }
}

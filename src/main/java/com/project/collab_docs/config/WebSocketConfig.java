package com.project.collab_docs.config;

import com.project.collab_docs.websocket.YjsWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final YjsWebSocketHandler yjsWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        // Register Yjs WebSocket handler for document collaboration
        // Pattern: /ws/yjs/{documentId} where documentId is the Yjs room ID
        registry.addHandler(yjsWebSocketHandler, "/ws/yjs/{documentId}")
                .setAllowedOrigins("*") // Configure appropriately for production
                .withSockJS(); // Enable SockJS fallback for browsers that don't support WebSocket
    }
}

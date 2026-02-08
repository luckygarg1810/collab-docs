package com.project.collab_docs.config;

import com.project.collab_docs.websocket.JwtHandshakeInterceptor;
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
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        // Get allowed origins from environment or use default
        String allowedOrigins = System.getenv("FRONTEND_URL");
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = "http://localhost:3000,http://localhost:8080";
        }

        // Register Yjs WebSocket handler for document collaboration
        // Pattern: /ws/yjs/{documentId} where documentId is the Yjs room ID
        registry.addHandler(yjsWebSocketHandler, "/ws/yjs/{documentId}")
                .setAllowedOrigins(allowedOrigins.split(",")) // Environment-based CORS
                .addInterceptors(jwtHandshakeInterceptor) // JWT authentication
                .withSockJS(); // Enable SockJS fallback
    }
}

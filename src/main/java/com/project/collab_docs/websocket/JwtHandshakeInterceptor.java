package com.project.collab_docs.websocket;

import com.project.collab_docs.security.JwtUtil;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * WebSocket handshake interceptor for JWT authentication
 * Validates JWT token before allowing WebSocket upgrade
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {

        try {
            // Extract JWT token from request
            String token = extractJwtToken(request);

            if (token == null || token.isEmpty()) {
                log.warn("WebSocket handshake rejected: No JWT token provided");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // Validate token
            if (!jwtUtil.validateToken(token)) {
                log.warn("WebSocket handshake rejected: Invalid JWT token");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // Extract user information from token
            Claims claims = jwtUtil.getClaimsFromToken(token);
            String username = claims.getSubject();
            Long userId = claims.get("userId", Long.class);

            if (username == null || userId == null) {
                log.warn("WebSocket handshake rejected: Invalid token claims");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // Store user information in WebSocket session attributes
            attributes.put("username", username);
            attributes.put("userId", userId);
            attributes.put("jwt", token);

            log.info("WebSocket handshake successful for user: {} (ID: {})", username, userId);
            return true;

        } catch (Exception e) {
            log.error("Error during WebSocket handshake: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {

        if (exception != null) {
            log.error("WebSocket handshake failed: {}", exception.getMessage(), exception);
        }
    }

    /**
     * Extract JWT token from request
     * Supports token in:
     * 1. Cookie (HttpOnly JWT cookie)
     * 2. Query parameter (?token=xxx)
     * 3. Authorization header (Bearer xxx)
     */
    private String extractJwtToken(ServerHttpRequest request) {
        // Try cookie first (most secure for browser clients)
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();

            // Check for JWT cookie
            Cookie[] cookies = servletRequest.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("JWT".equals(cookie.getName())) {
                        log.debug("JWT token found in cookie");
                        return cookie.getValue();
                    }
                }
            }
        }

        // Try query parameter (for WebSocket clients that can't set cookies)
        String query = request.getURI().getQuery();
        if (query != null && query.contains("token=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("token=")) {
                    String token = param.substring(6);
                    log.debug("JWT token found in query parameter");
                    return token;
                }
            }
        }

        // Try Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            log.debug("JWT token found in Authorization header");
            return authHeader.substring(7);
        }

        log.debug("No JWT token found in request");
        return null;
    }
}

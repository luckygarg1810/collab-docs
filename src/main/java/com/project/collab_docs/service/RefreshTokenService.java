package com.project.collab_docs.service;

import com.project.collab_docs.entities.RefreshToken;
import com.project.collab_docs.entities.User;
import com.project.collab_docs.exception.InvalidRefreshTokenException;
import com.project.collab_docs.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Issues, rotates, and revokes server-tracked refresh tokens.
 *
 * The raw token is a cryptographically random opaque string, sent to the
 * client exactly once (inside an HttpOnly cookie). Only its SHA-256 hash is
 * ever persisted, so a database read alone can never be replayed as a valid
 * credential — the same principle used for password storage.
 *
 * Rotation: every successful refresh revokes the presented token and issues
 * a brand new one. If a token that's already been revoked is presented again,
 * that's a reuse signal (e.g. a stolen cookie being replayed after the
 * legitimate client already rotated past it) — the whole session family for
 * that user is revoked in response.
 */
@Service
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    // Self-injected proxy, needed so revokeAllForUserInNewTransaction() below
    // goes through Spring's AOP proxy (and therefore actually opens a new
    // transaction) even when called from another method on this same bean —
    // a plain `this.foo()` call bypasses the proxy and @Transactional entirely.
    private final RefreshTokenService self;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, @Lazy RefreshTokenService self) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.self = self;
    }

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    /**
     * Result of a successful validate-and-rotate: the new raw token to send
     * to the client, and the user it belongs to.
     */
    public record RotationResult(String rawToken, User user) {
    }

    @Transactional
    public String issueToken(User user) {
        String rawToken = generateRawToken();

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(LocalDateTime.now().plus(java.time.Duration.ofMillis(refreshExpirationMs)))
                .revoked(false)
                .build();

        refreshTokenRepository.save(token);
        log.info("Issued refresh token for user {}", user.getEmail());
        return rawToken;
    }

    @Transactional
    public RotationResult validateAndRotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token missing");
        }

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not recognized"));

        if (stored.getRevoked()) {
            // This exact token was already rotated away once before — someone is
            // replaying an old value. Treat it as theft and kill every session
            // for this user so the legitimate device has to log in again too.
            //
            // This runs in its own REQUIRES_NEW transaction: the exception thrown
            // right after this would otherwise roll back the current transaction
            // (Spring's default behavior for unchecked exceptions) and silently
            // undo the very revocation meant to respond to the theft attempt.
            log.warn("Refresh token reuse detected for user {} — revoking all sessions",
                    stored.getUser().getEmail());
            self.revokeAllForUserInNewTransaction(stored.getUser());
            throw new InvalidRefreshTokenException("Refresh token has already been used");
        }

        if (stored.isExpired()) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        // Rotate: revoke this one, issue a fresh one
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        String newRawToken = issueToken(stored.getUser());
        return new RotationResult(newRawToken, stored.getUser());
    }

    /**
     * Revoke every token for a user in its own transaction, independent of the
     * caller's — so it survives even when the caller is about to roll back.
     * Package-visible-by-convention only via the self-proxy; not part of the
     * public API other services should call directly.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUserInNewTransaction(User user) {
        refreshTokenRepository.revokeAllForUser(user);
    }

    /**
     * Revoke a single token (best-effort — used on logout). Silently no-ops
     * if the token doesn't exist or is already revoked.
     */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JVM — this can't actually happen.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

package com.project.collab_docs.exception;

/**
 * Thrown when a refresh token is missing, malformed, expired, revoked,
 * or has already been used once (rotation reuse — a theft signal).
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}

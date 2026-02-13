package com.project.collab_docs.exception;

/**
 * Exception thrown when a share link is invalid, expired, or cannot be used.
 */
public class InvalidShareLinkException extends RuntimeException {

    public InvalidShareLinkException(String message) {
        super(message);
    }

    public InvalidShareLinkException(String message, Throwable cause) {
        super(message, cause);
    }
}


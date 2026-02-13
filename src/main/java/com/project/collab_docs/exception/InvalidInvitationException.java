package com.project.collab_docs.exception;

/**
 * Exception thrown when an invitation is invalid or cannot be processed.
 */
public class InvalidInvitationException extends RuntimeException {

    public InvalidInvitationException(String message) {
        super(message);
    }

    public InvalidInvitationException(String message, Throwable cause) {
        super(message, cause);
    }
}


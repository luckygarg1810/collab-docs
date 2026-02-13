package com.project.collab_docs.exception;

/**
 * Exception thrown when a user already has an invitation or permission.
 */
public class DuplicateInvitationException extends RuntimeException {

    public DuplicateInvitationException(String message) {
        super(message);
    }

    public DuplicateInvitationException(String message, Throwable cause) {
        super(message, cause);
    }
}


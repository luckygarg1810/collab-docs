package com.project.collab_docs.exception;

/**
 * Exception thrown when a user attempts an action they don't have permission
 * for
 */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String message) {
        super(message);
    }

    public PermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}

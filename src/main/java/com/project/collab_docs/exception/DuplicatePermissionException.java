package com.project.collab_docs.exception;

/**
 * Exception thrown when attempting to create a duplicate permission
 */
public class DuplicatePermissionException extends RuntimeException {

    public DuplicatePermissionException(String message) {
        super(message);
    }

    public DuplicatePermissionException(String message, Throwable cause) {
        super(message, cause);
    }
}

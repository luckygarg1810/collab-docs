package com.project.collab_docs.exception;

/**
 * Exception thrown when version limit is exceeded for a document
 */
public class VersionLimitExceededException extends RuntimeException {

    public VersionLimitExceededException(String message) {
        super(message);
    }

    public VersionLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}


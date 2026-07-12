package com.project.collab_docs.exception;

/**
 * Thrown when a request to an internal-service-only endpoint is missing the
 * X-Internal-Service-Key header or presents the wrong value.
 */
public class InvalidServiceKeyException extends RuntimeException {

    public InvalidServiceKeyException(String message) {
        super(message);
    }
}

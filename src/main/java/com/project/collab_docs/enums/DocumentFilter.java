package com.project.collab_docs.enums;

/**
 * Which subset of the caller's accessible documents to return from
 * GET /api/documents/user/documents.
 */
public enum DocumentFilter {
    ALL,
    OWNED,
    SHARED,
    STARRED
}

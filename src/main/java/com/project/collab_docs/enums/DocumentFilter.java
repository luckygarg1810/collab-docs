package com.project.collab_docs.enums;

/**
 * Which subset of the caller's accessible documents to return from
 * GET /api/documents/user/documents.
 */
public enum DocumentFilter {
    ALL,
    OWNED,
    SHARED,
    STARRED,
    RECENT,
    // Owner's own Recycle Bin — deleted documents. Deliberately not visible
    // to other collaborators; a document a user no longer has access to
    // just isn't shown, it doesn't show up in *their* trash.
    TRASH
}

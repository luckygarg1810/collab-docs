package com.project.collab_docs.enums;

/**
 * Role-based access control levels for document permissions.
 * Hierarchy: OWNER > EDITOR > VIEWER
 */
public enum Role {
    /**
     * Full control over document: can edit, delete, share, and manage permissions
     */
    OWNER("Can manage document, permissions, and delete"),

    /**
     * Can edit document content but cannot delete or manage permissions
     */
    EDITOR("Can edit document content"),

    /**
     * Read-only access to document
     */
    VIEWER("Read-only access");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this role has at least the required permission level.
     * Uses enum ordinal for hierarchy: OWNER(0) > EDITOR(1) > VIEWER(2)
     * 
     * @param required The minimum required role
     * @return true if this role has sufficient permissions
     * 
     * @example OWNER.hasPermissionOf(EDITOR) returns true
     * @example VIEWER.hasPermissionOf(EDITOR) returns false
     */
    public boolean hasPermissionOf(Role required) {
        return this.ordinal() <= required.ordinal();
    }
}

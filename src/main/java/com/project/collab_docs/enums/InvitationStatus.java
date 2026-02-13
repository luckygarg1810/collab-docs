package com.project.collab_docs.enums;

/**
 * Status of share invitations sent to users.
 */
public enum InvitationStatus {
    /**
     * Invitation has been sent but not yet accepted
     */
    PENDING("Invitation pending"),

    /**
     * User has accepted the invitation
     */
    ACCEPTED("Invitation accepted"),

    /**
     * User has declined the invitation
     */
    DECLINED("Invitation declined"),

    /**
     * Invitation has expired
     */
    EXPIRED("Invitation expired"),

    /**
     * Invitation was revoked by sender or document owner
     */
    REVOKED("Invitation revoked");

    private final String description;

    InvitationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if invitation is still actionable
     */
    public boolean isActionable() {
        return this == PENDING;
    }
}


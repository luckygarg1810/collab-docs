package com.project.collab_docs.dto.response;

import com.project.collab_docs.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for document access via share link.
 * Returned after successfully granting access to authenticated users.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentAccessResponse {

    /**
     * Document ID
     */
    private Long documentId;

    /**
     * Document title
     */
    private String title;

    /**
     * Yjs room ID for WebSocket connection
     */
    private String yjsRoomId;

    /**
     * Role granted via the share link (VIEWER or EDITOR)
     */
    private Role role;

    /**
     * Whether this is anonymous access (always false for Option 2: Force Registration)
     */
    @Builder.Default
    private Boolean isAnonymous = false;

    /**
     * Whether a new permission was granted (false if user already had access)
     */
    @Builder.Default
    private Boolean hasPermissionGranted = false;

    /**
     * When the share link access expires (null if permanent)
     */
    private LocalDateTime accessExpiresAt;

    /**
     * Message to display to user
     */
    private String message;

    /**
     * Document owner information
     */
    private String ownerName;
}


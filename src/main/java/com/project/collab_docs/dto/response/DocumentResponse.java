package com.project.collab_docs.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.project.collab_docs.enums.Role;
import com.project.collab_docs.enums.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentResponse {
    private Long id;
    private String title;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String yjsRoomId;
    private String ownerEmail;
    private String ownerName;
    private Boolean isTemplate;
    private Visibility visibility;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** Effective role of the requesting user (OWNER / EDITOR / VIEWER) */
    private Role userRole;
    /** Whether the requesting user has starred this document */
    private Boolean isStarred;
    /** When the requesting user last opened this document (null if never) */
    private LocalDateTime lastOpenedAt;
    /** When this document was moved to the Recycle Bin (null unless deleted) */
    private LocalDateTime deletedAt;

    /**
     * Extracted HTML content from an uploaded DOCX or PDF file.
     * Only set in the upload response — null for blank documents and all
     * other API responses. The frontend injects this into TipTap/Yjs after
     * the WebSocket sync event fires, then discards it from memory.
     * Never persisted to the database.
     */
    private String extractedHtml;
}

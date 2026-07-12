package com.project.collab_docs.dto.response;

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
}

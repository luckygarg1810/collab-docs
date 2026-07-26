package com.project.collab_docs.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for document version information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VersionResponse {

    private Long id;
    private Long documentId;
    private Integer versionNumber;
    private String versionName;
    private String changeNotes;
    private Long createdByUserId;
    private String createdByName;
    private String createdByEmail;
    private LocalDateTime createdAt;
    private Long sizeBytes;
    private String snapshotHash;
    private String versionIdentifier; // e.g., "v1", "v2"
    private String displayName; // e.g., "Final draft (v2)" or "Version 2"
    private boolean restoration; // true if auto-created as a restore's audit record, not manually saved

    /**
     * Create a response from entity
     */
    public static VersionResponse from(com.project.collab_docs.entities.DocumentVersion version) {
        return VersionResponse.builder()
                .id(version.getId())
                .documentId(version.getDocument().getId())
                .versionNumber(version.getVersionNumber())
                .versionName(version.getVersionName())
                .changeNotes(version.getChangeNotes())
                .createdByUserId(version.getCreatedBy().getId())
                .createdByName(version.getCreatedBy().getFirstName() + " " + version.getCreatedBy().getLastName())
                .createdByEmail(version.getCreatedBy().getEmail())
                .createdAt(version.getCreatedAt())
                .sizeBytes(version.getSizeBytes())
                .snapshotHash(version.getSnapshotHash())
                .versionIdentifier(version.getVersionIdentifier())
                .displayName(version.getDisplayName())
                .restoration(version.isRestoration())
                .build();
    }
}


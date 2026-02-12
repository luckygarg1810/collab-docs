package com.project.collab_docs.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for version content data
 * Used when fetching actual version snapshot for restoration or viewing
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VersionContentResponse {

    private Long versionId;
    private Integer versionNumber;
    private String versionName;
    private String contentSnapshot; // HTML/JSON content
    private byte[] yjsSnapshot; // Binary Yjs snapshot
    private Long sizeBytes;
    private String snapshotHash;
}


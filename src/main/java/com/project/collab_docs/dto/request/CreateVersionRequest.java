package com.project.collab_docs.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new document version
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateVersionRequest {

    /**
     * Optional user-provided name for this version
     * Examples: "Final draft", "Before review", "Version with new figures"
     */
    @Size(max = 255, message = "Version name must not exceed 255 characters")
    private String versionName;

    /**
     * Optional notes about what changed in this version
     */
    @Size(max = 5000, message = "Change notes must not exceed 5000 characters")
    private String changeNotes;
}


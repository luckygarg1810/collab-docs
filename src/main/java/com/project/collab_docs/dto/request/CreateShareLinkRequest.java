package com.project.collab_docs.dto.request;

import com.project.collab_docs.enums.Role;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a shareable link.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShareLinkRequest {

    /**
     * Role to grant via this link (VIEWER or EDITOR only)
     */
    @NotNull(message = "Role is required")
    private Role role;

    /**
     * Number of days until link expires (null for no expiration)
     */
    @Min(value = 1, message = "Expiration days must be at least 1")
    private Integer expiresInDays;

    /**
     * Maximum number of times the link can be used (null for unlimited)
     */
    @Min(value = 1, message = "Max uses must be at least 1")
    private Integer maxUses;

    /**
     * Whether authentication is required to use this link
     */
    @Builder.Default
    private Boolean requiresAuth = false;

    /**
     * Optional description for the link
     */
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
}


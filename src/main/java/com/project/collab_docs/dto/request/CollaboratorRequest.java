package com.project.collab_docs.dto.request;

import com.project.collab_docs.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for adding a collaborator to a document
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollaboratorRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Role is required")
    private Role role;

    /**
     * Optional expiration in days from now
     * Null means permanent access
     */
    private Integer expiresInDays;
}

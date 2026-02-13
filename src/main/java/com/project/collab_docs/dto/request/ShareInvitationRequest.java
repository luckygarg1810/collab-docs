package com.project.collab_docs.dto.request;

import com.project.collab_docs.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for sending a share invitation via email.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareInvitationRequest {

    /**
     * Email address to send invitation to
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * Role to grant upon acceptance
     */
    @NotNull(message = "Role is required")
    private Role role;

    /**
     * Optional personal message
     */
    @Size(max = 1000, message = "Message cannot exceed 1000 characters")
    private String message;
}


package com.project.collab_docs.dto;

import com.project.collab_docs.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for returning collaborator information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollaboratorResponse {

    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
    private Role role;
    private LocalDateTime grantedAt;
    private String grantedByName;
    private LocalDateTime expiresAt;
    private boolean isExpired;
}

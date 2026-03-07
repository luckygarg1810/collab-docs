package com.project.collab_docs.dto.response;

import com.project.collab_docs.entities.ShareInvitation;
import com.project.collab_docs.enums.InvitationStatus;
import com.project.collab_docs.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for share invitation information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareInvitationResponse {

    private Long id;
    private String token;
    private String invitedEmail;
    private String invitedUserName;
    private Long invitedUserId;
    private Role role;
    private InvitationStatus status;
    private LocalDateTime invitedAt;
    private LocalDateTime respondedAt;
    private LocalDateTime expiresAt;
    private String invitedByName;
    private Long invitedById;
    private String message;
    private String documentTitle;
    private Long documentId;
    private Boolean isExpired;
    private Boolean isPending;

    /**
     * Convert ShareInvitation entity to response DTO
     */
    public static ShareInvitationResponse from(ShareInvitation invitation) {
        ShareInvitationResponseBuilder builder = ShareInvitationResponse.builder()
                .id(invitation.getId())
                .token(invitation.getToken())
                .invitedEmail(invitation.getInvitedEmail())
                .role(invitation.getRole())
                .status(invitation.getStatus())
                .invitedAt(invitation.getInvitedAt())
                .respondedAt(invitation.getRespondedAt())
                .expiresAt(invitation.getExpiresAt())
                .invitedByName(invitation.getInvitedBy().getFirstName() + " " +
                        invitation.getInvitedBy().getLastName())
                .invitedById(invitation.getInvitedBy().getId())
                .message(invitation.getMessage())
                .documentTitle(invitation.getDocument().getTitle())
                .documentId(invitation.getDocument().getId())
                .isExpired(invitation.isExpired())
                .isPending(invitation.isPending());

        // Add invited user info if available
        if (invitation.getInvitedUser() != null) {
            builder.invitedUserName(invitation.getInvitedUser().getFirstName() + " " +
                    invitation.getInvitedUser().getLastName())
                    .invitedUserId(invitation.getInvitedUser().getId());
        }

        return builder.build();
    }

    /**
     * Minimal version for email notifications
     */
    public static ShareInvitationResponse forEmail(ShareInvitation invitation) {
        return ShareInvitationResponse.builder()
                .invitedEmail(invitation.getInvitedEmail())
                .role(invitation.getRole())
                .invitedByName(invitation.getInvitedBy().getFirstName() + " " +
                        invitation.getInvitedBy().getLastName())
                .documentTitle(invitation.getDocument().getTitle())
                .message(invitation.getMessage())
                .expiresAt(invitation.getExpiresAt())
                .build();
    }
}

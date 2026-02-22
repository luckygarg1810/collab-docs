package com.project.collab_docs.dto.response;

import com.project.collab_docs.entities.ShareLink;
import com.project.collab_docs.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for share link information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLinkResponse {

    private Long id;
    private String token;
    private String shareUrl;
    private String documentTitle;
    private Role role;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Integer currentUses;
    private Integer remainingUses;
    private Boolean isActive;
    private Boolean requiresAuth;
    private String description;
    private LocalDateTime lastUsedAt;
    private String createdByName;
    private Long createdById;
    private Boolean isExpired;
    private Boolean isUsageLimitReached;
    private Boolean isValid;

    /**
     * Convert ShareLink entity to response DTO
     */
    public static ShareLinkResponse from(ShareLink link) {
        return ShareLinkResponse.builder()
                .id(link.getId())
                .token(link.getToken())
                .shareUrl(link.getShareUrl())
                .documentTitle(link.getDocument().getTitle())
                .role(link.getRole())
                .createdAt(link.getCreatedAt())
                .expiresAt(link.getExpiresAt())
                .maxUses(link.getMaxUses())
                .currentUses(link.getCurrentUses())
                .remainingUses(link.getRemainingUses())
                .isActive(link.getIsActive())
                .requiresAuth(link.getRequiresAuth())
                .description(link.getDescription())
                .lastUsedAt(link.getLastUsedAt())
                .createdByName(link.getCreatedBy().getFirstName() + " " + link.getCreatedBy().getLastName())
                .createdById(link.getCreatedBy().getId())
                .isExpired(link.isExpired())
                .isUsageLimitReached(link.isUsageLimitReached())
                .isValid(link.isValid())
                .build();
    }

    /**
     * Public version without sensitive details (for unauthenticated access)
     */
    public static ShareLinkResponse publicFrom(ShareLink link) {
        return ShareLinkResponse.builder()
                .role(link.getRole())
                .requiresAuth(link.getRequiresAuth())
                .isValid(link.isValid())
                .isExpired(link.isExpired())
                .build();
    }
}

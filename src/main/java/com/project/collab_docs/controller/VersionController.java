package com.project.collab_docs.controller;

import com.project.collab_docs.dto.request.CreateVersionRequest;
import com.project.collab_docs.dto.response.MessageResponse;
import com.project.collab_docs.dto.response.VersionContentResponse;
import com.project.collab_docs.dto.response.VersionResponse;
import com.project.collab_docs.security.CustomUserDetails;
import com.project.collab_docs.service.VersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for document versioning operations.
 *
 * Provides endpoints for:
 * - Creating version snapshots
 * - Listing version history
 * - Viewing specific versions
 * - Restoring previous versions
 * - Deleting old versions
 * - Version statistics
 *
 * All endpoints require authentication and appropriate permissions.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Document Versions", description = "Version control and history management")
@Slf4j
public class VersionController {

    @Autowired
    private VersionService versionService;

    /**
     * Create a new version snapshot of a document
     *
     * POST /api/documents/{documentId}/versions
     *
     * Requires: EDITOR permission or higher
     */
    @PostMapping("/documents/{documentId}/versions")
    @Operation(summary = "Create a new version", description = "Creates a snapshot of the current document state")
    public ResponseEntity<?> createVersion(
            @PathVariable Long documentId,
            @Valid @RequestBody CreateVersionRequest request,
            Authentication authentication) {
        try {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Long userId = userDetails.getUser().getId();

            VersionResponse version = versionService.createVersion(
                    documentId,
                    request.getVersionName(),
                    request.getChangeNotes(),
                    userId
            );

            log.info("Created version {} for document {} by user {}",
                    version.getVersionNumber(), documentId, userId);

            return ResponseEntity.status(HttpStatus.CREATED).body(version);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Version creation failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    /**
     * List all versions for a document
     *
     * GET /api/documents/{documentId}/versions
     *
     * Requires: VIEWER permission or higher
     */
    @GetMapping("/documents/{documentId}/versions")
    @Operation(summary = "List document versions", description = "Get all versions for a document with metadata")
    public ResponseEntity<?> listVersions(
            @PathVariable Long documentId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();

        if (page < 0 || size <= 0) {
            // Return all versions without pagination
            List<VersionResponse> versions = versionService.listVersions(documentId, userId);
            return ResponseEntity.ok(versions);
        } else {
            // Return paginated versions
            Pageable pageable = PageRequest.of(page, size);
            Page<VersionResponse> versions = versionService.listVersionsPaginated(documentId, userId, pageable);
            return ResponseEntity.ok(versions);
        }
    }

    /**
     * Get a specific version with its content
     *
     * GET /api/versions/{versionId}
     *
     * Requires: VIEWER permission on the document
     */
    @GetMapping("/versions/{versionId}")
    @Operation(summary = "Get version details", description = "Get a specific version with its content snapshot")
    public ResponseEntity<?> getVersion(
            @PathVariable Long versionId,
            Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();

        VersionContentResponse version = versionService.getVersionContent(versionId, userId);
        return ResponseEntity.ok(version);
    }

    /**
     * Restore a document to a previous version
     *
     * POST /api/versions/{versionId}/restore
     *
     * This creates a NEW version with the old content rather than overwriting history.
     * Requires: EDITOR permission or higher
     */
    @PostMapping("/versions/{versionId}/restore")
    @Operation(summary = "Restore a version", description = "Restore document to a previous version (creates a new version)")
    public ResponseEntity<?> restoreVersion(
            @PathVariable Long versionId,
            Authentication authentication) {
        try {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Long userId = userDetails.getUser().getId();

            VersionResponse newVersion = versionService.restoreVersion(versionId, userId);

            log.info("Restored version {} by user {}, created new version {}",
                    versionId, userId, newVersion.getVersionNumber());

            return ResponseEntity.ok(Map.of(
                    "message", "Version restored successfully",
                    "newVersion", newVersion
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Version restoration failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    /**
     * Delete a specific version
     *
     * DELETE /api/versions/{versionId}
     *
     * Requires: OWNER permission
     * Cannot delete if it's the only version
     */
    @DeleteMapping("/versions/{versionId}")
    @Operation(summary = "Delete a version", description = "Delete a specific version (Owner only)")
    public ResponseEntity<?> deleteVersion(
            @PathVariable Long versionId,
            Authentication authentication) {
        try {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Long userId = userDetails.getUser().getId();

            versionService.deleteVersion(versionId, userId);

            log.info("Deleted version {} by user {}", versionId, userId);

            return ResponseEntity.ok(new MessageResponse("Version deleted successfully"));
        } catch (IllegalStateException e) {
            log.warn("Version deletion failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    /**
     * Delete old versions keeping only N most recent
     *
     * POST /api/documents/{documentId}/versions/cleanup
     *
     * Requires: OWNER permission
     */
    @PostMapping("/documents/{documentId}/versions/cleanup")
    @Operation(summary = "Cleanup old versions", description = "Delete old versions keeping only N most recent")
    public ResponseEntity<?> cleanupVersions(
            @PathVariable Long documentId,
            @Parameter(description = "Number of recent versions to keep") @RequestParam(defaultValue = "10") int keepCount,
            Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();

        int deletedCount = versionService.deleteOldVersions(documentId, keepCount, userId);

        log.info("Cleaned up {} versions for document {} by user {}", deletedCount, documentId, userId);

        return ResponseEntity.ok(Map.of(
                "message", "Cleanup completed successfully",
                "deletedCount", deletedCount,
                "keptCount", keepCount
        ));
    }

    /**
     * Get version statistics for a document
     *
     * GET /api/documents/{documentId}/versions/stats
     *
     * Requires: VIEWER permission
     */
    @GetMapping("/documents/{documentId}/versions/stats")
    @Operation(summary = "Get version statistics", description = "Get version count and storage usage")
    public ResponseEntity<?> getVersionStatistics(
            @PathVariable Long documentId,
            Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();

        VersionService.VersionStatistics stats = versionService.getVersionStatistics(documentId, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("versionCount", stats.count);
        response.put("totalStorageBytes", stats.totalStorageBytes);
        response.put("formattedStorage", stats.getFormattedStorage());

        return ResponseEntity.ok(response);
    }
}

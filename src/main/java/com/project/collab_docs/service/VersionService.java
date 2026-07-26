package com.project.collab_docs.service;

import com.project.collab_docs.entities.Document;
import com.project.collab_docs.entities.DocumentVersion;
import com.project.collab_docs.entities.User;
import com.project.collab_docs.enums.Role;
import com.project.collab_docs.exception.PermissionDeniedException;
import com.project.collab_docs.exception.ResourceNotFoundException;
import com.project.collab_docs.exception.VersionLimitExceededException;
import com.project.collab_docs.repository.DocumentRepository;
import com.project.collab_docs.repository.DocumentVersionRepository;
import com.project.collab_docs.repository.UserRepository;
import com.project.collab_docs.dto.response.VersionContentResponse;
import com.project.collab_docs.dto.response.VersionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing document versions.
 *
 * Key features:
 * - Create snapshots of document state at any point in time
 * - List version history with metadata
 * - Restore previous versions
 * - Delete old versions for storage management
 * - Enforce version limits per document
 * - Calculate SHA-256 hashes for integrity verification
 *
 * Security:
 * - All operations require appropriate permissions (EDITOR or higher to create, OWNER to delete)
 * - Version restoration creates a new version rather than overwriting
 */
@Service
@Slf4j
public class VersionService {

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private YjsCollaborationService yjsCollaborationService;

    /**
     * Maximum number of versions per document (0 = unlimited)
     * Can be configured via application.properties
     */
    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.version.max-versions-per-document:100}")
    private int maxVersionsPerDocument;

    /**
     * Base URL of the Yjs Node.js collaboration service.
     * Used to trigger an immediate snapshot flush before versioning.
     */
    @Value("${app.yjs.service-url:http://localhost:3000}")
    private String yjsServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // The backend container's clock is pinned to IST (see docker-compose.yml's
    // TZ: Asia/Kolkata under the backend service), so LocalDateTime.now() —
    // and everything derived from it, like DocumentVersion.createdAt — is
    // already IST wall-clock time despite carrying no timezone marker.
    private static final DateTimeFormatter RESTORATION_NOTE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy, h:mm a 'IST'");

    /**
     * Ask the Yjs service to immediately flush the in-memory Y.Doc to PostgreSQL.
     * This is called before creating a version when the document has no saved snapshot yet
     * (e.g., within the first 30 seconds of editing).
     *
     * @param yjsRoomId The Yjs room ID (= document.yjsRoomId)
     */
    private void flushYjsSnapshot(String yjsRoomId) {
        try {
            String url = yjsServiceUrl + "/api/documents/" + yjsRoomId + "/save";
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            log.info("Yjs force-save triggered for room {}: HTTP {}", yjsRoomId, response.getStatusCode());
        } catch (Exception e) {
            // Non-fatal: the Yjs service may not be running locally or the document
            // may not be in its memory. Log a warning and continue — the caller will
            // handle the missing snapshot.
            log.warn("Could not trigger Yjs flush for room {}: {}", yjsRoomId, e.getMessage());
        }
    }

    /**
     * Ask the Yjs service to replace a room's live document content with an
     * older version's snapshot, and persist the merged result.
     *
     * Writing oldVersion.getYjsSnapshot() straight into Document.yjsSnapshot
     * here would look like it worked (200 OK, row updated) but be invisible:
     * yjs-service's getDocument() checks its in-memory doc cache and then
     * Redis before ever looking at Postgres, so a live/cached room would
     * just keep serving its old content and overwrite this write on its next
     * autosave. And even if we did push the raw bytes into yjs-service, a
     * plain Y.applyUpdate() merge can't roll back a doc that has already
     * moved past that state — CRDT merges only add operations, they can't
     * undo ones a peer already has. yjs-service's /restore endpoint handles
     * both problems: it replaces the live doc's content with new operations
     * (so the merge actually takes effect) and broadcasts the change to any
     * connected clients.
     *
     * @throws IllegalStateException if the Yjs service call fails — restore
     *         must not be reported as successful (and no restoration version
     *         should be recorded) if the document wasn't actually restored.
     */
    private void restoreYjsDocument(String yjsRoomId, byte[] snapshot) {
        try {
            String url = yjsServiceUrl + "/api/documents/" + yjsRoomId + "/restore";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            HttpEntity<byte[]> entity = new HttpEntity<>(snapshot, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("Yjs restore triggered for room {}: HTTP {}", yjsRoomId, response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to restore Yjs document for room {}: {}", yjsRoomId, e.getMessage());
            throw new IllegalStateException("Failed to restore document content. Please try again.", e);
        }
    }

    /**
     * Create a new version snapshot of a document.
     * Captures current Yjs state and content.
     *
     * @param documentId Document to version
     * @param versionName Optional user-provided name
     * @param changeNotes Optional notes about changes
     * @param userId User creating the version
     * @return Created version response
     * @throws ResourceNotFoundException if document or user not found
     * @throws PermissionDeniedException if user lacks EDITOR permission
     * @throws VersionLimitExceededException if version limit exceeded
     */
    @Transactional
    public VersionResponse createVersion(Long documentId, String versionName, String changeNotes, Long userId) {
        return createVersion(documentId, versionName, changeNotes, userId, false);
    }

    /**
     * @param isRestoration true when this version is the auto-created audit
     *        record of a restore (see restoreVersion()), false for a
     *        user-initiated save — lets the UI tell the two apart.
     */
    private VersionResponse createVersion(Long documentId, String versionName, String changeNotes, Long userId, boolean isRestoration) {
        log.info("Creating version for document {} by user {}", documentId, userId);

        // Validate user has EDITOR permission (can create versions)
        if (!permissionService.hasPermission(documentId, userId, Role.EDITOR)) {
            throw new PermissionDeniedException("You must have EDITOR permission to create versions");
        }

        // Get document
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check version limit
        long currentVersionCount = versionRepository.countByDocumentId(documentId);
        if (maxVersionsPerDocument > 0 && currentVersionCount >= maxVersionsPerDocument) {
            throw new VersionLimitExceededException(
                    String.format("Document has reached maximum version limit of %d. Please delete old versions first.",
                    maxVersionsPerDocument));
        }

        // Always force-flush the live Yjs doc to PostgreSQL before snapshotting.
        // yjs-service only persists on a 30s-after-last-edit debounce (or a 5min
        // interval fallback) — without an unconditional flush here, "Save Version"
        // would silently capture whatever was last autosaved rather than what's
        // actually on screen when the user clicks Save.
        flushYjsSnapshot(document.getYjsRoomId());

        // IMPORTANT: Evict the stale L1-cached entity so the next findById
        // issues a real SELECT and picks up the freshly written yjsSnapshot.
        entityManager.refresh(document);
        byte[] yjsSnapshot = document.getYjsSnapshot();

        if (yjsSnapshot == null || yjsSnapshot.length == 0) {
            // Also try asking the YjsCollaborationService (reads from DB/Redis directly)
            yjsSnapshot = yjsCollaborationService.getYjsSnapshot(document.getYjsRoomId());
        }

        if (yjsSnapshot == null || yjsSnapshot.length == 0) {
            throw new IllegalStateException(
                    "Document has no content to snapshot yet. Please make some edits, wait a moment, and try again.");
        }

        // Get next version number
        Integer nextVersionNumber = versionRepository.getNextVersionNumber(documentId);

        // Calculate SHA-256 hash for integrity
        String snapshotHash = calculateSHA256(yjsSnapshot);

        // Create version entity
        DocumentVersion version = DocumentVersion.builder()
                .document(document)
                .versionNumber(nextVersionNumber)
                .versionName(versionName)
                .changeNotes(changeNotes)
                .createdBy(user)
                .createdAt(LocalDateTime.now())
                .yjsSnapshot(yjsSnapshot)
                .sizeBytes((long) yjsSnapshot.length)
                .snapshotHash(snapshotHash)
                .restoration(isRestoration)
                .build();

        version = versionRepository.save(version);
        log.info("Created version {} for document {} (size: {} bytes)",
                nextVersionNumber, documentId, yjsSnapshot.length);

        return VersionResponse.from(version);
    }

    /**
     * List all versions for a document, ordered by version number descending
     *
     * @param documentId Document to get versions for
     * @param userId User requesting versions
     * @return List of version metadata
     * @throws PermissionDeniedException if user lacks VIEWER permission
     */
    @Transactional(readOnly = true)
    public List<VersionResponse> listVersions(Long documentId, Long userId) {
        log.debug("Listing versions for document {} by user {}", documentId, userId);

        // Validate user has at least VIEWER permission
        if (!permissionService.hasPermission(documentId, userId, Role.VIEWER)) {
            throw new PermissionDeniedException("You must have access to this document to view versions");
        }

        List<DocumentVersion> versions = versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
        return versions.stream()
                .map(VersionResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * List versions with pagination
     */
    @Transactional(readOnly = true)
    public Page<VersionResponse> listVersionsPaginated(Long documentId, Long userId, Pageable pageable) {
        log.debug("Listing versions (paginated) for document {} by user {}", documentId, userId);

        if (!permissionService.hasPermission(documentId, userId, Role.VIEWER)) {
            throw new PermissionDeniedException("You must have access to this document to view versions");
        }

        Page<DocumentVersion> versions = versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId, pageable);
        return versions.map(VersionResponse::from);
    }

    /**
     * Get a specific version with its content
     *
     * @param versionId Version to retrieve
     * @param userId User requesting the version
     * @return Version content including binary snapshot
     * @throws ResourceNotFoundException if version not found
     * @throws PermissionDeniedException if user lacks VIEWER permission
     */
    @Transactional(readOnly = true)
    public VersionContentResponse getVersionContent(Long versionId, Long userId) {
        log.debug("Getting version content for version {} by user {}", versionId, userId);

        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found"));

        // Check permission on the document
        if (!permissionService.hasPermission(version.getDocument().getId(), userId, Role.VIEWER)) {
            throw new PermissionDeniedException("You must have access to this document to view its versions");
        }

        return VersionContentResponse.builder()
                .versionId(version.getId())
                .versionNumber(version.getVersionNumber())
                .versionName(version.getVersionName())
                .contentSnapshot(version.getContentSnapshot())
                .yjsSnapshot(version.getYjsSnapshot())
                .sizeBytes(version.getSizeBytes())
                .snapshotHash(version.getSnapshotHash())
                .build();
    }

    /**
     * Restore a document to a previous version.
     * This creates a NEW version with the old content rather than overwriting history.
     *
     * @param versionId Version to restore
     * @param userId User performing the restoration
     * @return New version created from restoration
     * @throws ResourceNotFoundException if version not found
     * @throws PermissionDeniedException if user lacks EDITOR permission
     */
    @Transactional
    public VersionResponse restoreVersion(Long versionId, Long userId) {
        log.info("Restoring version {} by user {}", versionId, userId);

        // Get the version to restore
        DocumentVersion oldVersion = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found"));

        Document document = oldVersion.getDocument();

        // Check permission (EDITOR required)
        if (!permissionService.hasPermission(document.getId(), userId, Role.EDITOR)) {
            throw new PermissionDeniedException("You must have EDITOR permission to restore versions");
        }

        // Replace the room's live content via the Yjs service (see
        // restoreYjsDocument() javadoc for why a direct Postgres write can't
        // actually restore a CRDT document).
        restoreYjsDocument(document.getYjsRoomId(), oldVersion.getYjsSnapshot());

        // The Yjs service just updated documents.yjs_snapshot out-of-band (its
        // own HTTP call into Spring, outside this persistence context).
        // Evict the stale L1-cached copy so the createVersion() call below
        // snapshots the actual restored content, not what was cached here
        // before the restore ran.
        entityManager.refresh(document);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);

        // Create a new version marking this restoration
        String creatorName = (oldVersion.getCreatedBy().getFirstName() + " " + oldVersion.getCreatedBy().getLastName()).trim();
        String restorationName = "Restored from " + oldVersion.getDisplayName();
        String restorationNotes = String.format("Restored from version %d created by %s on %s",
                oldVersion.getVersionNumber(),
                creatorName.isEmpty() ? oldVersion.getCreatedBy().getEmail() : creatorName,
                oldVersion.getCreatedAt().format(RESTORATION_NOTE_DATE_FORMAT));

        log.info("Document {} restored to version {} by user {}",
                document.getId(), oldVersion.getVersionNumber(), userId);

        // Create new version to record this restoration
        return createVersion(document.getId(), restorationName, restorationNotes, userId, true);
    }

    /**
     * Delete a specific version.
     * Only OWNERs can delete versions to prevent tampering with history.
     * Cannot delete if it's the only version (keep at least one for audit trail).
     *
     * @param versionId Version to delete
     * @param userId User performing deletion
     * @throws ResourceNotFoundException if version not found
     * @throws PermissionDeniedException if user is not OWNER
     * @throws IllegalStateException if trying to delete the last version
     */
    @Transactional
    public void deleteVersion(Long versionId, Long userId) {
        log.info("Deleting version {} by user {}", versionId, userId);

        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found"));

        Document document = version.getDocument();

        // Check permission (OWNER required for deletion)
        if (!permissionService.isOwner(document.getId(), userId)) {
            throw new PermissionDeniedException("Only document owners can delete versions");
        }

        // Prevent deleting the only version
        long versionCount = versionRepository.countByDocumentId(document.getId());
        if (versionCount <= 1) {
            throw new IllegalStateException("Cannot delete the only version. At least one version must remain for audit purposes.");
        }

        versionRepository.delete(version);
        log.info("Deleted version {} from document {}", version.getVersionNumber(), document.getId());
    }

    /**
     * Delete old versions keeping only the N most recent ones.
     * Useful for storage management and enforcing quotas.
     *
     * @param documentId Document to clean up
     * @param keepCount Number of recent versions to keep
     * @param userId User performing cleanup
     * @return Number of versions deleted
     */
    @Transactional
    public int deleteOldVersions(Long documentId, int keepCount, Long userId) {
        log.info("Cleaning up old versions for document {}, keeping {} most recent", documentId, keepCount);

        // Check permission (OWNER required)
        if (!permissionService.isOwner(documentId, userId)) {
            throw new PermissionDeniedException("Only document owners can delete versions");
        }

        if (keepCount < 1) {
            throw new IllegalArgumentException("Must keep at least 1 version");
        }

        // Get all versions sorted by version number descending
        List<DocumentVersion> allVersions = versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);

        if (allVersions.size() <= keepCount) {
            log.info("Document has {} versions, no cleanup needed", allVersions.size());
            return 0;
        }

        // Delete versions beyond keepCount
        List<DocumentVersion> versionsToDelete = allVersions.subList(keepCount, allVersions.size());
        versionRepository.deleteAll(versionsToDelete);

        log.info("Deleted {} old versions from document {}", versionsToDelete.size(), documentId);
        return versionsToDelete.size();
    }

    /**
     * Get version statistics for a document
     */
    @Transactional(readOnly = true)
    public VersionStatistics getVersionStatistics(Long documentId, Long userId) {
        if (!permissionService.hasPermission(documentId, userId, Role.VIEWER)) {
            throw new PermissionDeniedException("You must have access to this document");
        }

        long count = versionRepository.countByDocumentId(documentId);
        long totalStorage = versionRepository.getTotalStorageByDocumentId(documentId);

        return new VersionStatistics(count, totalStorage);
    }

    /**
     * Calculate SHA-256 hash of binary data for integrity verification
     */
    private String calculateSHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            return null;
        }
    }

    /**
     * Inner class for version statistics
     */
    public static class VersionStatistics {
        public final long count;
        public final long totalStorageBytes;

        public VersionStatistics(long count, long totalStorageBytes) {
            this.count = count;
            this.totalStorageBytes = totalStorageBytes;
        }

        public String getFormattedStorage() {
            if (totalStorageBytes < 1024) {
                return totalStorageBytes + " B";
            } else if (totalStorageBytes < 1024 * 1024) {
                return String.format("%.2f KB", totalStorageBytes / 1024.0);
            } else {
                return String.format("%.2f MB", totalStorageBytes / (1024.0 * 1024.0));
            }
        }
    }
}


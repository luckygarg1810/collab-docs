package com.project.collab_docs.service;

import com.project.collab_docs.dto.request.UpdateTitleRequest;
import com.project.collab_docs.entities.Document;
import com.project.collab_docs.entities.DocumentPermission;
import com.project.collab_docs.entities.User;
import com.project.collab_docs.enums.DocumentFilter;
import com.project.collab_docs.enums.Role;
import com.project.collab_docs.enums.Visibility;
import com.project.collab_docs.exception.PermissionDeniedException;
import com.project.collab_docs.exception.ResourceNotFoundException;
import com.project.collab_docs.repository.DocumentRepository;
import com.project.collab_docs.repository.DocumentPermissionRepository;
import com.project.collab_docs.repository.StarredDocumentRepository;
import com.project.collab_docs.repository.RecentDocumentViewRepository;
import com.project.collab_docs.repository.UserRepository;
import com.project.collab_docs.entities.StarredDocument;
import com.project.collab_docs.entities.RecentDocumentView;
import com.project.collab_docs.dto.response.DocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final DocumentPermissionRepository permissionRepository;
    private final StarredDocumentRepository starredDocumentRepository;
    private final RecentDocumentViewRepository recentDocumentViewRepository;
    private final DocumentEventPublisher documentEventPublisher;

    @Transactional
    public Document createBlankDocument(String title, Long userId) {
        // Fetch user from database to get managed entity
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Generate unique Yjs room ID
        String yjsRoomId = generateUniqueYjsRoomId();

        // Note: Blank documents start with no content
        // TipTap editor with Yjs will initialize the collaborative document
        // Yjs snapshots are saved automatically by the Yjs service
        Document document = Document.builder()
                .title(title)
                .fileName(title + ".docx")
                .yjsRoomId(yjsRoomId)
                .owner(owner)
                .contentType("application/octet-stream") // Yjs binary format
                .fileSize(0L)
                .visibility(Visibility.PRIVATE) // Default to private
                .isDeleted(false)
                .build();

        Document savedDocument = documentRepository.save(document);

        // AUTO-GRANT OWNER PERMISSION
        permissionService.grantPermission(
                savedDocument.getId(),
                owner.getId(),
                Role.OWNER,
                owner.getId());

        log.info("Created blank document with ID: {} for user: {} with OWNER permission",
                savedDocument.getId(), owner.getEmail());

        return savedDocument;
    }

    @Transactional(readOnly = true)
    public Document getDocumentById(Long documentId, User requestingUser) {
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Check VIEWER permission using RBAC
        if (!permissionService.hasPermission(documentId, requestingUser.getId(), Role.VIEWER)) {
            throw new PermissionDeniedException("Access denied. You don't have permission to view this document");
        }

        log.info("Document {} accessed by user: {} with role: {}",
                documentId, requestingUser.getEmail(),
                permissionService.getEffectiveRole(documentId, requestingUser.getId()));
        return document;
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> getUserDocuments(User user, DocumentFilter filter, int page, int size) {
        try {
            log.info("Get Document API called");
            // Validate pagination parameters
            if (page < 0) {
                throw new IllegalArgumentException("Page number cannot be negative");
            }
            if (size <= 0 || size > 100) {
                throw new IllegalArgumentException("Page size must be between 1 and 100");
            }

            // Get the requested subset of documents the user can access (RBAC-checked)
            List<Document> accessibleDocuments = switch (filter) {
                case OWNED -> permissionService.getUserDocumentsByRole(user.getId(), Role.OWNER);
                case SHARED -> permissionService.getSharedDocumentsForUser(user.getId());
                case ALL -> permissionService.getUserAccessibleDocuments(user.getId());
                case STARRED -> {
                    // Intersect with current access so a star left behind after
                    // access was revoked doesn't resurface a document the user
                    // can no longer open.
                    var accessibleIds = permissionService.getUserAccessibleDocuments(user.getId()).stream()
                            .map(Document::getId)
                            .collect(Collectors.toSet());
                    yield starredDocumentRepository.findStarredDocumentsByUserId(user.getId()).stream()
                            .filter(doc -> accessibleIds.contains(doc.getId()))
                            .toList();
                }
                case RECENT -> recentDocumentViewRepository
                        .findTop10ByUserIdOrderByLastOpenedAtDesc(user.getId()).stream()
                        .map(RecentDocumentView::getDocument)
                        .filter(doc -> !doc.getIsDeleted()
                                && permissionService.hasPermission(doc.getId(), user.getId(), Role.VIEWER))
                        .toList();
            };

            // Manual pagination since we're working with a List
            int start = page * size;
            int end = Math.min(start + size, accessibleDocuments.size());

            var paginatedDocs = accessibleDocuments.subList(
                    Math.min(start, accessibleDocuments.size()),
                    end);

            // Convert to DocumentResponse, resolving the caller's role on each
            // (owner vs. shared-with) so the frontend can rely on the backend
            // for ownership instead of comparing emails client-side.
            var documentResponses = paginatedDocs.stream()
                    .map(doc -> mapToDocumentResponse(doc, user.getId()))
                    .toList();

            // Create Page object
            Pageable pageable = PageRequest.of(page, size);
            long total = accessibleDocuments.size();

            Page<DocumentResponse> documentResponsePage = new org.springframework.data.domain.PageImpl<>(
                    documentResponses, pageable, total);

            log.info("Retrieved {} accessible documents for user: {} (page: {}, size: {})",
                    total, user.getEmail(), page, size);

            return documentResponsePage;

        } catch (IllegalArgumentException e) {
            log.error("Invalid parameters for getUserDocuments: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error retrieving documents for user {}: {}", user.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve user documents", e);
        }
    }



    @Transactional
    public Document uploadDocument(MultipartFile file, String title, User owner) throws IOException {
        // Validate file
        validateUploadedFile(file);

        // Generate unique Yjs room ID
        String yjsRoomId = generateUniqueYjsRoomId();

        // Safely handle potential null filename
        String originalFilename = file.getOriginalFilename();
        String fileName = (originalFilename != null && !originalFilename.isEmpty())
                ? originalFilename
                : "Untitled File";

        // Store original file MIME type for tracking purposes
        String originalContentType = file.getContentType();

        // Note: We DO NOT extract/convert content here
        // The frontend (TipTap) will handle file conversion and create Yjs document
        // This allows for better client-side control and real-time collaboration setup
        Document document = Document.builder()
                .title(title != null && !title.trim().isEmpty() ? title : getFileNameWithoutExtension(fileName))
                .fileName(fileName)
                .contentType(originalContentType) // Store original file type for reference
                .fileSize(file.getSize())
                .yjsRoomId(yjsRoomId)
                .owner(owner)
                .visibility(Visibility.PRIVATE) // Default to private
                .isDeleted(false)
                .build();

        Document savedDocument = documentRepository.save(document);

        // AUTO-GRANT OWNER PERMISSION
        permissionService.grantPermission(
                savedDocument.getId(),
                owner.getId(),
                Role.OWNER,
                owner.getId());

        log.info("Uploaded document metadata with ID: {} for user: {} with OWNER permission. " +
                "Frontend will handle content conversion.",
                savedDocument.getId(), owner.getEmail());

        return savedDocument;
    }

    private void validateUploadedFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null
                || !contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        && !contentType.equals("application/pdf")) {
            throw new IllegalArgumentException("Only DOCX and PDF files are supported");
        }

        // 20MB limit
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new IllegalArgumentException("File size cannot exceed 20MB");
        }
    }

    public DocumentResponse mapToDocumentResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .fileName(document.getFileName())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .yjsRoomId(document.getYjsRoomId())
                .ownerEmail(document.getOwner().getEmail())
                .ownerName(document.getOwner().getFirstName() + " " + document.getOwner().getLastName())
                .isTemplate(document.getIsTemplate())
                .visibility(document.getVisibility())
                .isDeleted(document.getIsDeleted())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .isStarred(false) // freshly created document, can't be starred yet
                .build();
    }

    /**
     * Map to DocumentResponse with the requesting user's effective role resolved.
     */
    public DocumentResponse mapToDocumentResponse(Document document, Long userId) {
        Role effectiveRole;
        if (document.getOwner().getId().equals(userId)) {
            // Owners have no DocumentPermission row — they are always OWNER.
            effectiveRole = Role.OWNER;
        } else {
            effectiveRole = permissionRepository
                    .findByDocumentIdAndUserId(document.getId(), userId)
                    .map(DocumentPermission::getRole)
                    .orElse(Role.VIEWER);
        }
        boolean isStarred = starredDocumentRepository.existsByDocumentIdAndUserId(document.getId(), userId);
        LocalDateTime lastOpenedAt = recentDocumentViewRepository
                .findByUserIdAndDocumentId(userId, document.getId())
                .map(RecentDocumentView::getLastOpenedAt)
                .orElse(null);
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .fileName(document.getFileName())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .yjsRoomId(document.getYjsRoomId())
                .ownerEmail(document.getOwner().getEmail())
                .ownerName(document.getOwner().getFirstName() + " " + document.getOwner().getLastName())
                .isTemplate(document.getIsTemplate())
                .visibility(document.getVisibility())
                .isDeleted(document.getIsDeleted())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .userRole(effectiveRole)
                .isStarred(isStarred)
                .lastOpenedAt(lastOpenedAt)
                .build();
    }

    @Transactional
    public void recordDocumentOpen(Long documentId, User user) {
        if (!permissionService.hasPermission(documentId, user.getId(), Role.VIEWER)) {
            throw new PermissionDeniedException("You don't have access to this document");
        }
        recentDocumentViewRepository.recordOpen(user.getId(), documentId);
    }

    @Transactional
    public void starDocument(Long documentId, User user) {
        if (!permissionService.hasPermission(documentId, user.getId(), Role.VIEWER)) {
            throw new PermissionDeniedException("You don't have access to this document");
        }
        if (starredDocumentRepository.existsByDocumentIdAndUserId(documentId, user.getId())) {
            return; // already starred — idempotent
        }
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        starredDocumentRepository.save(StarredDocument.builder()
                .document(document)
                .user(user)
                .build());
    }

    @Transactional
    public void unstarDocument(Long documentId, User user) {
        starredDocumentRepository.deleteByDocumentIdAndUserId(documentId, user.getId());
    }

    // Note: HTML content extraction methods removed
    // The frontend (TipTap editor) handles file conversion directly
    // This provides better control over the document format and allows
    // immediate Yjs collaboration setup without backend processing

    private String getFileNameWithoutExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "Untitled Document";
        }

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }

    @Transactional
    public void saveYjsSnapshot(String yjsRoomId, byte[] snapshot) {
        documentRepository.findByYjsRoomIdAndIsDeletedFalse(yjsRoomId)
                .ifPresent(document -> {
                    document.setYjsSnapshot(snapshot);
                    documentRepository.save(document);
                    log.info("Saved Yjs snapshot for document: {}", document.getId());
                });
    }

    @Transactional
    public void updateDocumentVisibility(Long documentId, Visibility visibility, User owner) {
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Check OWNER permission using RBAC
        if (!permissionService.hasPermission(documentId, owner.getId(), Role.OWNER)) {
            throw new PermissionDeniedException("Only the document owner can change visibility");
        }

        if (!isValidVisibility(visibility)) {
            throw new IllegalArgumentException("Invalid visibility value. Must be 'private', 'shared', or 'public'");
        }

        document.setVisibility(visibility);
        documentRepository.save(document);
        log.info("Updated document {} visibility to: {}", documentId, visibility);
    }

    private boolean isValidVisibility(Visibility visibility) {
        return Visibility.PRIVATE.equals(visibility) || Visibility.PUBLIC.equals(visibility)
                || Visibility.SHARED.equals(visibility);
    }

    @Transactional
    public void softDeleteDocument(Long documentId, User user) {
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Check OWNER permission using RBAC
        if (!permissionService.hasPermission(documentId, user.getId(), Role.OWNER)) {
            throw new PermissionDeniedException("Only the document owner can delete this document");
        }

        // Perform soft delete
        document.setIsDeleted(true);
        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);

        documentEventPublisher.publishDocumentDeleted(documentId, document.getYjsRoomId());

        log.info("Soft deleted document with ID: {} by user: {}", documentId, user.getEmail());
    }

    private String generateUniqueYjsRoomId() {
        String roomId;
        do {
            roomId = "doc_" + UUID.randomUUID().toString().replace("-", "");
        } while (documentRepository.existsByYjsRoomId(roomId));
        return roomId;
    }

    @Transactional(readOnly = true)
    public byte[] getYjsSnapshot(String yjsRoomId) {
        try {
            // Validate input
            if (yjsRoomId == null || yjsRoomId.trim().isEmpty()) {
                throw new IllegalArgumentException("YJS room ID cannot be null or empty");
            }

            String cleanRoomId = yjsRoomId.trim();
            // Find document by YJS room ID
            Document document = documentRepository.findByYjsRoomIdAndIsDeletedFalse(cleanRoomId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Document not found or has been deleted for room ID: " + cleanRoomId));

            // Get the YJS snapshot
            byte[] snapshot = document.getYjsSnapshot();
            if (snapshot == null || snapshot.length == 0) {
                log.info("No YJS snapshot found for room ID: {}, returning empty snapshot", cleanRoomId);
                // Return empty byte array if no snapshot exists
                return new byte[0];
            }

            log.info("Retrieved YJS snapshot for room ID: {}, size: {} bytes",
                    cleanRoomId, snapshot.length);

            return snapshot;
        } catch (IllegalArgumentException e) {
            log.error("Invalid request for YJS snapshot: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error retrieving YJS snapshot for room ID {}: {}", yjsRoomId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve YJS snapshot", e);
        }
    }

    public void updateTitle(Long documentId, User user, UpdateTitleRequest request) {
        Document document = documentRepository.findByIdAndIsDeletedFalse(documentId).
                orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if(!permissionService.hasPermission(documentId, user.getId(), Role.OWNER)){
            throw new PermissionDeniedException("Only owner can change title!");
        }

        document.setTitle(request.getTitle());
        documentRepository.save(document);
        log.info("Updated document {} title to: {}", documentId, request.getTitle());
    }
}
